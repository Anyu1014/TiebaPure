import Foundation

struct PrivateReadingPosition: Codable, Equatable {
    var anchor: String
    var latest: String
    var atBottom: Bool
}

enum PrivateReadingPolicy {
    static func boundary(_ position: PrivateReadingPosition?, ids: [String]) -> Int? {
        guard let position, position.atBottom, let index = ids.firstIndex(of: position.latest), index + 1 < ids.count else { return nil }
        return index + 1
    }
    static func target(_ position: PrivateReadingPosition?, ids: [String]) -> String? {
        guard let position else { return nil }
        if position.atBottom { return boundary(position, ids: ids).map { ids[$0] } }
        return ids.contains(position.anchor) ? position.anchor : nil
    }
    static func needsHistory(_ position: PrivateReadingPosition?, ids: [String], hasMore: Bool) -> Bool {
        guard let position, !ids.isEmpty, hasMore else { return false }
        return !ids.contains(position.atBottom ? position.latest : position.anchor)
    }
}
