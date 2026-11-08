import XCTest
import SwiftProtobuf
@testable import TiebaPure

@MainActor
final class PrivateMessagePolicyTests: XCTestCase {
    func testConversationTimestampUsesLocalDayAndMondayBoundary() throws {
        let zone = try XCTUnwrap(TimeZone(identifier: "Asia/Shanghai"))
        let iso = ISO8601DateFormatter()
        func label(_ date: String, now: String = "2026-11-06T16:05:00Z") throws -> String {
            try ReaderDateText.privateConversationString(
                from: XCTUnwrap(iso.date(from: date)),
                now: XCTUnwrap(iso.date(from: now)),
                timeZone: zone
            )
        }
        XCTAssertEqual(try label("2026-11-06T16:04:00Z"), "00:04")
        XCTAssertEqual(try label("2026-11-06T15:59:00Z"), "昨天 23:59")
        XCTAssertEqual(try label("2026-11-05T01:04:00Z"), "星期四")
        XCTAssertEqual(try label("2026-11-02T01:04:00Z"), "星期一")
        XCTAssertEqual(try label("2026-11-01T01:04:00Z"), "11月1日")
        XCTAssertEqual(try label("2026-11-08T15:59:00Z", now: "2026-11-08T16:05:00Z"), "昨天 23:59")
        XCTAssertEqual(try label("2025-12-31T01:04:00Z"), "12月31日")
    }

    func testNewestConversationsAndMarkAllReadPreserveLaterMessages() throws {
        let inbox = try OfficialPrivateInbox(accountID: "84")
        func message(_ id: Int64, from: Int64, to: Int64, time: Int32) -> Tiebapure_Official_ChatMessage {
            var result = Tiebapure_Official_ChatMessage()
            result.id = id
            result.fromUid = from
            result.toUid = to
            result.timestamp = time
            result.type = 1
            return result
        }
        func merge(_ items: [Tiebapure_Official_ChatMessage]) throws {
            var response = Tiebapure_Official_PullResponse()
            response.error.code = 0
            response.data.personal.messages = items
            try inbox.merge(OfficialPrivateSnapshot(groups: [], response: response))
        }
        try merge([
            message(10, from: 42, to: 84, time: 100),
            message(11, from: 43, to: 84, time: 100),
            message(15, from: 44, to: 84, time: 90),
            message(99, from: 99, to: 1, time: 110)
        ])
        XCTAssertEqual(inbox.conversations().items.map(\.userID), ["43", "42", "44"])
        XCTAssertEqual(inbox.conversations().items.map(\.unread), [1, 1, 1])
        try inbox.markAllRead()
        XCTAssertEqual(inbox.conversations().items.map(\.unread), [0, 0, 0])
        try merge([message(16, from: 42, to: 84, time: 101)])
        XCTAssertEqual(inbox.conversations().items.map(\.userID), ["42", "43", "44"])
        XCTAssertEqual(inbox.conversations().items.map(\.unread), [1, 0, 0])
    }

    func testOfficialSuffixedVoiceIdentifierReachesPlaybackURL() throws {
        let content = try XCTUnwrap(fixture()["official_content"] as? [String: String])
        let id = try XCTUnwrap(content["suffixed_voice_id"])
        var message = Tiebapure_Official_ChatMessage()
        message.type = 3
        message.content = try XCTUnwrap(content["suffixed_voice_content"])
        let row = PrivateMessageContent.message(message)
        XCTAssertEqual(row.voiceMD5, id)
        XCTAssertEqual(row.duration, 4)
        let voice = try XCTUnwrap(VoiceContent(md5: XCTUnwrap(row.voiceMD5), durationMilliseconds: 4_000))
        let url = try XCTUnwrap(VoiceAudioURLPolicy.url(forMD5: voice.md5))
        XCTAssertEqual(URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first?.value, id)
        XCTAssertNil(VoiceAudioURLPolicy.url(forMD5: id + "&a=1"))
    }

