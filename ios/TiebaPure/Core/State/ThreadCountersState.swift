import Combine
import Foundation

struct ThreadCounters: Equatable {
    var threadID: Int64
    var replyCount: Int
    var likeCount: Int
    var isLiked: Bool

    init(_ thread: ThreadSummary) {
        threadID = thread.id
        replyCount = thread.replyCount
        likeCount = thread.likeCount
        isLiked = thread.isLiked
    }

    func applying(to thread: ThreadSummary) -> ThreadSummary {
        guard thread.id == threadID else { return thread }
        var result = thread
        result.replyCount = replyCount
        result.likeCount = likeCount
        result.isLiked = isLiked
        return result
    }
}

struct ThreadCountersChange {
    let session: AccountSessionIdentity?
    let counters: ThreadCounters
}

@MainActor
final class ThreadCountersState {
    let didChange = PassthroughSubject<ThreadCountersChange, Never>()
    private var session: AccountSessionIdentity?
    private var values: [Int64: ThreadCounters] = [:]
    private var order: [Int64] = []

    func record(_ thread: ThreadSummary, session: AccountSessionIdentity?) {
        prepare(session: session)
        let counters = ThreadCounters(thread)
        guard values[thread.id] != counters else { return }
        values[thread.id] = counters
        order.removeAll { $0 == thread.id }
        order.append(thread.id)
        if order.count > 256 { values.removeValue(forKey: order.removeFirst()) }
        didChange.send(ThreadCountersChange(session: session, counters: counters))
    }

    func reconciled(_ threads: [ThreadSummary], session: AccountSessionIdentity?) -> [ThreadSummary] {
        prepare(session: session)
        return threads.map { values[$0.id]?.applying(to: $0) ?? $0 }
    }

    private func prepare(session: AccountSessionIdentity?) {
        guard self.session != session else { return }
        self.session = session
        values.removeAll()
        order.removeAll()
    }
}
