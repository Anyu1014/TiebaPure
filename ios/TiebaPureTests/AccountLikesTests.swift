import XCTest
@testable import TiebaPure

final class AccountLikesTests: XCTestCase {
    override func tearDown() { AccountLikesURLProtocol.handler = nil; super.tearDown() }

    private func fixture(_ name: String) throws -> Data {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "account-likes", withExtension: "json"))
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        return try JSONSerialization.data(withJSONObject: XCTUnwrap(root[name]))
    }

    private func api(_ handler: @escaping (URLRequest) throws -> (Data, String)) -> TiebaAPI {
        AccountLikesURLProtocol.handler = handler
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [AccountLikesURLProtocol.self]
        return TiebaAPI(client: TiebaHTTPClient(session: URLSession(configuration: config)))
    }

    func testOfficialRequestCarriesCredentialsAndEveryFilter() throws {
        for filter in AccountLikesFilter.allCases {
            let fields = try AccountLikesRequestPolicy.fields(account: FixtureTiebaAPI.account, page: 3, filter: filter)
            XCTAssertEqual(fields["BDUSS"], FixtureTiebaAPI.account.bduss)
            XCTAssertEqual(fields["stoken"], FixtureTiebaAPI.account.stoken)
            XCTAssertEqual(fields["pn"], "3")
            XCTAssertEqual(fields["rn"], "20")
            XCTAssertEqual(fields["tab_id"], String(filter.rawValue))
        }
        XCTAssertThrowsError(try AccountLikesRequestPolicy.fields(account: FixtureTiebaAPI.account, page: 0, filter: .all))
        XCTAssertEqual(TiebaEndpoint.accountLikes.url.absoluteString, "https://tiebac.baidu.com/c/u/feed/userAgree")
    }

    func testSharedFixtureKeepsSameThreadRepliesAndLargeIdentifiers() throws {
        let page = try JSONDecoder().decode(AccountLikesResponseDTO.self, from: fixture("first_page"))
        XCTAssertEqual(page.items.map(\.id), ["1001:0", "1001:2002", "1001:2003", "1001:2004", "9007199254740993:0"])
        XCTAssertTrue(page.hasMore)
        XCTAssertEqual(page.items[0].preview, "主帖预览")
        XCTAssertEqual(page.items[1].preview, "第一条赞过的回复")
        XCTAssertEqual(page.items[1].authorDisplayName, "回复者")
        XCTAssertEqual(page.items[3].parentPostID, 2003)
        XCTAssertFalse(try JSONDecoder().decode(AccountLikesResponseDTO.self, from: fixture("second_page")).hasMore)
        XCTAssertThrowsError(try JSONDecoder().decode(AccountLikesResponseDTO.self, from: fixture("malformed")))
        XCTAssertTrue(try JSONDecoder().decode(AccountLikesResponseDTO.self, from: Data(#"{"error_code":0,"data":{"has_more":0}}"#.utf8)).items.isEmpty)
        XCTAssertThrowsError(try JSONDecoder().decode(AccountLikesResponseDTO.self, from: Data(#"{"error_code":0,"data":{"has_more":0,"thread_list":"invalid"}}"#.utf8)))
    }

    func testRoutesStartAtMainOrExactPostAndSubpost() throws {
        let entries = try JSONDecoder().decode(AccountLikesResponseDTO.self, from: fixture("first_page")).items
        XCTAssertNil(entries[0].readerRoute.initialPostID)
        XCTAssertEqual(entries[0].readerRoute.initialDestination, .mainPost)
        XCTAssertEqual(entries[1].readerRoute.initialPostID, 2002)
        XCTAssertNil(entries[1].readerRoute.initialDestination)
        XCTAssertEqual(entries[3].readerRoute.initialPostID, 2003)
        XCTAssertEqual(entries[3].readerRoute.initialDestination, .subpost(parentPostID: 2003, subpostID: 2004))
    }

    func testAPIUsesSignedPOSTAndAcceptsOfficialJavascriptMIME() async throws {
        let body = try fixture("first_page")
        let api = api { request in
            XCTAssertEqual(request.httpMethod, "POST")
            XCTAssertEqual(request.url, TiebaEndpoint.accountLikes.url)
            let fields = try Self.fields(request)
            XCTAssertEqual(fields["tab_id"], "2")
            XCTAssertEqual(fields["pn"], "2")
            let sign = try XCTUnwrap(fields["sign"])
            XCTAssertEqual(sign, TiebaFormSigner.sign(fields: fields.filter { $0.key != "sign" }, secret: "tiebaclient!!!"))
            return (body, "application/x-javascript")
        }
        let page = try await api.accountLikes(account: FixtureTiebaAPI.account, page: 2, filter: .replies)
        XCTAssertEqual(page.currentPage, 2)
        XCTAssertEqual(page.items.count, 5)
    }

    func testAPIRejectsLoginFailuresAndInvalidResponseMIME() async throws {
        let loginError = try fixture("login_error")
        var service = api { _ in (loginError, "application/json") }
        do { _ = try await service.accountLikes(account: FixtureTiebaAPI.account, page: 1, filter: .all); XCTFail("login rejection accepted") }
        catch { XCTAssertTrue(error is TiebaAPIError) }
        let valid = try fixture("first_page")
        service = api { _ in (valid, "text/html") }
        do { _ = try await service.accountLikes(account: FixtureTiebaAPI.account, page: 1, filter: .all); XCTFail("HTML accepted") }
        catch { XCTAssertEqual(error as? TiebaHTTPError, .invalidMIMEType("text/html")) }
        let wrongHost = try XCTUnwrap(HTTPURLResponse(url: URL(string: "https://example.com/c/u/feed/userAgree")!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": "application/json"]))
        XCTAssertThrowsError(try AccountLikesRequestPolicy.validate(response: wrongHost))
        XCTAssertFalse(SecureRemoteRedirectPolicy.allows(originalMethod: "POST", destination: TiebaEndpoint.accountLikes.url, scope: .baiduHTTPS))
    }

    @MainActor
    func testLoaderPagesByContentIDWithoutLosingRepliesOfTheSameThread() async throws {
        let first = try fixture("first_page"), second = try fixture("second_page")
        var calls = 0
        let service = api { request in
            calls += 1
            return (try Self.fields(request)["pn"] == "1" ? first : second, "application/json")
        }
        let loader = AccountLikesLoader()
        await loader.reload(account: FixtureTiebaAPI.account, filter: .all, api: service)
        await loader.loadMore(account: FixtureTiebaAPI.account, api: service)
        XCTAssertEqual(loader.items.map(\.id), ["1001:0", "1001:2002", "1001:2003", "1001:2004", "9007199254740993:0", "1001:2005"])
        XCTAssertFalse(loader.hasMore)
        await loader.loadMore(account: FixtureTiebaAPI.account, api: service)
        XCTAssertEqual(calls, 2)
        var replacement = FixtureTiebaAPI.account
        replacement.bduss = "different-session"
        loader.prepare(account: replacement, filter: .all)
        XCTAssertTrue(loader.items.isEmpty, "Re-login of the same UID must clear the prior session")
        loader.prepare(account: nil, filter: .all)
        XCTAssertTrue(loader.items.isEmpty)
        await loader.reload(account: nil, filter: .all, api: service)
        XCTAssertEqual(calls, 2)
    }

    @MainActor
    func testLoaderHasNoHundredItemLimit() async throws {
        let service = api { request in
            let page = Int(try Self.fields(request)["pn"] ?? "0")!
            let rows = (1...20).map { ["tid": "1001", "title": "fixture", "top_agree_post": ["id": String((page - 1) * 20 + $0), "content": []]] as [String: Any] }
            return (try JSONSerialization.data(withJSONObject: ["error_code": 0, "data": ["has_more": page < 6 ? 1 : 0, "thread_list": rows]]), "application/json")
        }
        let loader = AccountLikesLoader()
        await loader.reload(account: FixtureTiebaAPI.account, filter: .all, api: service)
        for _ in 2...6 { await loader.loadMore(account: FixtureTiebaAPI.account, api: service) }
        XCTAssertEqual(loader.items.count, 120)
        XCTAssertFalse(loader.hasMore)
        loader.prepare(account: FixtureTiebaAPI.account, filter: .threads)
        XCTAssertTrue(loader.items.isEmpty)
        XCTAssertEqual(loader.nextPage, 1)
    }

    private static func fields(_ request: URLRequest) throws -> [String: String] {
        let data: Data
        if let body = request.httpBody { data = body }
        else if let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var result = Data(), buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                guard count > 0 else { break }
                result.append(contentsOf: buffer.prefix(count))
            }
            data = result
        } else { data = Data() }
        let body = String(decoding: data, as: UTF8.self).replacingOccurrences(of: "+", with: "%20")
        let items = try XCTUnwrap(URLComponents(string: "https://fixture.invalid/?" + body)?.queryItems)
        return Dictionary(uniqueKeysWithValues: items.map { ($0.name, $0.value ?? "") })
    }
}

private final class AccountLikesURLProtocol: URLProtocol {
    static var handler: ((URLRequest) throws -> (Data, String))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            let (body, mime) = try XCTUnwrap(Self.handler)(request)
            let response = try XCTUnwrap(HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": mime]))
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: body)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}
