import SwiftProtobuf
import XCTest
@testable import TiebaPure

final class ThreadCountersTests: XCTestCase {
    func testUserTopicsReadOfficialPostInfoListAgreeField40() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "thread-counters", withExtension: "json"))
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let fixture = try XCTUnwrap(json["user_topic"] as? [String: Any])
        let hex = try XCTUnwrap(fixture["item_hex"] as? String)
        let bytes = stride(from: 0, to: hex.count, by: 2).map { offset in
            let start = hex.index(hex.startIndex, offsetBy: offset)
            return UInt8(hex[start..<hex.index(start, offsetBy: 2)], radix: 16)!
        }
        let item = try Tiebapure_Profile_UserThreadItem(serializedBytes: bytes)
        let topic = try XCTUnwrap(UserProfileMapper.thread(from: item))
        XCTAssertEqual(topic.id, 42)
        XCTAssertEqual(topic.replyCount, 5)
        XCTAssertEqual(topic.likeCount, 123)
    }

    func testFirstFloorUsesThreadCounterAndOtherRepliesKeepPostCounter() {
        var response = Tieba_PbPage_PbPageResponse()
        response.data.thread.id = 42
        response.data.thread.replyNum = 103
        response.data.thread.agree.agreeNum = 394
        response.data.thread.agree.hasAgree_p = 1
        var main = Tieba_Post()
        main.id = 1
        main.floor = 1
        main.agree.agreeNum = 336
        var reply = Tieba_Post()
        reply.id = 2
        reply.floor = 2
        reply.agree.agreeNum = 9
        response.data.postList = [main, reply]
        let page = PostMapper.threadPage(from: response)
        XCTAssertEqual(page.thread.replyCount, 103)
        XCTAssertEqual(page.mainPost?.likeCount, 394)
        XCTAssertEqual(page.posts.map(\.likeCount), [394, 9])
        XCTAssertEqual(page.mainPost?.isLiked, true)
    }

    func testExplicitNestedZeroRemainsAuthoritative() {
        var thread = Tieba_ThreadInfo()
        thread.agreeNum = 19
        thread.agree = Tieba_Agree()
        XCTAssertEqual(ThreadMapper.fromThreadInfo(thread, usersByID: [:]).likeCount, 0)
        thread.clearAgree()
        XCTAssertEqual(ThreadMapper.fromThreadInfo(thread, usersByID: [:]).likeCount, 19)
    }

    @MainActor
    func testDetailCountersReconcileListsWithoutChangingContentAndIsolateSessions() {
        let state = ThreadCountersState()
        var detail = ThreadSummary(id: 42, title: "detail", author: UserSummary(id: 1, name: "", displayName: "", portrait: ""), replyCount: 103, viewCount: 0, likeCount: 394, blocks: [])
        var old = detail
        old.title = "keep preview"
        old.replyCount = 102
        old.likeCount = 336
        state.record(detail, session: nil)
        let current = state.reconciled([old], session: nil).first
        XCTAssertEqual(current?.title, old.title)
        XCTAssertEqual(current?.likeCount, 394)
        XCTAssertEqual(current?.replyCount, 103)
        detail.likeCount = 395
        detail.isLiked = true
        state.record(detail, session: nil)
        XCTAssertEqual(state.reconciled([old], session: nil).first?.isLiked, true)
        XCTAssertEqual(state.reconciled([old], session: Account.preview.sessionIdentity), [old])
        XCTAssertEqual(state.reconciled([old], session: nil), [old])
    }
}
