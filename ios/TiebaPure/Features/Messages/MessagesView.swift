import CoreText
import SwiftUI

struct PrivateMessagesTabPreferenceKey: PreferenceKey {
    static var defaultValue = false
    static func reduce(value: inout Bool, nextValue: () -> Bool) { value = value || nextValue() }
}

struct MessagesView: View {
    @EnvironmentObject private var environment: AppEnvironment
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.floatingRootTabSelection) private var rootSelection
    @ObservedObject var unread: MessageUnreadState

    let account: Account?
    private let openThreadInParent: ((ReaderSplitThreadRoute) -> Void)?

    @ObservedObject private var blocklistStore = BlocklistStore.shared
    @State private var selectedTab = 0
    private var kind: MessageKind { selectedTab == 1 ? .at : .reply }
    @State private var items: [MessageItem] = []
    @State private var nextPage = 1
    @State private var hasMore = true
    @State private var isLoading = false
    @State private var didLoad = false
    @State private var errorMessage: String?
    @State private var requestGeneration = 0
    @State private var loadTask: Task<MessagesPage, Error>?
    @State private var activeMessage: MessageItem?
    @State private var navigationSourceLifecycle = NavigationSourceLifecycleState()

    init(
        account: Account?,
        unread: MessageUnreadState,
        openThreadInParent: ((ReaderSplitThreadRoute) -> Void)? = nil
    ) {
        self.account = account
        self.unread = unread
        self.openThreadInParent = openThreadInParent
    }

    var body: some View {
        VStack(spacing: 0) {
            if account != nil {
                kindPicker
                    .readableWidth()
            }

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(TiebaPureTheme.ColorToken.readerGroupedBackground)
        .navigationTitle("消息")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(isPresented: messageIsActive) {
            if let activeMessage {
                ThreadDetailView(
                    account: account,
                    threadID: activeMessage.threadID,
                    initialPostID: activeMessage.postID
                )
                .interactiveNavigationPopStateSync {
                    self.activeMessage = nil
                }
            }
        }
        .task {
            guard didLoad == false else { return }
            await reload()
        }
        .task(id: badgeRefreshScope) {
            guard let account, badgeRefreshScope != nil else { return }
            await unread.refreshWhileVisible(
                account: account,
                environment: environment,
                viewing: selectedTab
            )
        }
        .onChange(of: selectedTab) { _ in
            resetForNewRequestScope()
            Task { await reload() }
        }
        .onChange(of: account?.sessionIdentity) { _ in
            activeMessage = nil
            resetForNewRequestScope()
            Task { await reload() }
        }
        .onChange(of: blocklistStore.entries) { _ in
            items.removeAll { TiebaContentFilter.shouldKeep(message: $0) == false }
        }
        .onAppear { navigationSourceLifecycle.didAppear() }
        .onDisappear {
            guard navigationSourceLifecycle.shouldTearDown(
                isPresentingLocalDestination: activeMessage != nil
            ) else { return }
            loadTask?.cancel()
            loadTask = nil
            requestGeneration += 1
            isLoading = false
        }
        .preference(key: PrivateMessagesTabPreferenceKey.self, value: selectedTab == 2)
        .accessibilityIdentifier("messages-screen")
        .fullScreenInteractiveNavigationPop()
    }

    private let tabTitles = ["回复我的", "@我的", "私信"]

    private struct BadgeRefreshScope: Hashable {
        let session: AccountSessionIdentity
        let tab: Int
    }

    private var badgeRefreshScope: BadgeRefreshScope? {
        guard scenePhase == .active, rootSelection?.wrappedValue == .me,
              let account else { return nil }
        return BadgeRefreshScope(session: account.sessionIdentity, tab: selectedTab)
    }

    private var kindPicker: some View {
        let tabsWithUnread = unread.tabs(for: account)
        return Picker("消息类型", selection: $selectedTab) {
            ForEach(tabTitles.indices, id: \.self) { tab in
                Text(tabTitles[tab]).tag(tab)
            }
        }
        .pickerStyle(.segmented)
        .overlay {
            HStack(spacing: 0) {
                ForEach(tabTitles.indices, id: \.self) { tab in
                    Text(tabTitles[tab])
                        .font(.system(size: 13))
                        .hidden()
                        .overlay(alignment: .topTrailing) {
                            if tabsWithUnread.contains(tab) {
                                UnreadBadge().offset(x: 8, y: -4)
                            }
                        }
                        .frame(maxWidth: .infinity)
                }
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .accessibilityValue(tabTitles.indices.filter { tabsWithUnread.contains($0) }
            .map { "\(tabTitles[$0])有未读消息" }.joined(separator: "，"))
        .frame(maxWidth: 320)
        .frame(minHeight: 44)
        .padding(.horizontal, TiebaPureTheme.Spacing.md)
        .padding(.vertical, TiebaPureTheme.Spacing.xs)
        .accessibilityIdentifier("messages-kind-picker")
    }

    @ViewBuilder
    private var content: some View {
        if account == nil {
            ReaderStateView(
                kind: .empty,
                title: "登录后查看消息",
                message: "登录后可以查看回复、@我的和私信消息。",
                systemImage: "person.crop.circle.badge.exclamationmark"
            )
            .accessibilityIdentifier("messages-login-prompt")
        } else if selectedTab == 2, let account {
            PrivateMessagesView(account: account) { hasUnread in
                unread.updatePrivate(hasUnread, for: account)
            }
            .id(account.sessionIdentity)
        } else if isLoading, didLoad == false {
            ReaderStateView.loading("正在加载消息")
        } else if let errorMessage, items.isEmpty {
            ReaderStateScrollView(refresh: { await reload() }) {
                ReaderStateView.error(message: errorMessage) {
                    Task { await reload() }
                }
            }
        } else if items.isEmpty {
            ReaderStateScrollView(refresh: { await reload() }) {
                ReaderStateView.empty(
                    title: emptyTitle,
                    message: emptyMessage,
                    actionTitle: hasMore ? "继续加载" : nil,
                    action: hasMore ? { Task { await loadMore() } } : nil
                )
            }
        } else {
            messageList
        }
    }

    private var messageList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, message in
                    ReaderCard(action: { openMessage(message) }) {
                        MessageRow(message: message)
                    }
                    .accessibilityHint("打开该帖子并定位到相关楼层")
                    .accessibilityIdentifier("message-row-\(message.id)")
                    .onAppear {
                        guard PaginationPrefetchPolicy.shouldLoadMore(
                            currentIndex: index,
                            totalCount: items.count
                        ) else { return }
                        Task { await loadMore() }
                    }
                }

                if isLoading, didLoad {
                    ProgressView()
                        .padding(TiebaPureTheme.Spacing.md)
                        .accessibilityLabel("正在加载更多消息")
                }

                if let errorMessage {
                    InlineLoadErrorView(message: errorMessage) {
                        Task { await loadMore() }
                    }
                } else if hasMore, isLoading == false, didLoad {
                    Button {
                        Task { await loadMore() }
                    } label: {
                        Label("加载更多消息", systemImage: "arrow.down.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .minTouchTarget()
                    .padding(.horizontal, TiebaPureTheme.Spacing.md)
                    .accessibilityIdentifier("messages-load-more")
                } else if hasMore == false {
                    Text("已显示全部消息")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding(TiebaPureTheme.Spacing.md)
                        .frame(maxWidth: .infinity, alignment: .center)
                        .accessibilityLabel("已显示全部消息")
                }

                Color.clear
                    .frame(height: 32)
                    .accessibilityHidden(true)
            }
            .readableWidth()
        }
        .background(TiebaPureTheme.ColorToken.readerGroupedBackground)
        .accessibilityIdentifier("messages-list")
        .shortPullRefresh(
            isEnabled: didLoad && isLoading == false,
            surface: .grouped,
            accessibilityIdentifier: "messages-refresh-animation"
        ) {
            await reload()
        }
    }

    private func openMessage(_ message: MessageItem) {
        let route = ReaderSplitThreadRoute(
            threadID: message.threadID,
            forumID: nil,
            initialPostID: message.postID
        )
        if let openThreadInParent {
            navigationSourceLifecycle.beginParentNavigation()
            openThreadInParent(route)
        } else {
            activeMessage = message
        }
    }

    private var emptyTitle: String {
        kind == .reply ? "还没有收到回复" : "还没有人@我"
    }

    private var emptyMessage: String {
        kind == .reply
            ? "别人回复你的帖子或楼层后会显示在这里。"
            : "别人在帖子里@你后会显示在这里。"
    }

    private var messageIsActive: Binding<Bool> {
        Binding(
            get: { activeMessage != nil },
            set: { isActive in
                if isActive == false {
                    activeMessage = nil
                }
            }
        )
    }

    private func resetForNewRequestScope() {
        loadTask?.cancel()
        loadTask = nil
        requestGeneration += 1
        items = []
        nextPage = 1
        hasMore = true
        isLoading = false
        didLoad = false
        errorMessage = nil
    }

    private func reload() async {
        guard account != nil, selectedTab != 2 else { return }
        loadTask?.cancel()
        requestGeneration += 1
        isLoading = false
        nextPage = 1
        hasMore = true
        errorMessage = nil
        await loadMore(generation: requestGeneration, replacing: true)
    }

    private func loadMore() async {
        await loadMore(generation: requestGeneration, replacing: false)
    }

    private func loadMore(
        generation: Int,
        replacing: Bool,
        consecutiveHiddenPageCount: Int = 0
    ) async {
        guard let account, selectedTab != 2 else { return }
        guard isLoading == false, hasMore || replacing else { return }
        let requestedSession = account.sessionIdentity
        let requestedKind = kind
        let requestedPage = replacing ? 1 : nextPage
        isLoading = true
        errorMessage = nil
        var continuation: LocallyFilteredPaginationDecision?

        do {
            let task = Task {
                try await environment.api.messages(
                    account: account,
                    kind: requestedKind,
                    page: requestedPage
                )
            }
            loadTask = task
            let page = try await task.value
            guard generation == requestGeneration, !Task.isCancelled,
                  requestedSession == self.account?.sessionIdentity else { return }
            if replacing {
                unread.didView(page, kind: requestedKind, account: account)
            }
            let visibleItems = page.items.filter(TiebaContentFilter.shouldKeep(message:))
            if replacing {
                items = deduplicated(visibleItems)
            } else {
                items = deduplicated(items + visibleItems)
            }
            // Pagination is deliberately derived from the unfiltered service
            // page, so a locally muted page never becomes a false end-of-feed.
            let pagination = MessagePaginationPolicy.state(
                after: page,
                requestedPage: requestedPage
            )
            hasMore = pagination.hasMore
            nextPage = pagination.nextPage
            continuation = LocallyFilteredPaginationPolicy.decision(
                visibleItemCount: visibleItems.count,
                serverHasMore: hasMore,
                consecutiveHiddenPageCount: consecutiveHiddenPageCount
            )
        } catch is CancellationError {
            guard generation == requestGeneration,
                  requestedSession == self.account?.sessionIdentity else { return }
            loadTask = nil
            isLoading = false
            return
        } catch {
            guard generation == requestGeneration,
                  requestedSession == self.account?.sessionIdentity else { return }
            errorMessage = ReaderErrorMessage.message(for: error)
        }

        guard generation == requestGeneration,
              requestedSession == self.account?.sessionIdentity else { return }
        loadTask = nil
        isLoading = false
        didLoad = true
        if let continuation, continuation.shouldAutomaticallyLoadNextPage {
            await loadMore(
                generation: generation,
                replacing: false,
                consecutiveHiddenPageCount: continuation.consecutiveHiddenPageCount
            )
        }
    }

    private func deduplicated(_ candidates: [MessageItem]) -> [MessageItem] {
        var seen = Set<String>()
        return candidates.filter { seen.insert($0.id).inserted }
    }
}

