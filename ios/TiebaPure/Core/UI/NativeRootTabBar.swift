import SwiftUI
import UIKit

@available(iOS 26.0, *)
struct NativeRootTabBarPresentation: UIViewControllerRepresentable {
    let state: NativeRootTabBarState
    let collapsed: Bool
    let showsLabels: Bool
    let position: BottomBarPosition
    let reduceMotion: Bool
    let isSelected: () -> Bool
    let onFrame: (CGRect) -> Void

    func makeUIViewController(context: Context) -> Controller { Controller(state: state) }

    func updateUIViewController(_ controller: Controller, context: Context) {
        controller.presentation = self
        controller.isActive = true
        controller.attach()
    }

    static func dismantleUIViewController(_ controller: Controller, coordinator: ()) {
        controller.isActive = false
        controller.state.restore(ifOwnedBy: controller)
    }

    final class Controller: UIViewController {
        let state: NativeRootTabBarState
        var presentation: NativeRootTabBarPresentation?
        var isActive = true
        private var lastFrame = CGRect.zero

        init(state: NativeRootTabBarState) {
            self.state = state
            super.init(nibName: nil, bundle: nil)
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            attach()
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            attach()
        }

        func attach() {
            guard isActive, view.window != nil, let presentation, presentation.isSelected(),
                  let controller = tabBarController ?? findTabController(view.window?.rootViewController) else { return }
            state.attach(controller.tabBar, owner: self, tabController: controller)
            state.apply(collapsed: presentation.collapsed, showsLabels: presentation.showsLabels,
                        position: presentation.position, animated: !presentation.reduceMotion)
            let bar = controller.tabBar
            let layoutFrame = CGRect(x: bar.center.x - bar.bounds.width / 2, y: bar.center.y - bar.bounds.height / 2,
                                     width: bar.bounds.width, height: bar.bounds.height)
            let frame = bar.superview?.convert(layoutFrame, to: nil) ?? .zero
            if frame != lastFrame {
                lastFrame = frame
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.isActive, self.view.window != nil,
                          let presentation = self.presentation, presentation.isSelected(),
                          self.lastFrame == frame else { return }
                    presentation.onFrame(frame)
                }
            }
        }

        private func findTabController(_ root: UIViewController?) -> UITabBarController? {
            guard let root else { return nil }
            if let tab = root as? UITabBarController { return tab }
            return root.children.lazy.compactMap { self.findTabController($0) }.first
        }
    }
}

@MainActor
final class NativeRootTabBarState {
    private struct ItemStyle {
        let item: UITabBarItem
        let title: String?
        let accessibilityLabel: String?
        let insets: UIEdgeInsets
        let image: UIImage?
        let selectedImage: UIImage?
    }
    private weak var bar: UITabBar?
    private weak var owner: AnyObject?
    private weak var tabController: UITabBarController?
    private var styles: [ItemStyle] = []
    private var restoreTabArtwork: [ObjectIdentifier: () -> Void] = [:]
    private lazy var iconOnlyArtwork = [
        "首页": "house.fill", "进吧": "square.grid.2x2.fill", "我的": "person.crop.circle.fill"
    ].compactMapValues { symbol in
        // Match the 2.1 glyph without the system symbol's selected-state scaling.
        let glyph = Image(systemName: symbol).font(.system(size: 29, weight: .semibold)).foregroundStyle(.black)
        let renderer = ImageRenderer(content: glyph)
        renderer.scale = 3
        return renderer.uiImage?.withRenderingMode(.alwaysTemplate)
    }
    private lazy var portraitArtwork = iconOnlyArtwork.reduce(into: [String: UIImage]()) { artwork, entry in
        let outward: CGFloat = entry.key == "首页" ? 3 : entry.key == "我的" ? -3 : 0
        // Offset the blank-title layout to the center of the iPhone glass capsule.
        let insets = UIEdgeInsets(top: -7, left: outward, bottom: 7, right: -outward)
        artwork[entry.key] = entry.value.withAlignmentRectInsets(insets)
    }
    private var collapsed: Bool?
    private var animator: UIViewPropertyAnimator?
    private var fadeAnimator: UIViewPropertyAnimator?
    private var identifier: String?
    private var showsLabels: Bool?

    func attach(_ bar: UITabBar, owner: AnyObject? = nil, tabController: UITabBarController? = nil) {
        if self.bar === bar {
            self.owner = owner
            return
        }
        restore()
        self.bar = bar
        self.owner = owner
        self.tabController = tabController
        identifier = bar.accessibilityIdentifier
        bar.accessibilityIdentifier = "native-root-tab-bar"
    }

