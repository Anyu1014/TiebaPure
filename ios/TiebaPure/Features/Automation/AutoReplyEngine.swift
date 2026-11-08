import Foundation

@MainActor
protocol AutoReplyBackend {
    func followed(_ account: Account) async throws -> [Forum]
    func threads(_ forum: Forum, page: Int, account: Account) async throws -> [AutoReplyThread]
    func body(_ thread: AutoReplyThread, account: Account, forum: Forum) async throws -> String
    func reply(_ job: AutoReplyJob, account: Account) async throws -> UInt64?
}

@MainActor
final class AutoReplyEngine {
    let store: AutoReplyStore
    private let api: any AutoReplyBackend
    private let currentAccount: () async throws -> Account?
    private let repliesAllowed: () -> Bool
    private let now: () -> Date
    private var scanning = false
    private var sending = false
    init(store: AutoReplyStore, api: any AutoReplyBackend, currentAccount: @escaping () async throws -> Account?, repliesAllowed: @escaping () -> Bool, now: @escaping () -> Date = Date.init) {
        self.store = store; self.api = api; self.currentAccount = currentAccount; self.repliesAllowed = repliesAllowed; self.now = now
    }
    func recover(_ uid: String) throws {
        try store.commit(uid) { state in
            for (id, var job) in state.jobs {
                if job.status == .sending { job.status = .unknown; job.note = "上次发送中断，请打开原帖确认，不会自动重发" }
                if job.status == .queued { job.status = .review; job.note = "监测曾中断，继续回复前需要确认"; job.reviewPrompted = false }
                state.jobs[id] = job
            }
            for (id, var cursor) in state.cursors where cursor.active { cursor.catchUp = true; state.cursors[id] = cursor }
        }
    }
    func deferQueued(_ uid: String) throws {
        try store.commit(uid) { state in
            for (id, var job) in state.jobs where job.status == .queued {
                job.status = .review; job.note = "网络检查曾中断，继续回复前需要确认"; job.reviewPrompted = false; state.jobs[id] = job
            }
        }
    }
    private func valid(_ account: Account, rule: AutoReplyRule? = nil) async throws -> Bool {
        guard try await currentAccount()?.sessionIdentity == account.sessionIdentity, store.accountID == account.id, store.enabled(account.id) else { return false }
        return rule.map { rule in store.state.rules.contains { $0.id == rule.id && $0.revision == rule.revision && $0.enabled } } ?? true
    }
    func scan(_ account: Account, catchUp: Bool) async throws {
        guard !scanning, try await valid(account) else { return }
        let rules = store.state.rules.filter(\.enabled)
        guard !rules.isEmpty else { return }
        scanning = true; defer { scanning = false }
        let followed = Set(try await api.followed(account).map(\.id))
        for rule in rules {
            guard try await valid(account, rule: rule) else { break }
            if followed.contains(rule.forum.id) { try await scanRule(account, rule: rule, catchUp: catchUp) }
            else if var cursor = store.state.cursors[rule.id] {
                if !cursor.active { cursor.page = 1; cursor.newest = cursor.watermark; cursor.head = []; cursor.checked = [] }
                cursor.active = true; cursor.catchUp = true
                try store.commit(account.id) { $0.cursors[rule.id] = cursor }
            }
        }
    }
    private func scanRule(_ account: Account, rule: AutoReplyRule, catchUp: Bool) async throws {
        let uid = account.id
        guard var cursor = store.state.cursors[rule.id] else {
            let rows = try await api.threads(rule.forum, page: 1, account: account)
            guard try await valid(account, rule: rule) else { return }
            try store.commit(uid) { $0.cursors[rule.id] = AutoReplyCursor(watermark: rows.map(\.createdAt).max() ?? 0, known: Set(rows.map(\.id))) }
            return
        }
        if !cursor.active { cursor.active = true; cursor.catchUp = catchUp; cursor.newest = cursor.watermark; cursor.head = []; cursor.checked = []; cursor.page = 1 }
        if catchUp { cursor.catchUp = true }
        let startPage = cursor.page
        let pages = startPage > 1 ? [1, startPage, startPage + 1] : [1, 2, 3]
        for page in pages {
            let rows = try await api.threads(rule.forum, page: page, account: account)
            guard try await valid(account, rule: rule) else { return }
            if page == 1 { cursor.head = Set(rows.map(\.id)) }
            var boundary = rows.isEmpty || rows.allSatisfy { $0.pinned && cursor.known.contains($0.id) }
            for thread in rows {
                try Task.checkCancellation()
                let old = cursor.known.contains(thread.id) || (cursor.watermark > 0 && thread.createdAt > 0 && thread.createdAt < cursor.watermark)
                if old { if !thread.pinned { boundary = true }; continue }
                if cursor.checked.contains(thread.id) { continue }
                if store.state.jobs[thread.id] == nil && store.state.deletedThreadIDs?.contains(thread.id) != true && thread.authorID != uid {
                    var hits = rule.keywords.filter { thread.title.localizedCaseInsensitiveContains($0) }
                    if hits.isEmpty {
                        let body = try await api.body(thread, account: account, forum: rule.forum)
                        hits = rule.keywords.filter { body.localizedCaseInsensitiveContains($0) }
                    }
                    guard try await valid(account, rule: rule) else { return }
                    if !hits.isEmpty {
                        let job = AutoReplyJob(id: thread.id, title: thread.title, ruleID: rule.id, ruleRevision: rule.revision, forum: rule.forum, keywords: hits, message: rule.message, delaySeconds: rule.delaySeconds, discoveredAt: now(), dueAt: now().addingTimeInterval(Double(rule.delaySeconds)), status: cursor.catchUp ? .review : .queued)
                        try store.commit(uid) { state in guard state.jobs.count < 5000 else { throw AutoReplyError.capacity }; state.jobs[thread.id] = job }
                    }
                }
                cursor.checked.insert(thread.id); cursor.newest = max(cursor.newest, thread.createdAt)
                guard cursor.checked.count <= 10000 else { throw AutoReplyError.capacity }
                try store.commit(uid) { $0.cursors[rule.id] = cursor }
            }
            if boundary {
                try store.commit(uid) { $0.cursors[rule.id] = AutoReplyCursor(watermark: cursor.newest, known: cursor.head) }
                return
            }
            cursor.page = page == 1 && startPage > 1 ? startPage : page + 1
            try store.commit(uid) { $0.cursors[rule.id] = cursor }
        }
        cursor.page = max(1, cursor.page - 1)
        try store.commit(uid) { $0.cursors[rule.id] = cursor }
    }
    var pendingPrompts: Set<Int64> { Set(store.state.jobs.values.filter { $0.status == .review && !$0.reviewPrompted }.map(\.id)) }
    func acknowledgePrompts(_ uid: String, ids: Set<Int64>) throws {
        try store.commit(uid) { state in
            for (id, var job) in state.jobs where job.status == .review && !job.reviewPrompted && ids.contains(id) {
                job.reviewPrompted = true; state.jobs[id] = job
            }
        }
    }
    func review(_ uid: String, id: Int64, approve: Bool) throws {
        try store.commit(uid) { state in
            guard var job = state.jobs[id], job.status == .review else { return }
            let rule = state.rules.first { $0.id == job.ruleID && $0.enabled && $0.revision == job.ruleRevision }
            if approve && store.enabled(uid) && rule != nil { job.status = .queued; job.dueAt = now().addingTimeInterval(Double(job.delaySeconds)); job.note = "补扫任务已由用户确认" }
            else { job.status = .skipped; job.note = approve ? "规则已修改或关闭" : "用户忽略" }
            state.jobs[id] = job
        }
    }
    func sendDue(_ account: Account) async throws {
        guard !sending, try await valid(account), repliesAllowed(), var job = store.state.jobs.values.filter({ $0.status == .queued && $0.dueAt <= now() }).min(by: { $0.dueAt < $1.dueAt }) else { return }
        sending = true; defer { sending = false }
        let uid = account.id
        guard let rule = store.state.rules.first(where: { $0.id == job.ruleID && $0.revision == job.ruleRevision && $0.enabled }) else {
            job.status = .skipped; job.note = "规则已修改或关闭"; try store.commit(uid) { $0.jobs[job.id] = job }; return
        }
        guard try await api.followed(account).contains(where: { $0.id == job.forum.id }) else {
            job.status = .skipped; job.note = "已不再关注该吧，本任务已取消"; try store.commit(uid) { $0.jobs[job.id] = job }; return
        }
        guard try await valid(account, rule: rule) else { return }
        try Task.checkCancellation()
        guard let latest = store.state.jobs[job.id], latest.status == .queued, latest.dueAt == job.dueAt else { return }
        job.status = .sending
        try store.commit(uid) { $0.jobs[job.id] = job }
        var confirmed = false
        do {
            job.postID = try await api.reply(job, account: account); confirmed = true
            job.status = .sent; job.note = "贴吧已确认发送成功"
            try store.commit(uid) { $0.jobs[job.id] = job }
        } catch {
            let unknown = confirmed || error is CancellationError || (error as? ContentSubmissionError) == .outcomeUnknown
            job.status = unknown ? .unknown : .failed
            job.note = unknown ? "发送结果无法确认，请打开原帖检查，不会自动重发" : String(error.localizedDescription.prefix(240))
            try? store.commit(uid) { $0.jobs[job.id] = job }
            store.setEnabled(uid, false)
            if error is CancellationError { throw error }
        }
    }
}