    func testImageUploadGroupComesFromTheAuthenticatedInboxAndIsCached() async throws {
        let client = PrivateMessageClient(directory: nil)
        let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")
        var pulls = 0
        client.officialPull = { _, _ in
            pulls += 1
            var group = Tiebapure_Official_Group(); group.id = 600; group.type = 6
            var response = Tiebapure_Official_PullResponse(); response.error.code = 0
            return OfficialPrivateSnapshot(groups: [group], response: response)
        }
        let first = try await client.personalGroupID(account: account)
        let cached = try await client.personalGroupID(account: account)
        XCTAssertEqual(first, 600)
        XCTAssertEqual(cached, 600)
        XCTAssertEqual(pulls, 1)
    }
    func testReadEventsAreMetadataAndOnlyMarkTheMatchingOutgoingMessages() throws {
        let inbox = try OfficialPrivateInbox(accountID: "84")
        let fixture = try XCTUnwrap(fixture()["official_content"] as? [String: String])
        let outgoing = try Tiebapure_Official_ChatMessage(serializedBytes: hex(XCTUnwrap(fixture["outgoing_hex"])))
        var event = try Tiebapure_Official_ChatMessage(serializedBytes: hex(XCTUnwrap(fixture["read_event_hex"])))
        var group = Tiebapure_Official_Group(); group.id = 600; group.type = 6
        var batch = Tiebapure_Official_GroupMessages(); batch.group = group; batch.messages = [outgoing, event]
        var response = Tiebapure_Official_PullResponse(); response.error.code = 0; response.data.groups = [batch]
        try inbox.merge(OfficialPrivateSnapshot(groups: [group], response: response))
        let messages = try inbox.messages("42", before: nil).items
        XCTAssertEqual(messages.map(\.text), ["已发送"])
        XCTAssertTrue(try XCTUnwrap(messages.first).read)
        XCTAssertEqual(inbox.conversations().items.first?.unread, 0)
        event.groupID = 601
        XCTAssertNil(PrivateMessageContent.readWatermark(event))
        event.content = "not json"
        XCTAssertNil(PrivateMessageContent.readWatermark(event))
    }
    func testOfficialMediaArraysValidateAndKeepTheirTypes() throws {
        let image = #"[{"src":"https://tiebapic.baidu.com/a.jpg","big_src":"http://tiebapic.baidu.com/original.jpg","type":"3","bsize":"100,200"}]"#
        XCTAssertEqual(PrivateMessageContent.imageURL(image)?.absoluteString, "https://tiebapic.baidu.com/original.jpg")
        XCTAssertNil(PrivateMessageContent.imageURL(#"[{"src":"https://evil.example/a.jpg"}]"#))
        let voice = #"[{"voice_md5":"0123456789abcdef0123456789abcdef","during_time":4,"has_read":0}]"#
        XCTAssertEqual(PrivateMessageContent.duration(voice), 4)
        XCTAssertEqual(PrivateMessageContent.voiceMD5(voice), "0123456789abcdef0123456789abcdef")
        XCTAssertNoThrow(try PrivateMessageContent.validate(image, type: 2))
        XCTAssertNoThrow(try PrivateMessageContent.validate(voice, type: 3))
        XCTAssertThrowsError(try PrivateMessageContent.validate("[]", type: 2))
    }
    func testOfficialMediaSendKeepsTheTypeAndCachesRenderableContent() async throws {
        let fixture = try XCTUnwrap(fixture()["official_content"] as? [String: String])
        let client = PrivateMessageClient(directory: nil)
        let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")
        let peer = PrivateConversation(userID: "42", passUK: "42", userType: 0, name: "测试")
        for (type, key) in [(2, "image_content"), (3, "voice_content")] {
            let content = try XCTUnwrap(fixture[key])
            client.officialSend = { _, request in
                XCTAssertEqual(Int(request.data.msgType), type); XCTAssertEqual(request.data.content, content)
                var response = Tiebapure_Official_CommitResponse(); response.error.code = 0; response.data.id = Int64(type); response.data.toUid = 42; response.data.recordID = request.data.recordID
                return response
            }
            _ = try await client.send(content, to: peer, account: account, key: "fixture", type: type)
            let page = try await client.messages(peer, account: account, before: Int64.max)
            let cached = try XCTUnwrap(page.items.first { $0.type == type })
            if type == 2 { XCTAssertNotNil(cached.mediaURL) } else { XCTAssertNotNil(cached.voiceMD5); XCTAssertEqual(cached.duration, 4) }
        }
    }
    private func fixture() throws -> [String: Any] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "private-messages", withExtension: "json"))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }
    private func hex(_ text: String) -> Data { Data(stride(from: 0, to: text.count, by: 2).map { n in let start = text.index(text.startIndex, offsetBy: n); return UInt8(text[start..<text.index(start, offsetBy: 2)], radix: 16)! }) }
    func testOfficialWireAndCryptoFixture() throws {
        let f = try XCTUnwrap(fixture()["official_lcs"] as? [String: String])
        let key = try OfficialPrivateTransport.derive(XCTUnwrap(f["secret"]))
        XCTAssertEqual(key, hex(try XCTUnwrap(f["key_hex"])))
        let wire = hex(try XCTUnwrap(f["commit_hex"]))
        let request = try Tiebapure_Official_CommitRequest(serializedBytes: wire)
        XCTAssertEqual(request.data.toUid, 42); XCTAssertEqual(request.data.msgType, 1)
        XCTAssertTrue(request.data.hasToUserType); XCTAssertEqual(request.data.toUserType, 0)
        XCTAssertEqual(request.data.content, "测试")
        let cipher = hex(try XCTUnwrap(f["cipher_hex"]))
        XCTAssertEqual(try OfficialPrivateTransport.crypt(wire, key: key, encrypt: true), cipher)
        XCTAssertEqual(try OfficialPrivateTransport.crypt(cipher, key: key, encrypt: false), wire)
        let lookup = try XCTUnwrap(fixture()["id_lookup"] as? [String: String])
        let response = try Tiebapure_Profile_PrivateMessageUserResponse(serializedBytes: hex(XCTUnwrap(lookup["response_hex"])))
        XCTAssertEqual(String(response.data.user.id), lookup["uid"])
        XCTAssertNotEqual(String(response.data.user.id), lookup["id"])
    }
    func testInitialSyncAvoidsTheEmptySuccessSentinel() {
        var group = Tiebapure_Official_Group(); group.id = 600; group.type = 6
        for cursor: Int64 in [0,42] {
            var old = group; old.lastMsgID = cursor
            let request = OfficialPrivateTransport.pullRequest(device: "fixture", groups: [group], cursors: [old])
            XCTAssertEqual(request.data.groups.first?.lastMsgID,max(1,cursor))
            XCTAssertEqual(request.data.repair.followType,"0")
        }
        XCTAssertEqual(OfficialPrivateTransport.pullRequest(device: "fixture", groups: [group], cursors: []).data.groups.first?.lastMsgID,1)
    }
    func testInboxIsolationUnreadAndPersistentReload() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("inbox.pb")
        let inbox = try OfficialPrivateInbox(accountID: "84", file: file)
        func message(_ id: Int64, _ from: Int64, _ to: Int64) -> Tiebapure_Official_ChatMessage {
            var m = Tiebapure_Official_ChatMessage(); m.id = id; m.fromUid = from; m.toUid = to; m.type = 1; m.content = "消息\(id)"; m.timestamp = Int32(id); m.fromUser.id = from; m.fromUser.nickname = "用户\(from)"; return m
        }
        var group = Tiebapure_Official_Group(); group.id = 6; group.type = 6
        var batch = Tiebapure_Official_GroupMessages(); batch.group = group; batch.messages = [message(1,42,84),message(2,43,84),message(3,42,99)]
        var response = Tiebapure_Official_PullResponse(); response.error.code = 0; response.data.groups = [batch]
        for _ in 0..<2 { try inbox.merge(OfficialPrivateSnapshot(groups: [group], response: response)) }
        XCTAssertEqual(inbox.conversations().items.count, 2)
        XCTAssertEqual(inbox.conversations().items.first { $0.userID == "42" }?.unread, 1)
        XCTAssertEqual(try inbox.messages("42", before: nil).items.map(\.text), ["消息1"])
        XCTAssertEqual(inbox.conversations().items.first { $0.userID == "42" }?.unread, 0)
        let loaded = try OfficialPrivateInbox(accountID: "84", file: file)
        XCTAssertEqual(loaded.conversations().items.count, 2)
        XCTAssertEqual(try loaded.messages("43", before: nil).items.map(\.text), ["消息2"])
        var empty = Tiebapure_Official_PullResponse(); empty.error.code = 0
        try loaded.merge(OfficialPrivateSnapshot(groups: [group], response: empty))
        XCTAssertEqual(loaded.conversations().items.count, 2)
    }
    func testFollowedRecipient() throws {
        let user = UserSummary(id: 42, name: "raw", displayName: "关注用户", portrait: "portrait-token")
        let peer = try PrivateConversation.recipient(user, accountID: "84")
        XCTAssertEqual(peer.userID, "42"); XCTAssertEqual(peer.name, "关注用户"); XCTAssertEqual(peer.portrait, "portrait-token")
        XCTAssertThrowsError(try PrivateConversation.recipient(user, accountID: "42"))
    }
}

