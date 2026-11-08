import XCTest
@testable import TiebaPure

final class PrivateMediaUploadTests: XCTestCase {
    func testImageUploadUsesSignedMultipartAndAValidatedReceipt() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PrivateImageURLProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let data = try XCTUnwrap(Data(base64Encoded: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="))
        let image = ContentSubmissionImage(data: data, pixelWidth: 1, pixelHeight: 1, mimeType: "image/png")
        let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")
        let content = try await PrivateMessageMediaUpload.image(image, account: account, groupID: 600, clientID: "fixture", session: session)
        XCTAssertEqual(PrivateMessageContent.imageURL(content)?.absoluteString, "https://tiebapic.baidu.com/a.jpg")
    }
    func testImageUploadReceiptUsesValidatedOriginalURLAndOfficialBodyShape() throws {
        let data = Data(#"{"error_code":0,"picInfo":{"originPic":{"picUrl":"http://tiebapic.baidu.com/forum/pic/item/fixture.jpg"}}}"#.utf8)
        let content = try PrivateMessageMediaUpload.imageContent(data, width: 40, height: 50)
        XCTAssertEqual(PrivateMessageContent.imageURL(content)?.absoluteString, "https://tiebapic.baidu.com/forum/pic/item/fixture.jpg")
        let body = try XCTUnwrap((JSONSerialization.jsonObject(with: Data(content.utf8)) as? [[String: String]])?.first)
        XCTAssertEqual(body["type"], "3")
        XCTAssertEqual(body["bsize"], "40,50")
        for url in ["https://127.0.0.1/a.jpg", "https://example.com/a.jpg", "file:///tmp/a.jpg"] {
            let bad = try JSONSerialization.data(withJSONObject: ["picInfo": ["originPic": ["picUrl": url]]])
            XCTAssertThrowsError(try PrivateMessageMediaUpload.imageContent(bad, width: 40, height: 50))
        }
    }
}

private final class PrivateImageURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        XCTAssertEqual(request.url?.scheme, "https")
        XCTAssertEqual(request.url?.host, "tiebac.baidu.com")
        XCTAssertEqual(request.url?.path, "/c/s/uploadPicture")
        var body = request.httpBody ?? Data()
        if let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var buffer = [UInt8](repeating: 0, count: 8192)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                if count <= 0 { break }
                body.append(contentsOf: buffer.prefix(count))
            }
        }
        let text = String(decoding: body, as: UTF8.self)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Cookie"), "CUID=fixture; ka=open; BDUSS=fixture-bduss; stoken=fixture-stoken")
        XCTAssertTrue(text.contains("name=\"resourceId\"\r\n\r\nE44E7ECFEC99356632C13CD3EAA3E250512000\r\n"))
        XCTAssertTrue(text.contains("name=\"support_image\"\r\n\r\njepgwebp\r\n"))
        XCTAssertTrue(text.contains("name=\"sign\""))
        XCTAssertTrue(text.contains("name=\"chunkNo\"\r\n\r\n1\r\n"))
        XCTAssertTrue(text.contains("name=\"isFinish\"\r\n\r\n1\r\n"))
        XCTAssertTrue(text.contains("name=\"chunk\"; filename=\"file\""))
        XCTAssertTrue(text.contains("name=\"groupId\"\r\n\r\n600\r\n"))
        XCTAssertFalse(text.contains("Content-Type: image/"))
        let payload = Data(#"{"error_code":0,"picInfo":{"originPic":{"picUrl":"https://tiebapic.baidu.com/a.jpg"}}}"#.utf8)
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: payload)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
