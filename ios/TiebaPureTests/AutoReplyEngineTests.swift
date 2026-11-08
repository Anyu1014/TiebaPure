import Foundation
#if canImport(UIKit)
import XCTest
@testable import TiebaPure
#endif

@MainActor
enum AutoReplyEngineScenarios {
    static func require(_ condition: @autoclosure () -> Bool, _ message: String) throws {
        if !condition() { throw NSError(domain: "AutoReplyTests", code: 1, userInfo: [NSLocalizedDescriptionKey: message]) }
    }
    @MainActor final class Client: AutoReplyBackend {
        var pages: [Int: [AutoReplyThread]] = [1: [AutoReplyThread(id: 10, title: "旧帖", authorID: "1", createdAt: 100, pinned: false)]]
        var bodyText = "普通内容"
        var reads = 0
        var writes = 0
        var failWrite = false
        var followedForums: [Forum]
        var beforeFollowed: (() throws -> Void)?
        var duringReply: (() throws -> Void)?
        let store: AutoReplyStore
        init(store: AutoReplyStore, forum: Forum) { self.store = store; followedForums = [forum] }
        func followed(_ account: Account) async throws -> [Forum] { try beforeFollowed?(); return followedForums }
        func threads(_ forum: Forum, page: Int, account: Account) async throws -> [AutoReplyThread] { pages[page] ?? [] }
        func body(_ thread: AutoReplyThread, account: Account, forum: Forum) async throws -> String { reads += 1; return bodyText }
        func reply(_ job: AutoReplyJob, account: Account) async throws -> UInt64? {
            try require(store.state.jobs[job.id]?.status == .sending, "write must have a durable claim")
            writes += 1
            try duringReply?()
            if failWrite { throw ContentSubmissionError.outcomeUnknown }
            return 9001
        }
    }
    @MainActor final class Fixture {
        let uid = "99"
        let account = Account(uid: "99", name: "self", displayName: "self", portrait: "", bduss: "fixture-bduss", stoken: "fixture-stoken", baiduID: nil, tbs: "fixture-tbs")
        let forum = Forum(id: 7, name: "测试", displayName: "测试吧", avatarURL: nil, memberCount: 0, threadCount: 0)
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("auto-reply-tests-" + UUID().uuidString)
        let defaults = UserDefaults(suiteName: "auto-reply-tests-" + UUID().uuidString)!
        let store: AutoReplyStore
        let client: Client
        var current: Account?
        var clock = Date(timeIntervalSince1970: 200)
        var engine: AutoReplyEngine!
        var rule: AutoReplyRule!
        init() async throws {
            store = AutoReplyStore(root: root, defaults: defaults)
            client = Client(store: store, forum: forum)
            current = account
            store.selectAccount(uid); try await store.load(uid); store.setEnabled(uid, true)
            rule = AutoReplyRule(forum: forum, keywords: ["收物"], message: "有货#(呵呵)", delaySeconds: 30)
            try store.saveRule(uid, rule: rule)
            engine = AutoReplyEngine(store: store, api: client, currentAccount: { [weak self] in self?.current }, repliesAllowed: { true }, now: { [weak self] in self!.clock })
        }
        func newPost(catchUp: Bool = false, title: String = "收物", author: String = "1") async throws {
            try await engine.scan(account, catchUp: true)
            client.pages[1] = [AutoReplyThread(id: 11, title: title, authorID: author, createdAt: 101, pinned: false), AutoReplyThread(id: 10, title: "旧帖", authorID: "1", createdAt: 100, pinned: false)]
            try await engine.scan(account, catchUp: catchUp)
        }
        func cleanup() { try? FileManager.default.removeItem(at: root) }
    }
    static func run() async throws {
        let f = try await Fixture(); defer { f.cleanup() }
        try await f.engine.scan(f.account, catchUp: true)
        try require(f.store.state.jobs.isEmpty && f.client.reads == 0, "baseline must not reply to history")
        try await f.newPost()
        try require(f.store.state.jobs[11]?.status == .queued && f.client.reads == 0, "title hit must queue without fetching body")
        try await f.engine.sendDue(f.account); try require(f.client.writes == 0, "delay must be respected")
        f.clock = Date(timeIntervalSince1970: 230)
        try await f.engine.sendDue(f.account); try await f.engine.sendDue(f.account)
        try require(f.client.writes == 1 && f.store.state.jobs[11]?.status == .sent && f.store.state.jobs[11]?.postID == 9001, "receipt and exactly-once ledger")
        let reloaded = AutoReplyStore(root: f.root, defaults: f.defaults)
        reloaded.selectAccount(f.uid); try await reloaded.load(f.uid)
        try require(reloaded.state.jobs[11]?.message == "有货#(呵呵)", "journal must survive restart with native emoji codes")

        let offline = try await Fixture(); defer { offline.cleanup() }
        offline.client.bodyText = "首楼里有收物关键词"
        try await offline.newPost(catchUp: true, title: "其他标题")
        try require(offline.client.reads == 1 && offline.store.state.jobs[11]?.status == .review, "offline body hit requires review")
        try require(offline.engine.pendingPrompts == [11], "background-created reviews must still prompt after loading")
        try offline.engine.acknowledgePrompts(offline.uid, ids: [11])
        let promptReload = AutoReplyStore(root: offline.root, defaults: offline.defaults)
        promptReload.selectAccount(offline.uid); try await promptReload.load(offline.uid)
        try require(promptReload.state.jobs[11]?.reviewPrompted == true, "prompt acknowledgement must survive restart")
        try offline.engine.recover(offline.uid)
        try require(offline.engine.pendingPrompts.isEmpty, "already-shown reviews must not trigger another alert without new matches")
        offline.clock = Date(timeIntervalSince1970: 500)
        try await offline.engine.sendDue(offline.account); try require(offline.client.writes == 0, "review must never auto-send")
        try offline.engine.review(offline.uid, id: 11, approve: true)
        try await offline.engine.sendDue(offline.account); try require(offline.client.writes == 0, "confirmation starts a new delay")
        offline.clock = Date(timeIntervalSince1970: 530)
        try await offline.engine.sendDue(offline.account); try require(offline.client.writes == 1, "approved task must become usable")

        let unknown = try await Fixture(); defer { unknown.cleanup() }
        try await unknown.newPost(); unknown.clock = Date(timeIntervalSince1970: 300); unknown.client.failWrite = true
        try await unknown.engine.sendDue(unknown.account)
        try require(unknown.store.state.jobs[11]?.status == .unknown && !unknown.store.enabled(unknown.uid), "ambiguous writes pause automation")
        unknown.store.setEnabled(unknown.uid, true); try unknown.engine.recover(unknown.uid)
        try await unknown.engine.sendDue(unknown.account); try require(unknown.client.writes == 1, "unknown write must not retry")

        let changed = try await Fixture(); defer { changed.cleanup() }
        try await changed.newPost(); changed.clock = Date(timeIntervalSince1970: 300)
        changed.client.beforeFollowed = { changed.current = nil }
        try await changed.engine.sendDue(changed.account); try require(changed.client.writes == 0, "account switch blocks dispatch")
        changed.current = changed.account
        changed.client.beforeFollowed = { try changed.engine.recover(changed.uid) }
        try await changed.engine.sendDue(changed.account)
        try require(changed.client.writes == 0 && changed.store.state.jobs[11]?.status == .review, "recovered task must not escape review during preflight")

        let noHit = try await Fixture(); defer { noHit.cleanup() }
        try await noHit.newPost(catchUp: true, title: "没有命中")
        try require(noHit.store.state.jobs.isEmpty, "no hit must not create a prompt")
        let own = try await Fixture(); defer { own.cleanup() }
        try await own.newPost(author: own.uid)
        try require(own.store.state.jobs.isEmpty && own.client.reads == 0, "own threads excluded")

        let paged = try await Fixture(); defer { paged.cleanup() }
        try await paged.engine.scan(paged.account, catchUp: true)
        for page in 1...3 { paged.client.pages[page] = (1...20).map { AutoReplyThread(id: Int64(1000 - page * 20 - $0), title: "收物", authorID: "1", createdAt: 200, pinned: false) } }
        paged.client.pages[4] = [AutoReplyThread(id: 10, title: "旧帖", authorID: "1", createdAt: 100, pinned: false)]
        try await paged.engine.scan(paged.account, catchUp: true)
        try require(paged.store.state.cursors[paged.rule.id]?.page == 3 && paged.store.state.cursors[paged.rule.id]?.watermark == 100, "partial scans must resume without advancing the watermark")
        try await paged.engine.scan(paged.account, catchUp: false)
        try require(paged.store.state.jobs.count == 60 && paged.store.state.jobs.values.allSatisfy { $0.status == .review }, "entire offline gap keeps review mode across pages")
        try require(paged.store.state.cursors[paged.rule.id]?.watermark == 200, "watermark commits only on scan completion")

        let short = try await Fixture(); defer { short.cleanup() }
        try await short.engine.scan(short.account, catchUp: true)
        short.client.pages = [1: [AutoReplyThread(id: 11, title: "收物", authorID: "1", createdAt: 102, pinned: false)], 2: [AutoReplyThread(id: 12, title: "收物", authorID: "1", createdAt: 101, pinned: false)], 3: [AutoReplyThread(id: 10, title: "旧帖", authorID: "1", createdAt: 100, pinned: false)]]
        try await short.engine.scan(short.account, catchUp: true)
        try require(Set(short.store.state.jobs.keys) == [11, 12], "short API pages must not truncate the new-post gap")
        short.client.followedForums = []; short.clock = Date(timeIntervalSince1970: 500)
        try short.engine.review(short.uid, id: 11, approve: true)
        short.clock = Date(timeIntervalSince1970: 530); try await short.engine.sendDue(short.account)
        try require(short.client.writes == 0 && short.store.state.jobs[11]?.status == .skipped, "unfollowed threads cannot receive automatic replies")

        var moved = short.rule!
        moved.forum = Forum(id: 8, name: "另一吧", displayName: "另一吧", avatarURL: nil, memberCount: 0, threadCount: 0)
        try short.store.saveRule(short.uid, rule: moved)
        try require(short.store.state.cursors[moved.id] == nil, "changing the selected forum must start a fresh baseline")

        let deletion = try await Fixture(); defer { deletion.cleanup() }
        try await deletion.newPost()
        var second = deletion.store.state.jobs[11]!; second.id = 12; second.status = .review
        try deletion.store.commit(deletion.uid) { $0.jobs[12] = second }
        // The previous release did not encode deletion markers.
        let legacyData = try JSONEncoder().encode(deletion.store.state)
        let legacyState = try JSONDecoder().decode(AutoReplyState.self, from: legacyData)
        try require(legacyState.deletedThreadIDs == nil, "old journals must remain readable")
        deletion.clock = Date(timeIntervalSince1970: 300)
        deletion.client.beforeFollowed = { try deletion.store.deleteJobs(deletion.uid, ids: [11]) }
        try await deletion.engine.sendDue(deletion.account)
        try require(deletion.client.writes == 0 && deletion.store.state.jobs[11] == nil && deletion.store.state.jobs[12] != nil, "selected deletion must cancel only the selected pending task")
        deletion.client.beforeFollowed = nil
        try deletion.store.commit(deletion.uid) { $0.cursors[deletion.rule.id] = AutoReplyCursor(watermark: 100, known: [10]) }
        try await deletion.engine.scan(deletion.account, catchUp: false)
        try require(deletion.store.state.jobs[11] == nil, "deleted history must not reappear during rescans")
        try deletion.store.deleteJobs(deletion.uid, ids: Set(deletion.store.state.jobs.keys))
        let deletedReload = AutoReplyStore(root: deletion.root, defaults: deletion.defaults)
        deletedReload.selectAccount(deletion.uid); try await deletedReload.load(deletion.uid)
        try require(deletedReload.state.jobs.isEmpty && deletedReload.state.deletedThreadIDs == [11, 12], "clear-all must persist only deduplication IDs")
        deletedReload.selectAccount("100"); try await deletedReload.load("100")
        var rejectedOldAccount = false
        do { try deletedReload.deleteJobs(deletion.uid, ids: [11]) } catch is CancellationError { rejectedOldAccount = true }
        try require(rejectedOldAccount && deletedReload.state.deletedThreadIDs == nil, "history deletion must remain account scoped")
        try require(deletion.engine.pendingPrompts.isEmpty, "deleted reviews must not create alerts")

        let late = try await Fixture(); defer { late.cleanup() }
        try await late.newPost(); late.clock = Date(timeIntervalSince1970: 300)
        late.client.duringReply = { try late.store.deleteJobs(late.uid, ids: [11]) }
        try await late.engine.sendDue(late.account)
        try require(late.client.writes == 1 && late.store.state.jobs.isEmpty && late.store.state.deletedThreadIDs == [11], "late receipts cannot restore deleted history")
    }
}
#if canImport(UIKit)
final class AutoReplyEngineTests: XCTestCase {
    @MainActor func testAutomationBaselineDelayCatchUpPersistenceAndSessionIsolation() async throws { try await AutoReplyEngineScenarios.run() }
}
#endif
