import SwiftUI

extension View {
    func floatingReplyComposer(account: Account?, target: ContentSubmissionTarget?, active: Bool, focusRequest: Int, resetTarget: @escaping () -> Void = {}) -> some View {
        modifier(FloatingReplyComposerModifier(account: account, target: target, active: active, focusRequest: focusRequest, resetTarget: resetTarget))
    }
}

private struct FloatingReplyComposerModifier: ViewModifier {
    let account: Account?
    let target: ContentSubmissionTarget?
    let active: Bool
    let focusRequest: Int
    let resetTarget: () -> Void
    @EnvironmentObject private var environment: AppEnvironment
    @EnvironmentObject private var appearance: AppAppearanceStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = BottomBarMotion()
    @State private var collapsed = false
    @State private var editing = false
    @State private var handledFocusRequest = 0

    func body(content: Content) -> some View {
        content
            .background(RootTabScrollObserver(active: active && target != nil && appearance.bottomBarAutoCollapse && !editing, motion: motion) { collapsed = $0 })
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if active, let target {
                    // A constant inset keeps the reader viewport still during collapse.
                    Color.clear.frame(height: 78)
                        .overlay(alignment: .bottom) {
                            GeometryReader { geometry in
                                let width = min(480, max(62, geometry.size.width - 40))
                                let offset = max(0, (geometry.size.width - 40 - 62) / 2) * CGFloat(appearance.bottomBarPosition.fraction)
                                ReplyBarTransition(collapsed: compact, progress: compact ? 1 : 0,
                                                   expandedWidth: width, compactOffset: offset,
                                                   expanded: editor(for: target), expand: expand)
                                    .animation(reduceMotion ? nil : .floatingBarSpring, value: compact)
                                    .animation(reduceMotion ? nil : .floatingBarSpring, value: offset)
                                    .padding(.bottom, 8)
                                    // Extra rows grow above the keyboard-anchored input.
                                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .bottom)
                            }
                        }
                }
            }
            .onChange(of: focusRequest) { _ in expand() }
            .onChange(of: account?.sessionIdentity) { _ in expand(); editing = false }
            .onChange(of: appearance.bottomBarAutoCollapse) { _ in expand() }
    }

    private var compact: Bool { collapsed && appearance.bottomBarAutoCollapse }

    private func expand() {
        motion.expand()
        collapsed = false
    }

    @ViewBuilder private func editor(for target: ContentSubmissionTarget) -> some View {
        if let account {
            let draft = environment.inlineReplyDrafts.draft(account: account, target: target)
            VStack(spacing: 8) {
                if target.kind != .threadReply {
                    HStack {
                        Text(target.prompt).font(.caption).lineLimit(1)
                        Spacer()
                        Button("取消回复", action: resetTarget).font(.caption)
                    }
                    .padding(10)
                    .floatingGlass(in: Capsule())
                }
                ContentComposerView(
                    target: target, initialBody: draft.body, initialImages: draft.images,
                    inline: true, focusRequest: focusRequest,
                    takeFocusRequest: {
                        guard focusRequest > handledFocusRequest else { return false }
                        handledFocusRequest = focusRequest
                        return true
                    },
                    onDraftChange: { environment.inlineReplyDrafts.update($0, session: account.sessionIdentity) },
                    onEditingChange: { editing = $0; if $0 { expand() } },
                    onCancel: {},
                    onSaveDraft: { environment.inlineReplyDrafts.update($0, session: account.sessionIdentity) },
                    onSend: { request in
                        guard let current = try await environment.accountStore.load(), current.sessionIdentity == account.sessionIdentity else {
                            throw ContentSubmissionCoordinatorError.sessionTransition
                        }
                        let receipt = try await environment.contentSubmissionCoordinator.submit(account: account, request: request)
                        environment.inlineReplyDrafts.finish(request, session: account.sessionIdentity)
                        return receipt
                    }
                )
                .id(InlineReplyEditorIdentity(session: account.sessionIdentity, target: target.draftKey))
            }
        } else {
            Text("登录后回复帖子").foregroundStyle(.secondary).frame(maxWidth: .infinity).frame(height: 62)
        }
    }
}

private struct ReplyBarTransition<Expanded: View>: View, Animatable {
    let collapsed: Bool
    var progress: CGFloat
    let expandedWidth: CGFloat
    var compactOffset: CGFloat
    let expanded: Expanded
    let expand: () -> Void

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(progress, compactOffset) }
        set { progress = newValue.first; compactOffset = newValue.second }
    }

    var body: some View {
        let transition = BottomBarTransition(progress: Double(progress))
        let fraction = CGFloat(transition.progress)
        let width = expandedWidth + (62 - expandedWidth) * fraction
        ZStack(alignment: .bottom) {
            Color.clear.frame(width: width, height: 62).floatingGlass(in: Capsule()).allowsHitTesting(false)
            expanded
                .frame(width: expandedWidth)
                .mask(Rectangle().frame(width: width))
                .opacity(transition.expandedOpacity)
                .allowsHitTesting(!collapsed)
                .accessibilityHidden(collapsed)
            Button(action: expand) {
                Image(systemName: "arrowshape.turn.up.left").font(.title2).frame(width: 62, height: 62)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("展开回复输入框")
            .accessibilityIdentifier("thread-reply-collapsed")
            .opacity(transition.avatarOpacity)
            .scaleEffect(0.88 + 0.12 * fraction)
            .allowsHitTesting(collapsed)
            .accessibilityHidden(!collapsed)
        }
        .offset(x: compactOffset * fraction)
        .transaction { $0.animation = nil }
    }
}

private struct InlineReplyEditorIdentity: Hashable {
    let session: AccountSessionIdentity
    let target: String
}
