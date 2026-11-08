import Foundation
import SwiftProtobuf

struct OfficialPrivateSnapshot {
    var groups: [Tiebapure_Official_Group]
    var response: Tiebapure_Official_PullResponse
}

final class OfficialPrivateInbox {
    let accountID: String
    private let file: URL?
    private(set) var state = Tiebapure_Official_Inbox()
    init(accountID: String, file: URL? = nil) throws {
        self.accountID = accountID; self.file = file
        if let file, FileManager.default.fileExists(atPath: file.path) {
            guard (try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0) <= 16_777_216 else { throw PrivateMessageError.invalidResponse }
            state = try Tiebapure_Official_Inbox(serializedBytes: Data(contentsOf: file))
        }
    }
    private func peer(_ message: Tiebapure_Official_ChatMessage) -> String? {
        guard message.id > 0, message.fromUid > 0, message.toUid > 0, message.fromUid != message.toUid else { return nil }
        if String(message.fromUid) == accountID { return String(message.toUid) }
        if String(message.toUid) == accountID { return String(message.fromUid) }
        return nil
    }
    func merge(_ snapshot: OfficialPrivateSnapshot) throws {
        let response = snapshot.response
        guard response.hasError else { throw PrivateMessageError.invalidResponse }
        guard response.error.code == 0 else { throw PrivateMessageError.rejected(Int(response.error.code)) }
        var groups = Dictionary(state.groups.map { ($0.id, $0) }, uniquingKeysWith: { _, new in new })
        for var group in snapshot.groups where groups[group.id] == nil { group.lastMsgID = 0; groups[group.id] = group }
        var messages = Dictionary(state.messages.compactMap { message in peer(message).map { ("\($0):\(message.id)", message) } }, uniquingKeysWith: { _, new in new })
        func add(_ values: [Tiebapure_Official_ChatMessage]) throws {
            for message in values {
                guard let id = peer(message) else { continue }
                guard message.fromUser.id == 0 || message.fromUser.id == message.fromUid,
                      message.toUser.id == 0 || message.toUser.id == message.toUid else { throw PrivateMessageError.invalidResponse }
                messages["\(id):\(message.id)"] = message
            }
        }
        try add(response.data.personal.messages)
        for batch in response.data.groups {
            let known = groups[batch.group.id]
            if (batch.group.hasType ? batch.group.type : known?.type) == 6 { try add(batch.messages) }
            if known != nil || batch.group.hasType {
                var group = batch.group
                if !group.hasType, let known { group = known }
                group.lastMsgID = max(known?.lastMsgID ?? 0, batch.messages.map(\.id).max() ?? 0, batch.hasMore_p == 0 ? batch.group.lastMsgID : 0)
                groups[group.id] = group
            }
        }
        var next = state; next.groups = Array(groups.values); next.messages = Array(messages.values)
        try save(next)
    }
    func sent(_ text: String, to target: PrivateConversation, account: Account, receipt: Tiebapure_Official_CommitResult, type: Int = 1) throws {
        var message = Tiebapure_Official_ChatMessage()
        message.id = receipt.id; message.groupID = receipt.groupID; message.type = Int32(type)
        message.fromUid = Int64(account.uid)!; message.toUid = Int64(target.userID)!
        message.content = text; message.timestamp = Int32(Date().timeIntervalSince1970)
        message.fromUser.id = message.fromUid; message.fromUser.nickname = account.displayName; message.fromUser.portrait = account.portrait
        message.toUser.id = message.toUid; message.toUser.nickname = target.name; message.toUser.portrait = target.portrait
        var next = state; next.messages.removeAll { peer($0) == target.userID && $0.id == message.id }; next.messages.append(message)
        try save(next)
    }
    func conversations() -> PrivateMessagePage<PrivateConversation> {
        let reads = Dictionary(state.read.map { (String($0.peerID), $0.messageID) }, uniquingKeysWith: max)
        let grouped = Dictionary(grouping: state.messages.filter { peer($0) != nil && $0.type != 22 }, by: { peer($0)! })
        let rows = grouped.compactMap { id, messages -> (PrivateConversation, Int64)? in
            guard let last = messages.max(by: { ($0.timestamp, $0.id) < ($1.timestamp, $1.id) }) else { return nil }
            let user = String(last.fromUid) == id ? last.fromUser : last.toUser
            let name = !user.displayName.isEmpty ? user.displayName : !user.nickname.isEmpty ? user.nickname : !user.name.isEmpty ? user.name : "用户" + id
            let conversation = PrivateConversation(userID: id, passUK: id, userType: 0, name: name, portrait: user.portrait, preview: Self.text(last), timestamp: Int64(last.timestamp), unread: messages.filter { String($0.fromUid) == id && $0.id > (reads[id] ?? 0) }.count)
            return (conversation, last.id)
        }.sorted { ($0.0.timestamp, $0.1, $0.0.userID) > ($1.0.timestamp, $1.1, $1.0.userID) }
        return PrivateMessagePage(items: rows.map(\.0), hasMore: false, cursor: nil)
    }
    func markAllRead() throws {
        let incoming = state.messages.lazy.filter { $0.type != 22 && String($0.toUid) == self.accountID }
            .compactMap { message in self.peer(message).map { _ in (message.fromUid, message.id) } }
        try advanceReadCursors(Dictionary(incoming, uniquingKeysWith: max))
    }