    func apply(collapsed: Bool, showsLabels: Bool, position: BottomBarPosition = .center, animated: Bool = false) {
        guard let bar else { return }
        let previous = self.collapsed
        self.collapsed = collapsed
        let alpha: CGFloat = collapsed ? 0 : 1
        let width = bar.window?.bounds.width ?? bar.bounds.width
        let offset = max(0, (width - 40 - 62) / 2) * CGFloat(position.fraction)
        let transform = collapsed ? CGAffineTransform(translationX: offset, y: 0).scaledBy(x: 0.55, y: 0.94) : .identity
        let opacityChanged = bar.alpha != alpha
        if opacityChanged || bar.transform != transform {
            if animator?.state == .active {
                animator?.stopAnimation(false)
                animator?.finishAnimation(at: .current)
            }
            if opacityChanged, fadeAnimator?.state == .active {
                fadeAnimator?.stopAnimation(false)
                fadeAnimator?.finishAnimation(at: .current)
            }
            if animated, let previous, previous != collapsed || collapsed {
                let timing = UISpringTimingParameters(mass: 1, stiffness: 380, damping: 39, initialVelocity: .zero)
                let animator = UIViewPropertyAnimator(duration: 0.42, timingParameters: timing)
                animator.isUserInteractionEnabled = true
                self.animator = animator
                animator.addAnimations {
                    bar.transform = transform
                    if !collapsed, opacityChanged { bar.alpha = alpha }
                }
                if collapsed, opacityChanged {
                    // Clear the tabs before the avatar finishes moving into place.
                    let fade = UIViewPropertyAnimator(duration: 0.16, curve: .easeInOut) { bar.alpha = alpha }
                    fade.isUserInteractionEnabled = true
                    fadeAnimator = fade
                    fade.startAnimation()
                }
                animator.startAnimation()
            } else {
                UIView.performWithoutAnimation {
                    bar.alpha = alpha
                    bar.transform = transform
                }
            }
        }
        // UIKit can temporarily hide a bar while changing pages. Expanded is always visible.
        bar.isUserInteractionEnabled = !collapsed
        bar.accessibilityElementsHidden = collapsed

        let items = bar.items ?? []
        if items.map(ObjectIdentifier.init) != styles.map({ ObjectIdentifier($0.item) }) {
            restoreItemStyles()
            styles = items.map { item in
                ItemStyle(item: item, title: item.title, accessibilityLabel: item.accessibilityLabel,
                          insets: item.imageInsets, image: item.image, selectedImage: item.selectedImage)
            }
            self.showsLabels = nil
        }
        let blankTitle = "\u{00A0}"
        let titlesReconfigured = !showsLabels && items.contains { $0.title != blankTitle }
        let labelsChanged = self.showsLabels != showsLabels || titlesReconfigured
        self.showsLabels = showsLabels

        func updateArtwork() {
            if showsLabels {
                if labelsChanged { restoreItemStyles() }
                restoreNativeTabArtwork()
                return
            }

            let traits = tabController?.traitCollection ?? bar.traitCollection
            let portrait = traits.userInterfaceIdiom == .phone && traits.verticalSizeClass != .compact
            let artwork = portrait ? portraitArtwork : iconOnlyArtwork
            if #available(iOS 26.0, *) { applyNativeTabArtwork(artwork) }
            for style in styles {
                if labelsChanged {
                    // Keep a title line in both glass layers to preserve the native bar's size.
                    style.item.accessibilityLabel = style.accessibilityLabel ?? style.title
                    style.item.title = blankTitle
                    var insets = style.insets
                    insets.top += 6
                    insets.bottom -= 6
                    style.item.imageInsets = insets
                }
                guard let icon = style.title.flatMap({ artwork[$0] }) else { continue }
                if !matchesArtwork(style.item.image, icon) { style.item.image = icon }
                if !matchesArtwork(style.item.selectedImage, icon) { style.item.selectedImage = icon }
            }
        }

        // Commit the selected and unselected artwork together when toggling titles.
        if labelsChanged {
            if #available(iOS 27.0, *), let tabController {
                if animated {
                    tabController.performBatchUpdates(updateArtwork)
                } else {
                    UIView.performWithoutAnimation { tabController.performBatchUpdates(updateArtwork) }
                }
            } else {
                UIView.performWithoutAnimation(updateArtwork)
            }
            bar.setNeedsLayout()
        } else {
            updateArtwork()
        }
    }

    private func matchesArtwork(_ current: UIImage?, _ icon: UIImage) -> Bool {
        guard let current, let pixels = icon.cgImage else { return false }
        return current.cgImage === pixels && current.alignmentRectInsets == icon.alignmentRectInsets
    }

    @available(iOS 26.0, *)
    private func applyNativeTabArtwork(_ artwork: [String: UIImage]) {
        for tab in tabController?.tabs ?? [] {
            guard let icon = artwork[tab.title] else { continue }
            let id = ObjectIdentifier(tab)
            if restoreTabArtwork[id] == nil {
                let original = tab.image
                let selected = if #available(iOS 26.1, *) { tab.selectedImage } else { nil as UIImage? }
                restoreTabArtwork[id] = { [weak tab] in
                    tab?.image = original
                    if #available(iOS 26.1, *) { tab?.selectedImage = selected }
                }
            }
            if !matchesArtwork(tab.image, icon) { tab.image = icon }
            if #available(iOS 26.1, *), !matchesArtwork(tab.selectedImage, icon) { tab.selectedImage = icon }
        }
    }

    private func restoreNativeTabArtwork() {
        let restorations = restoreTabArtwork.values
        restoreTabArtwork.removeAll()
        for restoreImage in restorations { restoreImage() }
    }

    func restore(ifOwnedBy owner: AnyObject? = nil) {
        if let owner, self.owner !== owner { return }
        if animator?.state == .active { animator?.stopAnimation(true) }
        animator = nil
        if fadeAnimator?.state == .active { fadeAnimator?.stopAnimation(true) }
        fadeAnimator = nil
        restoreItemStyles()
        restoreNativeTabArtwork()
        if collapsed == true {
            bar?.alpha = 1
            bar?.transform = .identity
            bar?.isUserInteractionEnabled = true
            bar?.accessibilityElementsHidden = false
        }
        bar?.accessibilityIdentifier = identifier
        bar = nil
        self.owner = nil
        tabController = nil
        styles = []
        showsLabels = nil
        collapsed = nil
    }

    private func restoreItemStyles() {
        for style in styles {
            style.item.title = style.title
            style.item.accessibilityLabel = style.accessibilityLabel
            style.item.imageInsets = style.insets
            style.item.image = style.image
            style.item.selectedImage = style.selectedImage
        }
    }
}
