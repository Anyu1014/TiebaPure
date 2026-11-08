import XCTest
@testable import TiebaPure

@MainActor
final class InlineReplyDraftTests: XCTestCase {
    func testFirstEntryIsBlankAndMainPostAndRepliesKeepIndependentLiveDrafts() {
        let cache = InlineReplyDraftCache(), account = FixtureTiebaAPI.account
        var main = ContentSubmissionTarget.newThread(in: FixtureTiebaAPI.forum); main.kind = .threadReply; main.threadID = 100
        var reply = main; reply.kind = .postReply; reply.parentPostID = 42
        XCTAssertEqual(cache.draft(account: account, target: main).body, "")
        var first = cache.draft(account: account, target: main); first.body = "主帖草稿"; cache.update(first, session: account.sessionIdentity)
        XCTAssertEqual(cache.draft(account: account, target: reply).body, "")
        var second = cache.draft(account: account, target: reply); second.body = "回帖草稿"; cache.update(second, session: account.sessionIdentity)
        XCTAssertEqual(cache.draft(account: account, target: main), first)
        XCTAssertEqual(cache.draft(account: account, target: reply), second)
        cache.finish(first, session: account.sessionIdentity)
        XCTAssertEqual(cache.draft(account: account, target: main).body, "")
        XCTAssertEqual(cache.draft(account: account, target: reply), second)
    }

    func testSessionChangeClearsDraftAndLateCallbacksCannotRestoreOrEraseAnotherSession() {
        let cache = InlineReplyDraftCache(), first = FixtureTiebaAPI.account
        var next = first; next.stoken += "-test-new-session"
        let target = ContentSubmissionTarget.newThread(in: FixtureTiebaAPI.forum)
        var old = cache.draft(account: first, target: target); old.body = "old"; cache.update(old, session: first.sessionIdentity)
        XCTAssertEqual(cache.draft(account: next, target: target).body, "")
        var current = cache.draft(account: next, target: target); current.body = "current"; cache.update(current, session: next.sessionIdentity)
        cache.update(old, session: first.sessionIdentity); cache.finish(old, session: first.sessionIdentity)
        XCTAssertEqual(cache.draft(account: next, target: target), current)
        var newer = current; newer.body += " edit"; cache.update(newer, session: next.sessionIdentity)
        cache.finish(current, session: next.sessionIdentity)
        XCTAssertEqual(cache.draft(account: next, target: target), newer)
    }
}