struct UnreadBadge: View {
    var count: Int? = nil

    var body: some View {
        if let count {
            Canvas { context, size in
                let line = CTLineCreateWithAttributedString(NSAttributedString(
                    string: count > 99 ? "99+" : String(count),
                    attributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: 10, weight: .bold),
                                 .foregroundColor: UIColor.white]
                ))
                let glyph = CTLineGetBoundsWithOptions(line, .useGlyphPathBounds)
                context.withCGContext { graphics in
                    graphics.translateBy(x: size.width / 2 - glyph.midX, y: size.height / 2 + glyph.midY)
                    graphics.scaleBy(x: 1, y: -1)
                    graphics.textPosition = .zero
                    CTLineDraw(line, graphics)
                }
            }
            .frame(width: 21, height: 21)
            .background(Color(uiColor: .systemRed), in: Circle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(count) 条未读私信")
        } else {
            Circle()
                .fill(Color(uiColor: .systemRed))
                .frame(width: 8, height: 8)
                .accessibilityHidden(true)
        }
    }
}

@MainActor
final class MessageUnreadState: ObservableObject {
    @Published private var unreadTabs: Set<Int> = []
    private var session: AccountSessionIdentity?
    private var viewedHeads: [MessageKind: String] = [:]
    private var otherCounts: [MessageKind: Int] = [:]
    private let defaults: UserDefaults?

