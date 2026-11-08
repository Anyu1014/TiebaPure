import Foundation
import Combine
import SwiftProtobuf

struct PrivateConversation: Identifiable, Equatable, Sendable {
    var userID: String
    var passUK: String
    var userType: Int
    var name: String
    var portrait = ""
    var preview = ""
    var timestamp: Int64 = 0
    var unread = 0
    var id: String { "\(userType):\(passUK)" }
    static func recipient(_ user: UserSummary, accountID: String) throws -> Self {
        guard user.id > 0, String(user.id) != accountID else { throw PrivateMessageError.invalidUser }
        return Self(userID: String(user.id), passUK: String(user.id), userType: 0,
                    name: user.displayNameResolved, portrait: user.portrait)
    }
}

struct PrivateChatMessage: Identifiable, Equatable, Sendable {
    var id: String
    var senderID: String
    var text: String
    var timestamp: Int64
    var type: Int = 1
    var mediaURL: URL? = nil
    var voiceMD5: String? = nil
    var duration: Int = 0
    var read: Bool = false
}

enum PrivateMessageContent {
    private static func body(_ content: String) -> [String: Any]? {
        guard content.utf8.count <= 65_536, let data = content.data(using: .utf8) else { return nil }
        return (try? JSONSerialization.jsonObject(with: data) as? [[String: Any]])?.first
    }
    private static func integer(_ value: Any?) -> Int64? {
        if let value = value as? String { return Int64(value) }
        if let value = value as? NSNumber { return Int64(value.stringValue) }
        return nil
    }
    static func readWatermark(_ message: Tiebapure_Official_ChatMessage) -> Int64? {
        guard message.type == 22, message.content.utf8.count <= 65_536,
              let data = message.content.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              integer(json["eventId"]) == 22_001,
              let param = json["eventParam"] as? [String: Any],
              message.groupID > 0, integer(param["groupId"]) == message.groupID,
              let watermark = integer(param["readMsgId"]), watermark > 0 else { return nil }
        return watermark
    }
    static func imageURL(_ content: String) -> URL? {
        guard let json = body(content) else { return nil }
        return ["big_src", "src"].compactMap { TiebaRemoteMediaPolicy.url(json[$0] as? String) }.first
    }
    static func voiceMD5(_ content: String) -> String? {
        guard let value = body(content)?["voice_md5"] as? String else { return nil }
        return VoiceAudioURLPolicy.normalizedVoiceID(value)
    }
    static func duration(_ content: String) -> Int {
        Int(min(max(integer(body(content)?["during_time"]) ?? 0, 0), 600))
    }
    static func validate(_ content: String, type: Int) throws {
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              content.utf8.count <= (type == 1 ? 20_000 : 65_536), type != 1 || content.count <= 5_000,
              type == 1 || type == 2 && imageURL(content) != nil || type == 3 && voiceMD5(content) != nil && (1...600).contains(integer(body(content)?["during_time"]) ?? 0) else {
            throw PrivateMessageError.invalidResponse
        }
    }
    static func message(_ value: Tiebapure_Official_ChatMessage, read: Bool = false) -> PrivateChatMessage {
        PrivateChatMessage(id: String(value.id), senderID: String(value.fromUid), text: OfficialPrivateInbox.text(value), timestamp: Int64(value.timestamp), type: Int(value.type), mediaURL: value.type == 2 ? imageURL(value.content) : nil, voiceMD5: value.type == 3 ? voiceMD5(value.content) : nil, duration: value.type == 3 ? duration(value.content) : 0, read: read)
    }
}

struct PrivateMessagePage<Item> {
    var items: [Item]
    var hasMore: Bool
    var cursor: Int64?
}

enum PrivateMessageError: LocalizedError {
    case invalidResponse, invalidUser, recipientUnavailable, rejected(Int, message: String = ""), unknownSend
    var errorDescription: String? {
        switch self {
        case .invalidResponse: return "私信响应无法识别，请稍后刷新。"
        case .invalidUser: return "请输入有效的贴吧 ID，或从对方主页进入私信。"
        case .recipientUnavailable: return "该用户的私信身份暂不可用，请从对方主页重新进入。"
        case .rejected(let code, let message): return "贴吧私信请求未通过（\(code)）。" + (message.isEmpty ? "请稍后重试。" : message)
        case .unknownSend: return "发送结果无法确认，请先刷新消息核对，避免重复发送。"
        }
    }
}