@MainActor
final class PrivateMessageNetworkTests: XCTestCase {
    func testNicknameSearchIncludesFuzzyMatches() async throws {
        PrivateWireURLProtocol.reset(response: Data("{\"no\":0,\"data\":{\"exactMatch\":{\"id\":42,\"name\":\"raw\",\"show_nickname\":\"昵称\"},\"fuzzyMatch\":[{\"id\":42,\"name\":\"raw\"},{\"uid\":43,\"name\":\"另一用户\"}]}}".utf8))
        defer { PrivateWireURLProtocol.reset() }
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [PrivateWireURLProtocol.self]
        let api = TiebaAPI(client: TiebaHTTPClient(session: URLSession(configuration: config)))
        let users = try await api.searchUsers(keyword: "昵称", account: nil)
        XCTAssertEqual(users.map(\.id), [42, 43])
    }

    private let account = Account(uid: "84", name: "fixture", displayName: "fixture", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", tbs: "fixture-tbs")
    func testTwoEntrypointsSharePersistentMessages() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let first = PrivateMessageClient(directory: directory), second = PrivateMessageClient(directory: directory)
        second.officialPull = { _, _ in var response = Tiebapure_Official_PullResponse(); response.error.code = 0; return OfficialPrivateSnapshot(groups: [], response: response) }
        _ = try await second.conversations(account: account)
        first.officialSend = { _, request in
            var response = Tiebapure_Official_CommitResponse(); response.error.code = 0
            response.data.id = 9; response.data.toUid = request.data.toUid; response.data.recordID = request.data.recordID; return response
        }
        let peer = PrivateConversation(userID: "42", passUK: "42", userType: 0, name: "用户42")
        _ = try await first.send("保留跨入口的消息", to: peer, account: account, key: "unused")
        let page = try await second.conversations(account: account)
        XCTAssertEqual(page.items.first?.preview,"保留跨入口的消息")
    }
    func testOfficialSendReceiptAndNoRetry() async throws {
        let client = PrivateMessageClient(directory: nil)
        let peer = PrivateConversation(userID: "42", passUK: "42", userType: 0, name: "测试用户")
        var attempts = 0
        client.officialSend = { _, request in
            attempts += 1; XCTAssertEqual(request.data.toUid,42); XCTAssertEqual(request.data.msgType,1); XCTAssertTrue(request.data.hasToUserType)
            var response = Tiebapure_Official_CommitResponse(); response.error.code = 0
            response.data.id = 9007199254740993; response.data.toUid = 42; response.data.recordID = request.data.recordID
            return response
        }
        let id = try await client.send("测试", to: peer, account: account, key: "unused")
        XCTAssertEqual(id,"9007199254740993"); XCTAssertEqual(attempts,1)
        for code in [0,615] {
            attempts = 0
            client.officialSend = { _, _ in attempts += 1; var response = Tiebapure_Official_CommitResponse(); if code != 0 { response.error.code = Int32(code) }; return response }
            do { _ = try await client.send("测试", to: peer, account: account, key: "unused"); XCTFail("must fail") }
            catch { if code == 0 { guard case PrivateMessageError.unknownSend = error else { return XCTFail("wrong outcome") } } else { guard case PrivateMessageError.rejected(615,_) = error else { return XCTFail("wrong outcome") } } }
            XCTAssertEqual(attempts,1)
        }
    }
}

private final class PrivateWireURLProtocol: URLProtocol {
    private static let lock = NSLock()
    private static var payload = Data()
    private static var captured: [URLRequest] = []
    static var requests: [URLRequest] { lock.lock(); defer { lock.unlock() }; return captured }
    static func reset(response: Data = Data()) { lock.lock(); defer { lock.unlock() }; payload = response; captured = [] }
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        var captured = request
        if captured.httpBody == nil, let stream = request.httpBodyStream {
            stream.open(); defer { stream.close() }
            var data = Data(), buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let count = stream.read(&buffer, maxLength: buffer.count)
                guard count > 0 else { break }
                data.append(contentsOf: buffer.prefix(count))
            }
            captured.httpBody = data
        }
        Self.lock.lock(); Self.captured.append(captured); let payload = Self.payload; Self.lock.unlock()
        let data = payload
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}