    init(defaults: UserDefaults? = .standard) {
        self.defaults = defaults
    }

    func tabs(for account: Account?) -> Set<Int> {
        account?.sessionIdentity == session ? unreadTabs : []
    }

    func refreshWhileVisible(account: Account, environment: AppEnvironment, viewing tab: Int? = nil) async {
        guard !Task.isCancelled else { return }
        if session != account.sessionIdentity {
            session = account.sessionIdentity
            viewedHeads = [:]
            otherCounts = [:]
            unreadTabs = []
        }

        while !Task.isCancelled, session == account.sessionIdentity {
            async let feeds: Void = refreshFeeds(viewing: tab, account: account, api: environment.api)
            async let privateMessages: Void = refreshPrivate(unlessViewing: tab, account: account, client: environment.privateMessageClient)
            _ = await (feeds, privateMessages)
            do {
                try await Task.sleep(for: .seconds(30))
            } catch {
                return
            }
        }
    }

    func didView(_ page: MessagesPage, kind: MessageKind, account: Account) {
        guard session == account.sessionIdentity else { return }
        otherCounts[kind == .reply ? .at : .reply] = page.otherUnreadCount
        let head = page.items.first?.id ?? ""
        if viewedHeads[kind] != head {
            viewedHeads[kind] = head
            defaults?.set(head, forKey: viewedKey(kind, account: account))
        }
        setUnread(false, tab: kind == .reply ? 0 : 1)
    }

