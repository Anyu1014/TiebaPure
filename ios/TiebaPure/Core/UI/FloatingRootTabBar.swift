import SwiftUI
import UIKit

extension Animation {
    static var floatingBarSpring: Animation { .interpolatingSpring(stiffness: 380, damping: 39) }
}

private struct FloatingRootTabSelectionKey: EnvironmentKey {
    static let defaultValue: Binding<RootTab>? = nil
}
private struct FloatingRootTabAccountKey: EnvironmentKey {
    static let defaultValue: Account? = nil
}
private struct NativeRootTabBarStateKey: EnvironmentKey {
    static let defaultValue: NativeRootTabBarState? = nil
}

extension EnvironmentValues {
    var nativeRootTabBarState: NativeRootTabBarState? {
        get { self[NativeRootTabBarStateKey.self] }
        set { self[NativeRootTabBarStateKey.self] = newValue }
    }
    var floatingRootTabAccount: Account? {
        get { self[FloatingRootTabAccountKey.self] }
        set { self[FloatingRootTabAccountKey.self] = newValue }
    }
    var floatingRootTabSelection: Binding<RootTab>? {
        get { self[FloatingRootTabSelectionKey.self] }
        set { self[FloatingRootTabSelectionKey.self] = newValue }
    }
}

extension View {
    func floatingRootTabBar(tab: RootTab, isVisible: Bool = true) -> some View {
        modifier(FloatingRootTabBarModifier(tab: tab, isVisible: isVisible))
    }

    func floatingGlass<S: InsettableShape>(in shape: S) -> some View {
        modifier(FloatingGlassModifier(shape: shape))
    }
}

private struct FloatingGlassModifier<S: InsettableShape>: ViewModifier {
    let shape: S
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    @ViewBuilder func body(content: Content) -> some View {
        if reduceTransparency {
            content.background(Color(uiColor: .secondarySystemBackground), in: shape)
        } else if #available(iOS 26.0, *) {
            content.glassEffect(.regular.interactive(), in: shape)
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .overlay(shape.strokeBorder(.white.opacity(0.5), lineWidth: 0.5))
                .shadow(color: .black.opacity(0.12), radius: 14, y: 5)
        }
    }
}

private struct FloatingRootTabBarModifier: ViewModifier {
    let tab: RootTab
    let isVisible: Bool
    @EnvironmentObject private var appearance: AppAppearanceStore
    @Environment(\.floatingRootTabSelection) private var selection
    @Environment(\.floatingRootTabAccount) private var account
    @Environment(\.nativeRootTabBarState) private var nativeState
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = BottomBarMotion()
    @State private var collapsed = false
    @State private var privateMessagesActive = false
    @State private var nativeBarFrame = CGRect.zero
    private var visible: Bool { isVisible && !privateMessagesActive }
    private var compact: Bool { collapsed && appearance.bottomBarAutoCollapse }
    private var spring: Animation? { reduceMotion ? nil : .floatingBarSpring }

    func body(content: Content) -> some View {
        barLayout(content: content)
            .onPreferenceChange(PrivateMessagesTabPreferenceKey.self) { privateMessagesActive = $0 }
            .toolbarBackground(.ultraThinMaterial, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .onChange(of: visible) { _ in expand() }
            .onChange(of: selection?.wrappedValue) { _ in expand() }
            .onChange(of: account?.id) { _ in expand() }
            .onChange(of: appearance.bottomBarAutoCollapse) { _ in expand() }
    }

    @ViewBuilder private func barLayout(content: Content) -> some View {
        // Regular-width iPad tabs live at the top. Keep the bottom control there.
        if #available(iOS 26.0, *), let nativeState,
           UIDevice.current.userInterfaceIdiom != .pad || horizontalSizeClass == .compact {
            nativeBody(content: content, state: nativeState)
        } else {
            floatingBody(content: content)
        }
    }

    private var scrollObserver: RootTabScrollObserver {
        RootTabScrollObserver(active: visible && appearance.bottomBarAutoCollapse && selection?.wrappedValue == tab, motion: motion) { collapsed = $0 }
    }

    @available(iOS 26.0, *)
    private func nativeBody(content: Content, state: NativeRootTabBarState) -> some View {
        content
            .background(scrollObserver)
            .background {
                if visible, let selection, selection.wrappedValue == tab {
                    NativeRootTabBarPresentation(
                        state: state,
                        collapsed: compact,
                        showsLabels: appearance.bottomBarShowsLabels,
                        position: appearance.bottomBarPosition,
                        reduceMotion: reduceMotion,
                        isSelected: { selection.wrappedValue == tab },
                        onFrame: { nativeBarFrame = $0 }
                    )
                }
            }
            .overlay {
                if visible, selection?.wrappedValue == tab {
                    GeometryReader { geometry in
                        let top = nativeBarFrame.isEmpty
                            ? geometry.size.height - 62
                            : nativeBarFrame.minY - geometry.frame(in: .global).minY
                        NativeRootTabAvatar(account: account, collapsed: compact, progress: compact ? 1 : 0,
                                            compactOffset: compactOffset(in: geometry.size.width), expand: expand)
                            .position(x: geometry.size.width / 2, y: top + 31)
                    }
                    .animation(appearance.bottomBarAutoCollapse ? spring : nil, value: compact)
                    .animation(spring, value: appearance.bottomBarPosition)
                }
            }
            .ignoresSafeArea(.container, edges: .bottom)
            .toolbar(visible ? .visible : .hidden, for: .tabBar)
    }

    private func floatingBody(content: Content) -> some View {
        GeometryReader { geometry in
            // Only the overlay changes size; the scroll viewport never follows the capsule.
            content
                .frame(width: geometry.size.width, height: geometry.size.height + geometry.safeAreaInsets.bottom)
                .background(scrollObserver)
                .overlay(alignment: .bottom) {
                    if visible, let selection {
                        FloatingRootTabBar(selection: selection, account: account, collapsed: compact,
                                           progress: compact ? 1 : 0,
                                           compactOffset: compactOffset(in: geometry.size.width),
                                           viewportWidth: geometry.size.width, expand: expand)
                            .animation(appearance.bottomBarAutoCollapse ? spring : nil, value: collapsed)
                            .animation(spring, value: appearance.bottomBarPosition)
                            .padding(.horizontal, 20)
                            .padding(.bottom, geometry.safeAreaInsets.bottom + 8)
                    }
                }
        }
        .toolbar(.hidden, for: .tabBar)
    }

    private func compactOffset(in width: CGFloat) -> CGFloat {
        max(0, (width - 40 - 62) / 2) * CGFloat(appearance.bottomBarPosition.fraction)
    }

    private func expand() {
        motion.expand()
        collapsed = false
    }
}

private struct NativeRootTabAvatar: View, Animatable {
    let account: Account?
    let collapsed: Bool
    var progress: CGFloat
    var compactOffset: CGFloat
    let expand: () -> Void

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(progress, compactOffset) }
        set { progress = newValue.first; compactOffset = newValue.second }
    }

    var body: some View {
        let transition = BottomBarTransition(progress: Double(progress))
        let fraction = CGFloat(transition.progress)
        RootTabAvatar(account: account, expand: expand)
            .floatingGlass(in: Capsule())
            .opacity(transition.avatarOpacity)
            .scaleEffect(0.88 + 0.12 * fraction)
            .offset(x: compactOffset * fraction)
            .allowsHitTesting(collapsed)
            .accessibilityHidden(!collapsed)
            .transaction { $0.animation = nil }
    }
}

