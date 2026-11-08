import Foundation
#if canImport(UIKit)
import UIKit
import XCTest
@testable import TiebaPure
#endif

@MainActor
enum BottomBarScenarios {
    static func require(_ value: @autoclosure () -> Bool, _ reason: String) throws {
        if !value() { throw NSError(domain: "BottomBarTests", code: 1, userInfo: [NSLocalizedDescriptionKey: reason]) }
    }
    static func transition() throws {
        let start = BottomBarTransition(progress: -0.1), end = BottomBarTransition(progress: 1.1)
        try require(start.progress == 0 && start.expandedOpacity == 1 && start.avatarOpacity == 0, "expanded endpoint stays bounded")
        try require(end.progress == 1 && end.expandedOpacity == 0 && end.avatarOpacity == 1, "compact endpoint stays bounded")
        var previous = start
        for step in 0...1000 {
            let current = BottomBarTransition(progress: Double(step) / 1000)
            try require(current.expandedOpacity <= previous.expandedOpacity && current.avatarOpacity >= previous.avatarOpacity, "content fades monotonically with geometry")
            try require(current.expandedOpacity + current.avatarOpacity > 0.5, "no empty capsule between the tabs and avatar")
            try require(abs(current.expandedOpacity - previous.expandedOpacity) < 0.003 && abs(current.avatarOpacity - previous.avatarOpacity) < 0.003, "no opacity jump at either handoff boundary")
            previous = current
        }
    }
    static func motion() throws {
        let bar = BottomBarMotion()
        for _ in 0..<11 { try require(bar.observe(delta: 2, velocity: 100) == nil, "collapse needs distance") }
        try require(bar.observe(delta: 2, velocity: 100) == true, "browsing downward collapses")
        bar.beginGesture()
        for _ in 0..<100 { try require(bar.observe(delta: -2, velocity: -120) == nil, "slow reverse must not expand") }
        try require(bar.collapsed, "slow travel never expands even over a long distance")
        bar.beginGesture()
        try require(bar.observe(delta: -6, velocity: -800) == nil, "fast reverse needs minimum travel")
        try require(bar.observe(delta: -6, velocity: -800) == false, "fast reverse expands")
        _ = bar.observe(delta: 24, velocity: 100); bar.expand()
        try require(!bar.collapsed && bar.observe(delta: 2, velocity: 100) == nil, "manual expansion resets movement accumulation")
        for _ in 0..<20 { _ = bar.observe(delta: 10, velocity: 900); _ = bar.observe(delta: -2, velocity: -100) }
        try require(!bar.collapsed, "direction jitter must not accumulate")
        try require(bar.observe(delta: .nan, velocity: 100) == nil && bar.observe(delta: 20, velocity: .infinity) == nil, "invalid samples ignored")
        _ = bar.observe(delta: 24, velocity: 100); bar.beginGesture(); _ = bar.observe(delta: -12, velocity: 0)
        try require(bar.finishGesture(velocity: -900) == false, "a short fast flick must expand")
        _ = bar.observe(delta: 24, velocity: 100); bar.beginGesture(); _ = bar.observe(delta: -30, velocity: -100)
        try require(bar.finishGesture(velocity: -100) == nil && bar.collapsed, "a slow release must keep the compact state")
    }
    static func preferences() throws {
        let suite = "bottom-bar-test-" + UUID().uuidString
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let key = "appearance"
        let store = AppAppearanceStore(defaults: defaults, key: key)
        try require(store.bottomBarAutoCollapse && store.bottomBarShowsLabels && store.bottomBarPosition == .left, "first launch preserves labels, auto collapse and defaults left")
        store.select(.dark); store.setBottomBarShowsLabels(false); store.setBottomBarPosition(.right); store.setBottomBarAutoCollapse(false)
        let loaded = AppAppearanceStore(defaults: defaults, key: key)
        try require(!loaded.bottomBarAutoCollapse && !loaded.bottomBarShowsLabels && loaded.bottomBarPosition == .right && loaded.selection == .dark, "display preferences persist independently")
        loaded.setBottomBarAutoCollapse(true)
        try require(AppAppearanceStore(defaults: defaults, key: key).bottomBarAutoCollapse && !loaded.bottomBarShowsLabels && loaded.bottomBarPosition == .right, "re-enabling keeps labels and position preferences")
        loaded.reset()
        try require(loaded.bottomBarAutoCollapse && loaded.bottomBarShowsLabels && loaded.bottomBarPosition == .left && loaded.selection == .system, "reset restores defaults")
        defaults.set("invalid", forKey: key + ".tab-position")
        try require(AppAppearanceStore(defaults: defaults, key: key).bottomBarPosition == .left, "unknown positions fall back safely")
        try require(BottomBarPosition.allCases.map(\.fraction) == [-1, 0, 1], "position order is left center right")
    }
}
#if canImport(UIKit)
final class BottomBarMotionTests: XCTestCase {
    @MainActor func testContinuousContentHandoffAndClampedEndpoints() throws { try BottomBarScenarios.transition() }
    @MainActor func testScrollDirectionSpeedAndManualExpansion() throws { try BottomBarScenarios.motion() }
    @MainActor func testPositionAndLabelPreferencesPersistence() throws { try BottomBarScenarios.preferences() }