@MainActor
final class PrivateMessageClient: ObservableObject {
    var officialPull: ((Account, [Tiebapure_Official_Group]) async throws -> OfficialPrivateSnapshot)?
    var officialSend: ((Account, Tiebapure_Official_CommitRequest) async throws -> Tiebapure_Official_CommitResponse)?
    let cuid: String
    private let directory: URL?
    private var inboxes: [String: OfficialPrivateInbox] = [:]
    private static var persistentInboxes: [URL: OfficialPrivateInbox] = [:]
    init(cuid: String = UUID().uuidString.replacingOccurrences(of: "-", with: "").uppercased(), directory: URL? = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?.appendingPathComponent("official-private-messages")) {
        self.cuid = cuid; self.directory = directory
    }
    private func inbox(_ account: Account) throws -> OfficialPrivateInbox {
        guard let id = Int64(account.uid), id > 0, String(id) == account.uid else { throw PrivateMessageError.invalidUser }
        if let file = directory?.appendingPathComponent(account.uid + ".pb") {
            if let inbox = Self.persistentInboxes[file] { return inbox }
            let inbox = try OfficialPrivateInbox(accountID: account.uid, file: file)
            Self.persistentInboxes[file] = inbox
            return inbox
        }
        if let inbox = inboxes[account.uid] { return inbox }
        let inbox = try OfficialPrivateInbox(accountID: account.uid)
        inboxes[account.uid] = inbox; return inbox
    }

    private func synchronize(_ account: Account) async throws -> OfficialPrivateInbox {
        let inbox = try inbox(account), cursors = inbox.state.groups
        let snapshot: OfficialPrivateSnapshot
        if let officialPull { snapshot = try await officialPull(account, cursors) }
        else {
            snapshot = try await OfficialPrivateTransport.session(account: account, device: cuid) { transport in
                let request = OfficialPrivateTransport.pullRequest(device: cuid, groups: transport.groups, cursors: cursors)
                let response = try Tiebapure_Official_PullResponse(serializedBytes: await transport.request(202003, data: request.serializedData()))
                return OfficialPrivateSnapshot(groups: transport.groups, response: response)
            }
        }
        try Task.checkCancellation(); try inbox.merge(snapshot); return inbox
    }
    func markAllRead(account: Account) throws {
        try inbox(account).markAllRead()
    }
    func hasCachedUnread(account: Account) throws -> Bool {
        try inbox(account).conversations().items.contains { $0.unread > 0 }
    }
    func conversations(account: Account, before: Int64? = nil) async throws -> PrivateMessagePage<PrivateConversation> {
        try await synchronize(account).conversations()
    }
    func personalGroupID(account: Account) async throws -> Int64 {
        try Task.checkCancellation()
        let cached = try inbox(account)
        let store = cached.state.groups.contains { $0.type == 6 && $0.id > 0 } ? cached : try await synchronize(account)
        try Task.checkCancellation()
        guard let group = store.state.groups.first(where: { $0.type == 6 && $0.id > 0 }) else {
            throw ContentSubmissionError.unsupported(message: "未取得当前账号的私信图片上传分组，请刷新会话后重试。")
        }
        return group.id
    }
    func messages(_ peer: PrivateConversation, account: Account, before: Int64? = nil) async throws -> PrivateMessagePage<PrivateChatMessage> {
        let inbox: OfficialPrivateInbox
        if before == nil { inbox = try await synchronize(account) } else { inbox = try self.inbox(account) }
        return try inbox.messages(peer.userID, before: before)
    }
    func send(_ text: String, to peer: PrivateConversation, account: Account, key: String, type: Int = 1) async throws -> String {
        try PrivateMessageContent.validate(text, type: type)
        guard let uid = Int64(peer.userID), uid > 0, String(uid) == peer.userID, peer.userID != account.uid else { throw PrivateMessageError.invalidUser }
        let inbox = try inbox(account)
        let record = Int64(Date().timeIntervalSince1970 * 1_000_000)
        var request = Tiebapure_Official_CommitRequest()
        request.data.toUid = uid; request.data.toUserType = 0; request.data.msgType = Int32(type)
        request.data.content = text; request.data.recordID = record
        let response: Tiebapure_Official_CommitResponse
        do {
            if let officialSend { response = try await officialSend(account, request) }
            else { response = try await OfficialPrivateTransport.session(account: account, device: cuid) { transport in
                try Tiebapure_Official_CommitResponse(serializedBytes: await transport.request(205001, data: request.serializedData()))
            } }
        } catch PrivateMessageError.rejected(let code, let message) { throw PrivateMessageError.rejected(code, message: message) }
        catch { throw PrivateMessageError.unknownSend }
        guard response.hasError else { throw PrivateMessageError.unknownSend }
        guard response.error.code == 0 else { throw PrivateMessageError.rejected(Int(response.error.code)) }
        let receipt = response.data
        guard receipt.id > 0, receipt.toUid == uid, receipt.recordID == record else { throw PrivateMessageError.unknownSend }
        do { try inbox.sent(text, to: peer, account: account, receipt: receipt, type: type) } catch { throw PrivateMessageError.unknownSend }
        return String(receipt.id)
    }
}
