import SwiftUI
import UIKit
import XCTest
@testable import TiebaPure

private final class ReplySamplePan: UIPanGestureRecognizer {
    var sampleState: UIGestureRecognizer.State = .possible
    var sampleTranslation = CGPoint.zero
    var sampleVelocity = CGPoint.zero
    override var state: UIGestureRecognizer.State { get { sampleState } set { sampleState = newValue } }
    override func translation(in view: UIView?) -> CGPoint { sampleTranslation }
    override func velocity(in view: UIView?) -> CGPoint { sampleVelocity }
}

@MainActor
final class FloatingReplyRenderingTests: XCTestCase {
    func testNativeInputCachesTextAndFloatingCollapseKeepsTheViewportAndDoesNotRefocus() async throws {
        let account = FixtureTiebaAPI.account
        let store = AccountStore(service: MemoryAccountStoreService(data: try JSONEncoder().encode(account)))
        let environment = AppEnvironment(accountStore: store, api: FixtureTiebaAPI(), logoutCoordinator: LogoutCoordinator(accountStore: store), contentDraftStore: ContentDraftStore(persistence: UnavailableContentDraftPersistenceBackend()))
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "ReplyRenderingTests-" + UUID().uuidString))
        let appearance = AppAppearanceStore(defaults: defaults)
        var target = ContentSubmissionTarget.newThread(in: FixtureTiebaAPI.forum)
        target.kind = .threadReply; target.threadID = 100
        let fixture = ScrollView { VStack { ForEach(0..<40) { Text("Reply row \($0)").frame(height: 44) } } }
            .floatingReplyComposer(account: account, target: target, active: true, focusRequest: 1)
            .environmentObject(environment).environmentObject(appearance)
        let host = UIHostingController(rootView: fixture)
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene); window.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        window.rootViewController = host; window.makeKeyAndVisible()
        defer { window.isHidden = true }
        await settle(host.view)
        let editor = try XCTUnwrap(descendants(host.view).compactMap { $0 as? ComposerTextView }.first)
        XCTAssertGreaterThan(editor.bounds.width, 50); XCTAssertLessThan(editor.bounds.width, 280)
        XCTAssertEqual(editor.bounds.height, 44, accuracy: 1)
        editor.insertText("live draft")
        await settle(host.view)
        XCTAssertEqual(environment.inlineReplyDrafts.draft(account: account, target: target).body, "live draft")
        editor.resignFirstResponder()
        await settle(host.view)
        let scroll = try XCTUnwrap(descendants(host.view).compactMap { $0 as? UIScrollView }.first { !($0 is UITextView) })
        let height = scroll.bounds.height
        func coordinator() throws -> RootTabScrollObserver.Coordinator {
            try XCTUnwrap(descendants(host.view).flatMap { $0.gestureRecognizers ?? [] }.compactMap { $0.delegate as? RootTabScrollObserver.Coordinator }.first)
        }
        func pan(_ coordinator: RootTabScrollObserver.Coordinator, distance: CGFloat, velocity: CGFloat) {
            let sample = ReplySamplePan(); sample.sampleState = .began; coordinator.panChanged(sample)
            sample.sampleState = .changed; sample.sampleTranslation.y = distance; sample.sampleVelocity.y = velocity; coordinator.panChanged(sample)
            sample.sampleState = .ended; coordinator.panChanged(sample)
        }
        XCTAssertTrue(try coordinator().active)
        pan(try coordinator(), distance: -24, velocity: -900)
        await settle(host.view)
        XCTAssertTrue(descendants(host.view).contains { $0 === editor }, "Collapse keeps the editor mounted")
        XCTAssertFalse(editor.isFirstResponder)
        XCTAssertEqual(scroll.bounds.height, height, accuracy: 1)
        pan(try coordinator(), distance: 24, velocity: 100)
        await settle(host.view)
        XCTAssertTrue(descendants(host.view).contains { $0 === editor })
        XCTAssertFalse(editor.isFirstResponder)
        pan(try coordinator(), distance: 24, velocity: 900)
        await settle(host.view)
        let restored = try XCTUnwrap(descendants(host.view).compactMap { $0 as? ComposerTextView }.first)
        XCTAssertTrue(restored === editor, "Expansion reuses the editor and its selection")
        XCTAssertEqual(ComposerEmoticonText.raw(restored.attributedText), "live draft")
        XCTAssertFalse(restored.isFirstResponder, "A scroll expansion must not reopen the keyboard")
        XCTAssertEqual(scroll.bounds.height, height, accuracy: 1)
    }

    func testReplyHeaderKeepsInputAtTheSameHeightAboveTheKeyboard() async throws {
        let account = FixtureTiebaAPI.account
        let store = AccountStore(service: MemoryAccountStoreService(data: try JSONEncoder().encode(account)))
        let environment = AppEnvironment(accountStore: store, api: FixtureTiebaAPI(), logoutCoordinator: LogoutCoordinator(accountStore: store), contentDraftStore: ContentDraftStore(persistence: UnavailableContentDraftPersistenceBackend()))
        let defaults = try XCTUnwrap(UserDefaults(suiteName: "ReplyRenderingTests-" + UUID().uuidString))
        let appearance = AppAppearanceStore(defaults: defaults)
        var target = ContentSubmissionTarget.newThread(in: FixtureTiebaAPI.forum)
        target.kind = .threadReply
        target.threadID = 100

        func fixture(_ target: ContentSubmissionTarget) -> some View {
            ScrollView { Color.clear.frame(height: 2_000) }
                .floatingReplyComposer(account: account, target: target, active: true, focusRequest: 0)
                .environmentObject(environment)
                .environmentObject(appearance)
        }

        let host = UIHostingController(rootView: fixture(target))
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        window.frame = CGRect(x: 0, y: 0, width: 390, height: 700)
        window.rootViewController = host
        window.makeKeyAndVisible()
        defer { window.isHidden = true }

        // Apply a repeatable keyboard-sized bottom exclusion to the actual hosting view.
        host.additionalSafeAreaInsets.bottom = 300
        func inputBottom() async throws -> CGFloat {
            await settle(host.view)
            let editor = try XCTUnwrap(descendants(host.view).compactMap { $0 as? ComposerTextView }.first)
            return editor.convert(editor.bounds, to: host.view).maxY
        }

        let baseline = try await inputBottom()
        let keyboardTop = host.view.safeAreaLayoutGuide.layoutFrame.maxY
        XCTAssertGreaterThan(keyboardTop - baseline, 8)

        target.parentPostID = 200
        target.replyUserDisplayName = "测试用户"
        for kind in [ContentSubmissionKind.postReply, .subpostReply] {
            target.kind = kind
            host.rootView = fixture(target)
            let bottom = try await inputBottom()
            XCTAssertEqual(bottom, baseline, accuracy: 1, "\(kind) header should grow above the input")
            XCTAssertGreaterThan(keyboardTop - bottom, 8, "\(kind) editor must clear the keyboard")
        }
    }

    private func settle(_ view: UIView) async {
        try? await Task.sleep(nanoseconds: 600_000_000)
        view.setNeedsLayout(); view.layoutIfNeeded()
    }
    private func descendants(_ view: UIView) -> [UIView] { [view] + view.subviews.flatMap(descendants) }
}
