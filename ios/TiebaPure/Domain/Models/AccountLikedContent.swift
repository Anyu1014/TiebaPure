import Foundation

enum AccountLikesFilter: Int, CaseIterable, Identifiable, Hashable, Sendable {
    case all = 0, threads = 1, replies = 2
    var id: Int { rawValue }
    var title: String {
        switch self { case .all: return "全部"; case .threads: return "主帖"; case .replies: return "回帖" }
    }
}

struct AccountLikedContent: Equatable, Identifiable, Sendable {
    var threadID: Int64
    var forumID: Int64?
    var forumName: String
    var title: String
    var authorDisplayName: String
    var blocks: [ContentBlock]
    var postID: UInt64?
    var parentPostID: UInt64?

    var id: String { "\(threadID):\(postID ?? 0)" }
    var kindTitle: String { parentPostID != nil ? "楼中楼" : postID != nil ? "回帖" : "主帖" }
    var preview: String {
        blocks.map { block in
            switch block {
            case .image: return "[图片]"
            case .video: return "[视频]"
            default: return block.plainText ?? ""
            }
        }.joined()
    }
}

struct AccountLikedContentPage: Equatable, Sendable {
    var items: [AccountLikedContent]
    var currentPage: Int
    var hasMore: Bool
}
