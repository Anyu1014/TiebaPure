import XCTest
@testable import TiebaPure

final class PrivateMediaUploadTests: XCTestCase {
    @MainActor func testUploadUsesPostingIdentityInsteadOfPrivateSessionID() {
        let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")
        let identity = TiebaPostingIdentity(androidID: "fixture-android", uuid: "fixture-uuid", cuidGalaxy2: "registered-cuid", c3AID: "registered-c3")
        let bootstrap = TiebaPostingBootstrapResult(identity: identity, clientID: "registered-client", sampleID: "registered-sample", zID: "registered-z")
        let context = PrivateMessageMediaUpload.requestContext(account: account, clientID: "lcs-session", bootstrap: bootstrap)
        XCTAssertEqual(context.fields["_client_id"], "registered-client")
        XCTAssertEqual(context.fields["cuid"], "registered-cuid")
        XCTAssertEqual(context.fields["c3_aid"], "registered-c3")
        XCTAssertEqual(context.fields["sample_id"], "registered-sample")
        XCTAssertEqual(context.fields["z_id"], "registered-z")
        XCTAssertEqual(context.headers["cuid"], "registered-cuid")
        XCTAssertEqual(context.fields["BDUSS"], "fixture-bduss")
    }

    func testDiagnosticMessageFlagsDoNotExposeResponseText() {
        XCTAssertEqual(PrivateMessageMediaUpload.diagnosticMessageFlags("签名错误 secret-123"), "signature,invalid")
        XCTAssertEqual(PrivateMessageMediaUpload.diagnosticMessageFlags("账号未登录 token=secret"), "authentication")
        XCTAssertEqual(PrivateMessageMediaUpload.diagnosticMessageFlags("random private information"), "other")
    }
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
        XCTAssertEqual(request.value(forHTTPHeaderField: "Cookie"), "ka=open")
        XCTAssertEqual(request.value(forHTTPHeaderField: "User-Agent"), "tieba/12.35.1.0 skin/default")
        XCTAssertTrue(text.contains("name=\"_client_version\"\r\n\r\n12.35.1.0\r\n"))
        XCTAssertTrue(text.contains("name=\"BDUSS\"\r\n\r\nfixture-bduss\r\n"))
        XCTAssertTrue(text.contains("name=\"resourceId\"\r\n\r\nE44E7ECFEC99356632C13CD3EAA3E250512000\r\n"))
        XCTAssertTrue(text.contains("name=\"support_image\"\r\n\r\njepgwebp\r\n"))
        XCTAssertTrue(text.contains("name=\"stoken\"\r\n\r\nfixture-stoken\r\n"))
        XCTAssertTrue(text.contains("name=\"scr_w\""))
        XCTAssertTrue(text.contains("name=\"scr_h\""))
        XCTAssertTrue(text.contains("name=\"scr_dip\""))
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
