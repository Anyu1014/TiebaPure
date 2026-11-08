import UIKit
import XCTest
@testable import TiebaPure

final class RootTabScrollObserverTests: XCTestCase {
    @MainActor
    func testLongShortAndStaticPagesUseIdenticalPanRules() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController()
        window.rootViewController = controller
        window.addSubview(controller.view)
        let motion = BottomBarMotion()
        let observer = RootTabScrollObserver.Coordinator(motion: motion, onCollapse: { _ in })
        observer.active = true
        defer { observer.detach() }
        for height in [2_000, 300, 0] {
            motion.expand()
            let page: UIView
            if height > 0 {
                let scroll = UIScrollView(frame: CGRect(x: 0, y: 0, width: 390, height: 600))
                scroll.contentSize = CGSize(width: 390, height: height)
                page = scroll
            } else { page = UIView(frame: CGRect(x: 0, y: 0, width: 390, height: 600)) }
            controller.view.addSubview(page)
            let attachment = RootTabScrollObserver.AttachmentView(frame: page.bounds)
            page.addSubview(attachment)
            observer.schedule(from: attachment)
            await withCheckedContinuation { continuation in DispatchQueue.main.async { continuation.resume() } }
            let pan = FixturePan()
            pan.sampleState = .began; observer.panChanged(pan)
            pan.sampleState = .changed; pan.point.y = -24; pan.speed.y = -900; observer.panChanged(pan)
            XCTAssertTrue(motion.collapsed, "Content height must not change sensitivity")
            pan.sampleState = .began; pan.point = .zero; observer.panChanged(pan)
            pan.sampleState = .changed; pan.point.y = 100; pan.speed.y = 120; observer.panChanged(pan)
            pan.sampleState = .ended; observer.panChanged(pan)
            XCTAssertTrue(motion.collapsed, "Slow downward travel must stay collapsed")
            pan.sampleState = .began; pan.point = .zero; observer.panChanged(pan)
            pan.sampleState = .changed; pan.point.y = 12; pan.speed.y = 900; observer.panChanged(pan)
            XCTAssertFalse(motion.collapsed, "Fast downward travel expands")
            observer.detach()
            page.removeFromSuperview()
        }
    }

    @MainActor
    func testObservationIsPassiveAndInactivePagesDoNotToggle() async throws {
        let scene = try XCTUnwrap(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController(); window.rootViewController = controller; window.addSubview(controller.view)
        let attachment = RootTabScrollObserver.AttachmentView(frame: controller.view.bounds)
        controller.view.addSubview(attachment)
        let motion = BottomBarMotion()
        let observer = RootTabScrollObserver.Coordinator(motion: motion, onCollapse: { _ in })
        observer.active = true; observer.schedule(from: attachment)
        await withCheckedContinuation { continuation in DispatchQueue.main.async { continuation.resume() } }
        defer { observer.detach() }
        let recognizer = try XCTUnwrap(controller.view.gestureRecognizers?.first { $0.delegate === observer } as? UIPanGestureRecognizer)
        XCTAssertFalse(recognizer.cancelsTouchesInView)
        XCTAssertFalse(recognizer.delaysTouchesBegan)
        XCTAssertFalse(recognizer.delaysTouchesEnded)
        XCTAssertEqual(recognizer.maximumNumberOfTouches, 1)
        XCTAssertTrue(observer.gestureRecognizer(recognizer, shouldRecognizeSimultaneouslyWith: UIPanGestureRecognizer()))
        let pan = FixturePan(); pan.sampleState = .began; observer.panChanged(pan)
        pan.sampleState = .changed; pan.point = CGPoint(x: 100, y: -30); pan.speed = CGPoint(x: 900, y: -40); observer.panChanged(pan)
        XCTAssertFalse(motion.collapsed)
        observer.active = false
        pan.point.y = -100; pan.speed = CGPoint(x: 0, y: -900); observer.panChanged(pan)
        XCTAssertFalse(motion.collapsed)
    }
}

private final class FixturePan: UIPanGestureRecognizer {
    var sampleState: UIGestureRecognizer.State = .possible
    var point = CGPoint.zero
    var speed = CGPoint.zero
    override var state: UIGestureRecognizer.State { get { sampleState } set { sampleState = newValue } }
    override func translation(in view: UIView?) -> CGPoint { point }
    override func velocity(in view: UIView?) -> CGPoint { speed }
}