    func updatePrivate(_ hasUnread: Bool, for account: Account) {
        guard session == account.sessionIdentity, !Task.isCancelled else { return }
        setUnread(hasUnread, tab: 2)
    }

    private func refreshFeeds(viewing selected: Int?, account: Account, api: any TiebaAPIService) async {
        async let replyPage = fetchFeed(.reply, skip: selected == 0, account: account, api: api)
        async let atPage = fetchFeed(.at, skip: selected == 1, account: account, api: api)
        let (replies, mentions) = await (replyPage, atPage)
        guard !Task.isCancelled, session == account.sessionIdentity else { return }

        if let replies {
            updateFeed(replies, kind: .reply, tab: 0, otherCount: mentions?.otherUnreadCount, account: account)
        }
        if let mentions {
            updateFeed(mentions, kind: .at, tab: 1, otherCount: replies?.otherUnreadCount, account: account)
        }
    }

    private func fetchFeed(_ kind: MessageKind, skip: Bool, account: Account, api: any TiebaAPIService) async -> MessagesPage? {
        guard !skip else { return nil }
        return try? await api.messages(account: account, kind: kind, page: 1)
    }

    private func updateFeed(_ page: MessagesPage, kind: MessageKind, tab: Int, otherCount: Int?, account: Account) {
        let viewed = viewedHeads[kind] ?? defaults?.string(forKey: viewedKey(kind, account: account))
        setUnread(Self.hasUnread(page, since: viewed, otherCount: otherCount ?? otherCounts[kind]), tab: tab)
    }

