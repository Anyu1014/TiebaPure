import Foundation

struct BottomBarTransition {
    let progress: Double
    init(progress: Double) { self.progress = min(1, max(0, progress)) }
    var expandedOpacity: Double { 1 - fade(progress / 0.75) }
    var avatarOpacity: Double { fade((progress - 0.25) / 0.75) }
    private func fade(_ value: Double) -> Double {
        let t = min(1, max(0, value))
        return t * t * (3 - 2 * t)
    }
}

enum BottomBarPosition: String, CaseIterable, Identifiable, Sendable {
    case left, center, right
    var id: String { rawValue }
    var title: String { switch self { case .left: return "居左"; case .center: return "居中"; case .right: return "居右" } }
    var fraction: Double { switch self { case .left: return -1; case .center: return 0; case .right: return 1 } }
}

final class BottomBarMotion {
    private(set) var collapsed = false
    private var direction = 0
    private var distance: Double = 0
    func beginGesture() { direction = 0; distance = 0 }
    func expand() { collapsed = false; beginGesture() }
    func finishGesture(velocity: Double) -> Bool? {
        let expands = collapsed && direction == -1 && distance >= 12 && velocity.isFinite && velocity <= -650
        beginGesture()
        guard expands else { return nil }
        collapsed = false; return false
    }
    // Positive delta means browsing toward later content, not finger direction.
    func observe(delta: Double, velocity: Double) -> Bool? {
        guard delta.isFinite, velocity.isFinite, abs(delta) >= 0.1 else { return nil }
        let nextDirection = delta > 0 ? 1 : -1
        if direction != nextDirection { direction = nextDirection; distance = 0 }
        distance += abs(delta)
        let target = delta > 0 && distance >= 24 ? true : delta < 0 && velocity <= -650 && distance >= 12 ? false : collapsed
        guard target != collapsed else { return nil }
        collapsed = target; distance = 0
        return target
    }
}

final class BottomBarPanInput {
    private let motion: BottomBarMotion
    private var previous: (x: Double, y: Double)?
    init(motion: BottomBarMotion) { self.motion = motion }

    func begin(x: Double, y: Double) {
        cancel()
        if x.isFinite && y.isFinite { previous = (x, y) }
    }

    func move(x: Double, y: Double, velocityX: Double, velocityY: Double) -> Bool? {
        guard let old = previous, x.isFinite, y.isFinite, velocityX.isFinite, velocityY.isFinite else { return nil }
        previous = (x, y)
        guard abs(velocityY) > abs(velocityX) else { motion.beginGesture(); return nil }
        // Finger travel still exists on short pages and at a scroll boundary.
        return motion.observe(delta: old.y - y, velocity: -velocityY)
    }

    func end(velocityX: Double, velocityY: Double) -> Bool? {
        guard previous != nil else { return nil }
        previous = nil
        guard velocityX.isFinite, velocityY.isFinite, abs(velocityY) > abs(velocityX) else { motion.beginGesture(); return nil }
        return motion.finishGesture(velocity: -velocityY)
    }

    func cancel() { previous = nil; motion.beginGesture() }
}
