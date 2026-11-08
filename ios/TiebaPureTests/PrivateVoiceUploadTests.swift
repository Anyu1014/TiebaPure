import Foundation
import XCTest
@testable import TiebaPure

final class PrivateVoiceUploadTests: XCTestCase {
    private let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")

    private func upload(rejectChunk: Bool = false) async throws -> (String, [URLRequest]) {
        let id = UUID().uuidString
        VoiceUploadURLProtocol.register(id, rejectChunk: rejectChunk)
        defer { VoiceUploadURLProtocol.remove(id) }
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [VoiceUploadURLProtocol.self]
        configuration.httpAdditionalHeaders = ["X-Voice-Test": id]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        var data = Data(repeating: 0, count: 31_000)
        data.replaceSubrange(0..<4, with: Data("RIFF".utf8))
        data.replaceSubrange(8..<12, with: Data("WAVE".utf8))
        let file = FileManager.default.temporaryDirectory.appendingPathComponent("voice-test-\(id).wav")
        try data.write(to: file)
        defer { try? FileManager.default.removeItem(at: file) }
        do {
            let content = try await PrivateVoiceUpload.upload(file, duration: 2, account: account, clientID: "fixture", session: session)
            return (content, VoiceUploadURLProtocol.records(id))
        } catch {
            if rejectChunk {
                let records = VoiceUploadURLProtocol.records(id)
                XCTAssertEqual(records.count, 1)
                XCTAssertEqual(records.first?.url?.path, "/c/c/voice/chunkupload")
            }
            throw error
        }
    }

    func testChunksAndFinishReturnAPlayableVoiceReceipt() async throws {
        let (content, requests) = try await upload()
        XCTAssertEqual(requests.map { $0.url?.path }, ["/c/c/voice/chunkupload", "/c/c/voice/chunkupload", "/c/c/voice/voice_fin_chunk_upload"])
        XCTAssertTrue(requests.allSatisfy { $0.url?.scheme == "https" && $0.url?.host == "tiebac.baidu.com" })
        XCTAssertTrue(requests.allSatisfy {
            $0.value(forHTTPHeaderField: "Cookie") == "CUID=fixture; ka=open; BDUSS=fixture-bduss; stoken=fixture-stoken"
        })
        let bodies = requests.map { String(decoding: $0.httpBody ?? Data(), as: UTF8.self) }
        XCTAssertTrue(bodies[0].contains("name=\"offset\"\r\n\r\n0\r\n"))
        XCTAssertTrue(bodies[1].contains("name=\"offset\"\r\n\r\n30720\r\n"))
        XCTAssertTrue(bodies[1].contains("name=\"length\"\r\n\r\n280\r\n"))
        XCTAssertTrue(bodies[2].contains("type=2"))
        XCTAssertEqual(PrivateMessageContent.voiceMD5(content), "0123456789abcdef0123456789abcdef_Synthetic_2")
        XCTAssertEqual(PrivateMessageContent.duration(content), 2)
        let envelope = try XCTUnwrap((JSONSerialization.jsonObject(with: Data(content.utf8)) as? [[String: Any]])?.first)
        XCTAssertEqual(envelope["type"] as? Int, 10)
        XCTAssertEqual(envelope["isUploaded"] as? Bool, true)
    }

    func testRejectedChunkNeverFinishesOrRetries() async {
        do { _ = try await upload(rejectChunk: true); XCTFail("Rejected upload must fail") }
        catch { XCTAssertNotNil(error as? ContentSubmissionError) }
    }
}

private final class VoiceUploadURLProtocol: URLProtocol {
    private struct Context { var requests: [URLRequest] = []; let rejectChunk: Bool }
    private static let lock = NSLock()
    private static var contexts: [String: Context] = [:]
    static func register(_ id: String, rejectChunk: Bool) { lock.lock(); defer { lock.unlock() }; contexts[id] = Context(rejectChunk: rejectChunk) }
    static func remove(_ id: String) { lock.lock(); defer { lock.unlock() }; contexts.removeValue(forKey: id) }
    static func records(_ id: String) -> [URLRequest] { lock.lock(); defer { lock.unlock() }; return contexts[id]?.requests ?? [] }
    override class func canInit(with request: URLRequest) -> Bool { request.value(forHTTPHeaderField: "X-Voice-Test") != nil }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        guard let id = request.value(forHTTPHeaderField: "X-Voice-Test"), let url = request.url else { return }
        var captured = request
        if captured.httpBody == nil, let stream = captured.httpBodyStream {
            stream.open(); defer { stream.close() }
            var body = Data(); var buffer = [UInt8](repeating: 0, count: 8192)
            while stream.hasBytesAvailable { let count = stream.read(&buffer, maxLength: buffer.count); if count <= 0 { break }; body.append(contentsOf: buffer.prefix(count)) }
            captured.httpBody = body
        }
        Self.lock.lock()
        guard var context = Self.contexts[id] else { Self.lock.unlock(); return }
        context.requests.append(captured); Self.contexts[id] = context
        Self.lock.unlock()
        let payload = context.rejectChunk ? #"{"error":{"errno":123}}"# : url.path.hasSuffix("chunkupload") ? #"{"error":{"errno":0}}"# : #"{"error":{"errno":0},"info":{"voice_md5":"0123456789abcdef0123456789abcdef_Synthetic_2"}}"#
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(payload.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