private struct RootTabAvatar: View {
    let account: Account?
    let expand: () -> Void

    var body: some View {
        Button(action: expand) {
            AvatarView(url: account?.portraitURL, title: account?.displayName, size: 44)
                .frame(width: 62, height: 62)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("展开底栏")
        .accessibilityHint("显示首页、进吧和我的，不改变当前页面位置")
        .accessibilityIdentifier("collapsed-root-tab-avatar")
    }
}

private struct FloatingRootTabBar: View, Animatable {
    @Binding var selection: RootTab
    let account: Account?
    let collapsed: Bool
    var progress: CGFloat
    var compactOffset: CGFloat
    let viewportWidth: CGFloat
    let expand: () -> Void
    @EnvironmentObject private var appearance: AppAppearanceStore
    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(progress, compactOffset) }
        set { progress = newValue.first; compactOffset = newValue.second }
    }

    var body: some View {
        let expandedWidth = min(280, max(62, viewportWidth - 40))
        let transition = BottomBarTransition(progress: Double(progress))
        let fraction = CGFloat(transition.progress)
        ZStack {
        HStack(spacing: 0) {
            tab(.home, title: "首页", symbol: "house.fill", identifier: "root-tab-home")
            tab(.forums, title: "进吧", symbol: "square.grid.2x2.fill", identifier: "root-tab-forums")
            tab(.me, title: "我的", symbol: "person.crop.circle.fill", identifier: "root-tab-me")
        }
        .padding(4)
        .frame(width: expandedWidth, height: 62)
        .opacity(transition.expandedOpacity)
        .scaleEffect(1 - 0.06 * fraction)
        .allowsHitTesting(!collapsed)
        .accessibilityHidden(collapsed)
        RootTabAvatar(account: account, expand: expand)
        .opacity(transition.avatarOpacity)
        .scaleEffect(0.88 + 0.12 * fraction)
        .allowsHitTesting(collapsed)
        .accessibilityHidden(!collapsed)
        }
        .frame(width: expandedWidth + (62 - expandedWidth) * fraction, height: 62)
        .clipShape(Capsule())
        .floatingGlass(in: Capsule())
        .contentShape(Capsule())
        .offset(x: compactOffset * fraction)
        // The interpolated progress drives every layer; avoid a second implicit layout animation.
        .transaction { $0.animation = nil }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("主导航")
        .accessibilityIdentifier("floating-root-tab-bar")
    }

    private func tab(_ value: RootTab, title: String, symbol: String, identifier: String) -> some View {
        Button {
            selection = value
        } label: {
            VStack(spacing: 3) {
                Image(systemName: symbol).font(.system(size: appearance.bottomBarShowsLabels ? 23 : 29, weight: .semibold))
                if appearance.bottomBarShowsLabels { Text(title).font(.caption2.weight(.semibold)) }
            }
            .frame(maxWidth: .infinity, minHeight: 54)
            .foregroundStyle(selection == value ? Color.blue : Color.primary)
            .background(selection == value ? Color.primary.opacity(0.08) : .clear, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
        .accessibilityAddTraits(selection == value ? .isSelected : [])
        .accessibilityIdentifier(identifier)
    }
}