    @MainActor func testExpandedNativeBarRecoversFromTransientUIKitVisibility() {
        let bar = UITabBar()
        let state = NativeRootTabBarState()
        bar.alpha = 0
        bar.isUserInteractionEnabled = false
        bar.accessibilityElementsHidden = true
        state.attach(bar)
        bar.alpha = 1
        bar.isUserInteractionEnabled = true
        bar.accessibilityElementsHidden = false

        state.apply(collapsed: false, showsLabels: true)
        XCTAssertEqual(bar.alpha, 1)
        XCTAssertTrue(bar.isUserInteractionEnabled)
        XCTAssertFalse(bar.accessibilityElementsHidden)
    }

    @available(iOS 26.0, *)
    @MainActor func testIconOnlyArtworkIsSharedByBothNativeStatesAndRestoresLabels() throws {
        let entries = [("首页", "house"), ("进吧", "square.grid.2x2"), ("我的", "person.circle")]
        let tabs = entries.map { title, symbol in
            UITab(title: title, image: UIImage(systemName: symbol), identifier: symbol) { _ in UIViewController() }
        }
        let controller = UITabBarController(tabs: tabs)
        controller.view.layoutIfNeeded()
        let bar = controller.tabBar
        let originalFrame = bar.frame
        let originalArtwork = tabs.map(\.image)
        let state = NativeRootTabBarState()
        state.attach(bar, tabController: controller)
        controller.selectedTab = tabs[2]
        state.apply(collapsed: false, showsLabels: true)
        for tab in [tabs[2]] + tabs {
            if controller.selectedTab !== tab { controller.selectedTab = tab }
            state.apply(collapsed: false, showsLabels: false)
            controller.view.layoutIfNeeded()
            XCTAssertEqual(bar.frame, originalFrame)
            let items = try XCTUnwrap(bar.items)
            XCTAssertEqual(items.map(\.accessibilityLabel), entries.map { $0.0 })
            for (item, nativeTab) in zip(items, tabs) {
                let image = try XCTUnwrap(item.image)
                XCTAssertFalse(image.isSymbolImage)
                XCTAssertEqual(item.selectedImage?.pngData(), image.pngData())
                XCTAssertEqual(nativeTab.image?.pngData(), image.pngData())
            }
        }
        state.apply(collapsed: false, showsLabels: true)
        XCTAssertEqual(bar.items?.map(\.title), entries.map { $0.0 })
        XCTAssertEqual(tabs.map(\.image), originalArtwork)
    }
}
#endif