    private func advanceReadCursors(_ updates: [Int64: Int64]) throws {
        let previous = Dictionary(state.read.map { ($0.peerID, $0.messageID) }, uniquingKeysWith: max)
        let changed = updates.filter { $0.value > previous[$0.key, default: 0] }
        guard !changed.isEmpty else { return }
        var next = state
        next.read.removeAll { changed[$0.peerID] != nil }
        next.read += changed.map { peer, message in
            var cursor = Tiebapure_Official_ReadCursor()
            cursor.peerID = peer
            cursor.messageID = message
            return cursor
        }
        try save(next)
    }
    func messages(_ id: String, before: Int64?) throws -> PrivateMessagePage<PrivateChatMessage> {
        guard let number = Int64(id), number > 0, String(number) == id, id != accountID else { throw PrivateMessageError.invalidUser }
        let events = state.messages.filter { peer($0) == id && String($0.fromUid) == id && String($0.toUid) == accountID }
        let watermarks = Dictionary(events.compactMap { event in PrivateMessageContent.readWatermark(event).map { (event.groupID, $0) } }, uniquingKeysWith: max)
        let all = state.messages.filter { peer($0) == id && $0.type != 22 }.sorted { ($0.timestamp, $0.id) < ($1.timestamp, $1.id) }
        if before == nil {
            try advanceReadCursors([number: all.map(\.id).max() ?? 0])
        }
        let filtered = all.filter { before == nil || $0.id < before! }, items = Array(filtered.suffix(100))
        return PrivateMessagePage(items: items.map { PrivateMessageContent.message($0, read: String($0.fromUid) == accountID && $0.id <= (watermarks[$0.groupID] ?? 0)) }, hasMore: filtered.count > items.count, cursor: items.first?.id)
    }
    private func save(_ next: Tiebapure_Official_Inbox) throws {
        let bytes = try next.serializedData()
        guard bytes.count <= 16_777_216 else { throw PrivateMessageError.invalidResponse }
        if let file {
            var directory = file.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var values = URLResourceValues(); values.isExcludedFromBackup = true; try directory.setResourceValues(values)
            try bytes.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
        state = next
    }
    static func text(_ message: Tiebapure_Official_ChatMessage) -> String {
        switch message.type { case 1: return message.content; case 2: return "[图片消息]"; case 3: return "[语音消息]"; case 22: return ""; default: return "[暂不支持的消息（类型\(message.type)）]" }
    }
}