    static func hasUnread(_ page: MessagesPage, since viewed: String?, otherCount: Int? = nil) -> Bool {
        guard let head = page.items.first, head.id != viewed else { return false }
        if (page.unreadCount ?? otherCount ?? 0) > 0 { return true }
        let newer = page.items.prefix { $0.id != viewed }
        if newer.contains(where: { $0.isUnread == true }) { return true }
        // shortcut: Older feeds can omit unread fields. Compare first-page IDs until they expose read state.
        return viewed != nil && page.unreadCount == nil && newer.contains { $0.isUnread == nil }
    }

    private func refreshPrivate(unlessViewing selected: Int?, account: Account, client: PrivateMessageClient) async {
        if let cached = try? client.hasCachedUnread(account: account) {
            updatePrivate(cached, for: account)
        }
        guard selected != 2, !Task.isCancelled,
              let page = try? await client.conversations(account: account) else { return }
        updatePrivate(page.items.contains { $0.unread > 0 }, for: account)
    }

    private func setUnread(_ hasUnread: Bool, tab: Int) {
        guard unreadTabs.contains(tab) != hasUnread else { return }
        if hasUnread { unreadTabs.insert(tab) } else { unreadTabs.remove(tab) }
    }

    private func viewedKey(_ kind: MessageKind, account: Account) -> String {
        "message-viewed-head.\(account.uid).\(kind.rawValue)"
    }
}

private struct MessageRow: View {
    let message: MessageItem

    var body: some View {
        HStack(alignment: .top, spacing: TiebaPureTheme.Spacing.sm) {
            AvatarView(
                url: message.author.portraitURL,
                title: message.author.displayNameResolved,
                size: TiebaPureTheme.AvatarSize.medium
            )

            VStack(alignment: .leading, spacing: TiebaPureTheme.Spacing.xxs) {
                HStack(alignment: .firstTextBaseline, spacing: TiebaPureTheme.Spacing.xs) {
                    Text(message.author.displayNameResolved)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)

                    Text(kindDescription)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .layoutPriority(1)

                    Spacer(minLength: TiebaPureTheme.Spacing.xs)

                    if let createdAt = message.createdAt {
                        Text(ReaderDateText.string(from: createdAt))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .layoutPriority(1)
                    }
                }

                if message.content.isEmpty == false {
                    Text(message.content)
                        .font(.body)
                        .foregroundStyle(.primary)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                if sourceLine.isEmpty == false {
                    Text(sourceLine)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityText)
    }

    private var kindDescription: String {
        switch message.kind {
        case .reply:
            return message.isFloorReply ? "在楼中楼回复了我" : "回复了我"
        case .at:
            return "@了我"
        }
    }

    private var sourceLine: String {
        var parts: [String] = []
        let title = message.threadTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty == false {
            parts.append("原帖：\(title)")
        }
        if let forumName = message.forumName?.trimmingCharacters(in: .whitespacesAndNewlines),
           forumName.isEmpty == false {
            parts.append(forumName.hasSuffix("吧") ? forumName : "\(forumName)吧")
        }
        return parts.joined(separator: " · ")
    }

    private var accessibilityText: String {
        var parts = ["\(message.author.displayNameResolved)\(kindDescription)"]
        if message.content.isEmpty == false {
            parts.append(message.content)
        }
        if sourceLine.isEmpty == false {
            parts.append(sourceLine)
        }
        if let createdAt = message.createdAt {
            parts.append(ReaderDateText.string(from: createdAt))
        }
        return parts.joined(separator: "，")
    }
}
