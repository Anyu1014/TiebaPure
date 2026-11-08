import SwiftUI
import UIKit

struct RootTabScrollObserver: UIViewRepresentable {
    let active: Bool
    let motion: BottomBarMotion
    let onCollapse: (Bool) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(motion: motion, onCollapse: onCollapse) }
    func makeUIView(context: Context) -> AttachmentView {
        let view = AttachmentView()
        view.isUserInteractionEnabled = false
        view.changed = { [weak coordinator = context.coordinator] view in coordinator?.schedule(from: view) }
        return view
    }
    func updateUIView(_ view: AttachmentView, context: Context) {
        context.coordinator.active = active
        context.coordinator.onCollapse = onCollapse
        if active { context.coordinator.schedule(from: view) } else { context.coordinator.detach() }
    }
    static func dismantleUIView(_ view: AttachmentView, coordinator: Coordinator) { view.changed = nil; coordinator.detach() }

    final class AttachmentView: UIView {
        var changed: ((AttachmentView) -> Void)?
        override func didMoveToWindow() { super.didMoveToWindow(); changed?(self) }
        override func didMoveToSuperview() { super.didMoveToSuperview(); changed?(self) }
        override func layoutSubviews() { super.layoutSubviews(); changed?(self) }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var active = false
        var onCollapse: (Bool) -> Void
        private let input: BottomBarPanInput
        private weak var attachment: UIView?
        private weak var host: UIView?
        private var pending: DispatchWorkItem?
        private lazy var pan: UIPanGestureRecognizer = {
            let value = UIPanGestureRecognizer(target: self, action: #selector(panChanged(_:)))
            value.maximumNumberOfTouches = 1
            value.cancelsTouchesInView = false
            value.delaysTouchesBegan = false
            value.delaysTouchesEnded = false
            value.delegate = self
            return value
        }()

        init(motion: BottomBarMotion, onCollapse: @escaping (Bool) -> Void) {
            input = BottomBarPanInput(motion: motion)
            self.onCollapse = onCollapse
        }

        func schedule(from view: UIView) {
            guard active, view.window != nil else { detach(); return }
            pending?.cancel()
            let work = DispatchWorkItem { [weak self, weak view] in
                guard let self, let view, self.active, view.window != nil else { return }
                // The hosting surface contains every pushed page, not just one selected scroll view.
                var responder: UIResponder? = view
                while let value = responder, !(value is UIViewController) { responder = value.next }
                guard let controller = responder as? UIViewController else { return }
                self.attachment = view
                guard self.host !== controller.view else { return }
                self.detach()
                self.attachment = view
                self.host = controller.view
                controller.view.addGestureRecognizer(self.pan)
            }
            pending = work
            DispatchQueue.main.async(execute: work)
        }

        func detach() {
            pending?.cancel(); pending = nil
            host?.removeGestureRecognizer(pan)
            host = nil; attachment = nil
            input.cancel()
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            guard active, let attachment, let host, attachment.window != nil,
                  let touchedView = touch.view, touchedView.isDescendant(of: host),
                  attachment.bounds.contains(touch.location(in: attachment)) else { return false }
            var view: UIView? = attachment
            while let current = view {
                if current.isHidden || current.alpha < 0.01 { return false }
                view = current.superview
            }
            return true
        }

        @objc func panChanged(_ pan: UIPanGestureRecognizer) {
            guard active, let host else { return }
            let point = pan.translation(in: host)
            let velocity = pan.velocity(in: host)
            switch pan.state {
            case .began: input.begin(x: point.x, y: point.y)
            case .changed:
                if let value = input.move(x: point.x, y: point.y, velocityX: velocity.x, velocityY: velocity.y) { onCollapse(value) }
            case .ended:
                if let value = input.end(velocityX: velocity.x, velocityY: velocity.y) { onCollapse(value) }
            case .cancelled, .failed: input.cancel()
            default: break
            }
        }
    }
}
