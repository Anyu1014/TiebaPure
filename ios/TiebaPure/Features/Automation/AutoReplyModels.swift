import Foundation

struct AutoReplyRule: Codable, Identifiable, Equatable, Sendable {
    var id = UUID().uuidString
    var forum: Forum
    var keywords: [String]
    var message: String
    var delaySeconds = 30
    var enabled = true
    var revision = UUID().uuidString
    func validate() throws {
        guard forum.id > 0, !forum.name.isEmpty, forum.name.count <= 100,
              (1...30).contains(keywords.count), keywords.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.count <= 100 }),
              !message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, message.count <= 10000,
              (0...3600).contains(delaySeconds) else { throw AutoReplyError.invalidRule }
    }
    static func keywords(_ text: String) -> [String] {
        var seen = Set<String>()
        return text.components(separatedBy: CharacterSet(charactersIn: "\n,，")).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty && seen.insert($0).inserted }
    }
}
struct AutoReplyThread: Sendable {
    var id: Int64
    var title: String
    var authorID: String
    var createdAt: TimeInterval
    var pinned: Bool
}
struct AutoReplyCursor: Codable, Sendable {
    var watermark: TimeInterval
    var known: Set<Int64>
    var page = 1
    var active = false
    var catchUp = true
    var newest: TimeInterval = 0
    var head = Set<Int64>()
    var checked = Set<Int64>()
}
enum AutoReplyStatus: String, Codable, Sendable {
    case review, queued, sending, sent, failed, unknown, skipped
    var title: String {
        switch self {
        case .review: return "待确认"
        case .queued: return "等待发送"
        case .sending: return "正在发送"
        case .sent: return "已发送"
        case .failed: return "发送失败"
        case .unknown: return "结果待确认"
        case .skipped: return "已忽略"
        }
    }
}
struct AutoReplyJob: Codable, Identifiable, Sendable {
    var id: Int64
    var title: String
    var ruleID: String
    var ruleRevision: String
    var forum: Forum
    var keywords: [String]
    var message: String
    var delaySeconds: Int
    var discoveredAt: Date
    var dueAt: Date
    var status: AutoReplyStatus
    var note = ""
    var postID: UInt64?
    var reviewPrompted = false
}
struct AutoReplyState: Codable, Sendable {
    var version = 1
    var rules: [AutoReplyRule] = []
    var cursors: [String: AutoReplyCursor] = [:]
    var jobs: [Int64: AutoReplyJob] = [:]
    var deletedThreadIDs: Set<Int64>?
}
enum AutoReplyError: Error, LocalizedError {
    case invalidRule, tooManyRules, duplicateForum, capacity, unreadableBody
    var errorDescription: String? {
        switch self {
        case .invalidRule: return "请选择已关注贴吧，填写1至30个关键词、10000字内回复和0至3600秒延迟。"
        case .tooManyRules: return "最多监测20个贴吧。"
        case .duplicateForum: return "该贴吧已有规则，请编辑原规则。"
        case .capacity: return "自动化缓存达到上限，已暂停，检查位置未前移。"
        case .unreadableBody: return "原帖首楼暂不可读取，本轮检查未完成。"
        }
    }
}
