import SwiftUI

private struct PrivateMessageFramesKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}
private struct PrivateViewportHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

struct PrivateMessagesView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject private var environment: AppEnvironment
    let account: Account
    private var client: PrivateMessageClient { environment.privateMessageClient }
    private let onUnreadChange: (Bool) -> Void
    @State private var conversations: [PrivateConversation] = []
    @State private var messages: [PrivateChatMessage] = []
    @State private var selected: PrivateConversation?
    @State private var revision = 0
    @State private var generation = 0
    @State private var loading = false
    @State private var sendsInFlight = 0
    private var sending: Bool { sendsInFlight > 0 }
    @State private var error: String?
    @State private var draft = ""
    @State private var cursor: Int64?
    @State private var hasMore = false
    @State private var newMessage = false
    @State private var recipient = ""
    @State private var resolving = false
    @State private var recipientError: String?
    @State private var action: Task<Void, Never>?
    @State private var active = true
    @State private var showsEmoticons = false
    @State private var mediaAspects: [String: CGFloat] = [:]
    @State private var previousPosition: PrivateReadingPosition?
    @State private var currentAnchor: String?
    @State private var chatAtBottom = false
    @State private var chatInitialized = false
    @State private var displayedLastID: String?
    @State private var viewportHeight: CGFloat = 0
    @State private var showReadingJump = false
    @State private var readingPromptHandled = false
    @State private var userNavigated = false

    init(account: Account, recipient: UserSummary? = nil, onUnreadChange: @escaping (Bool) -> Void = { _ in }) {
        self.account = account
        self.onUnreadChange = onUnreadChange
        _selected = State(initialValue: recipient.flatMap { user in
            try? PrivateConversation.recipient(user, accountID: account.uid)
        })
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if selected != nil {
                    Button { switchConversation(nil) } label: { Label("会话列表", systemImage: "chevron.left") }
                    Spacer()
                    Text(selected?.name ?? "私信").lineLimit(1)
                } else {
                    Text("私信").foregroundStyle(.secondary)
                    Spacer()
                    Button("一键已读", action: markAllRead)
                        .disabled(loading || !conversations.contains { $0.unread > 0 })
                        .accessibilityIdentifier("private-mark-all-read")
                    Button { recipient = ""; recipientError = nil; newMessage = true } label: { Label("发消息", systemImage: "square.and.pencil") }
                }
                Button { revision += 1 } label: { Image(systemName: "arrow.clockwise").frame(minWidth: 44, minHeight: 44) }.disabled(loading || sending).accessibilityLabel("刷新私信")
            }.font(.subheadline).padding(.horizontal)
            if let error { Text(error).font(.footnote).foregroundStyle(.red).padding(.horizontal).padding(.vertical, 8) }
            if let selected {
                chat(selected)
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        if conversations.isEmpty && !loading && error == nil { Text("暂无私信，点击“发消息”开始会话").foregroundStyle(.secondary).padding(32) }
                        ForEach(conversations) { peer in
                            Button {
                                switchConversation(peer)
                            } label: {
                                HStack(spacing: 12) {
                                    AvatarView(url: TiebaURL.avatar(peer.portrait), title: peer.name, size: 44)
                                        .overlay(alignment: .topTrailing) {
                                            if peer.unread > 0 {
                                                UnreadBadge(count: peer.unread).offset(x: 5, y: -5)
                                            }
                                        }
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(peer.name).foregroundStyle(.primary)
                                        Text(peer.preview).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                                    }
                                    Spacer()
                                    if peer.timestamp > 0 {
                                        Text(ReaderDateText.privateConversationString(from: Date(timeIntervalSince1970: Double(peer.timestamp))))
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .fixedSize()
                                    }
                                }.padding().contentShape(Rectangle())
                            }.buttonStyle(.plain)
                            Divider().padding(.leading, 72)
                        }
                        if hasMore { Button("加载更多会话") { loadMore() }.disabled(loading) }
                    }
                }
            }
        }
        .accessibilityIdentifier("private-messages-screen")
        .preference(key: PrivateMessagesTabPreferenceKey.self, value: true)
        .task(id: "\(selected?.id ?? "list"):\(revision)") {
            let storedAccount = (try? await environment.accountStore.load()) ?? nil
            guard storedAccount?.uid == account.uid, !Task.isCancelled else { active = false; return }
            active = true; await reload()
        }
        .task {
            while !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 15_000_000_000) } catch { return }
                if active && scenePhase == .active && !loading && !sending { revision += 1 }
            }
        }
        .onReceive(environment.accountStore.accountDidChange) { current in
            if current?.uid != account.uid { active = false; generation += 1; action?.cancel(); draft = ""; messages = []; conversations = [] }
        }
        .onChange(of: scenePhase) { if $0 != .active { saveReadingPosition() } }
        .onDisappear { saveReadingPosition(); active = false; generation += 1; action?.cancel(); sendsInFlight = 0; loading = false }
        .sheet(isPresented: $newMessage, onDismiss: { action?.cancel(); resolving = false }) {
            NavigationStack {
                Form {
                    Section {
                        NavigationLink {
                            FollowedUsersView(account: account) { user in
                                do { try openRecipient(user) }
                                catch { recipientError = error.localizedDescription }
                            }
                        } label: {
                            Label("从关注的人中选择", systemImage: "person.2")
                        }
                        .disabled(resolving)
                        .accessibilityIdentifier("private-message-following-picker")
                    }
                    Section("输入收件人的贴吧 ID") {
                        TextField("用户 ID", text: $recipient).keyboardType(.numberPad).disabled(resolving)
                        Text("输入对方主页显示的数字 ID，也可从其主页点击“发私信”。打开会话不会发送消息。").font(.footnote).foregroundStyle(.secondary)
                    }
                    Button(resolving ? "正在查找…" : "打开会话") { resolveRecipient() }.disabled(resolving || recipient.isEmpty)
                }
                .navigationTitle("发起私信").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("取消") { newMessage = false } } }
            }
            .alert("无法发起私信", isPresented: Binding(get: { recipientError != nil }, set: { if !$0 { recipientError = nil } })) {
                Button("好", role: .cancel) { recipientError = nil }
            } message: { Text(recipientError ?? "") }
        }
    }
    private func chat(_ peer: PrivateConversation) -> some View {
        let ids = messages.map(\.id)
        let boundary = PrivateReadingPolicy.boundary(previousPosition, ids: ids)
        let jumpTarget = PrivateReadingPolicy.target(previousPosition, ids: ids)
        let needsHistory = PrivateReadingPolicy.needsHistory(previousPosition, ids: ids, hasMore: hasMore)
        return VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if hasMore { Button("加载更早消息") { loadMore() }.disabled(loading || sending) }
                        if messages.isEmpty && !loading && error == nil { Text("暂无消息").foregroundStyle(.secondary).padding(24) }
                        ForEach(messages) { message in
                            let mine = message.senderID == account.uid
                            let edgeGap: CGFloat = showsBubbleChrome(message) ? 48 : 0
                            if let boundary, messages[boundary].id == message.id {
                                Text("以下是新消息").font(.caption2).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.vertical, 4)
                            }
                            HStack(spacing: 0) {
                                if mine { Spacer(minLength: edgeGap) }
                                VStack(alignment: mine ? .trailing : .leading, spacing: 5) {
                                    HStack(alignment: .bottom, spacing: 6) {
                                        if mine && message.read { Text("已读").font(.caption2).foregroundStyle(.secondary) }
                                        bubbleBody(message, mine: mine)
                                    }
                                    if message.timestamp > 0 { Text(Date(timeIntervalSince1970: Double(message.timestamp)), format: .dateTime.month().day().hour().minute()).font(.caption2).foregroundStyle(.secondary) }
                                }
                                if !mine { Spacer(minLength: edgeGap) }
                            }.background(GeometryReader { geometry in
                                Color.clear.preference(key: PrivateMessageFramesKey.self, value: [message.id: geometry.frame(in: .named("private-chat-scroll"))])
                            }).id(message.id)
                        }
                        Color.clear.frame(height: 1).id("private-chat-bottom")
                    }.padding().frame(minHeight: viewportHeight, alignment: .bottom)
                }
                .coordinateSpace(name: "private-chat-scroll")
                .modifier(BottomAnchoredScroll())
                .opacity(chatInitialized || messages.isEmpty ? 1 : 0)
                .background(GeometryReader { geometry in Color.clear.preference(key: PrivateViewportHeightKey.self, value: geometry.size.height) })
                .onPreferenceChange(PrivateViewportHeightKey.self) { viewportHeight = $0; updateChatScroll(proxy) }
                .onPreferenceChange(PrivateMessageFramesKey.self) { frames in
                    guard chatInitialized, viewportHeight > 0 else { return }
                    if let anchor = messages.first(where: { message in
                        guard let frame = frames[message.id] else { return false }
                        return frame.maxY > 0 && frame.minY < viewportHeight
                    })?.id { currentAnchor = anchor }
                    if let last = messages.last, let frame = frames[last.id] {
                        chatAtBottom = frame.maxY > 0 && frame.maxY <= viewportHeight + 2
                    } else { chatAtBottom = false }
                    if !userNavigated && !chatAtBottom && !loading { positionChatAtBottom(proxy) }
                }
                .overlay(alignment: .bottom) {
                    if showReadingJump, let jumpTarget {
                        Button(previousPosition?.atBottom == true ? "跳转到上次未读" : "回到上次阅读位置") {
                            userNavigated = true
                            withAnimation { proxy.scrollTo(jumpTarget, anchor: .top) }
                            showReadingJump = false
                        }
                        .buttonStyle(.borderedProminent).padding(.bottom, 12)
                        .accessibilityIdentifier("private-reading-jump")
                    }
                }
                .onAppear {
                    previousPosition = UserDefaults.standard.data(forKey: readingKey(peer)).flatMap { try? JSONDecoder().decode(PrivateReadingPosition.self, from: $0) }
                    updateChatScroll(proxy)
                }
                .onChange(of: messages.last?.id) { _ in updateChatScroll(proxy) }
                .onChange(of: loading) { _ in updateChatScroll(proxy) }
                .simultaneousGesture(DragGesture(minimumDistance: 4).onChanged { _ in userNavigated = true })
                .task(id: "\(peer.id):\(chatInitialized):\(needsHistory):\(loading):\(messages.first?.id ?? "")") {
                    if chatInitialized && needsHistory && !loading { loadMore() }
                }
                .task(id: "\(peer.id):\(chatInitialized):\(needsHistory):\(jumpTarget ?? "")") {
                    guard chatInitialized, !needsHistory, jumpTarget != nil, !readingPromptHandled else { return }
                    readingPromptHandled = true; showReadingJump = true
                }
                .task(id: showReadingJump) {
                    guard showReadingJump else { return }
                    do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                    showReadingJump = false
                }
            }
            VStack(spacing: 8) {
                HStack(alignment: .center, spacing: 4) {
                    TextField("输入私信", text: $draft, axis: .vertical)
                        .lineLimit(1...5).textFieldStyle(.plain)
                        .padding(.horizontal, 12).padding(.vertical, 10).frame(minHeight: 44)
                        .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 20))
                        .accessibilityIdentifier("private-message-input")
                    Button { showsEmoticons.toggle() } label: { Image(systemName: "face.smiling").font(.system(size: 21)).frame(width: 44, height: 44) }.accessibilityLabel("贴吧表情")
                    Button("发送") { send(peer) }
                        .frame(minWidth: 44, minHeight: 44)
                        .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || draft.count > 5_000)
                        .accessibilityLabel("发送私信")
                }
                if showsEmoticons {
                    ScrollView(.horizontal) {
                        HStack {
                            ForEach(ContentComposerEmoticonCatalog.entries) { entry in
                                Button { draft += entry.token } label: {
                                    if let image = TiebaEmoticon.cachedImage(for: entry.imageName) { Image(uiImage: image).resizable().scaledToFit().frame(width: 28, height: 28).padding(8) }
                                    else { Text(entry.label).padding(8) }
                                }.accessibilityLabel(entry.label)
                            }
                        }
                    }.frame(height: 48)
                }
            }.padding(.horizontal).padding(.vertical, 8).background(.regularMaterial)
        }
    }
    private func readingKey(_ peer: PrivateConversation) -> String { "private-reading-position.\(account.uid).\(peer.userID)" }
    private func saveReadingPosition() {
        guard chatInitialized, let peer = selected, let anchor = currentAnchor, let latest = messages.last?.id else { return }
        let position = PrivateReadingPosition(anchor: anchor, latest: latest, atBottom: chatAtBottom)
        if let data = try? JSONEncoder().encode(position) { UserDefaults.standard.set(data, forKey: readingKey(peer)) }
    }
    private func updateChatScroll(_ proxy: ScrollViewProxy) {
        guard let last = messages.last else { return }
        if !chatInitialized {
            displayedLastID = last.id
            var transaction = Transaction(animation: nil)
            transaction.disablesAnimations = true
            withTransaction(transaction) { proxy.scrollTo("private-chat-bottom", anchor: .bottom) }
            chatInitialized = true
            positionChatAtBottom(proxy)
            return
        }
        if displayedLastID != last.id {
            let shouldFollow = chatAtBottom || last.senderID == account.uid
            displayedLastID = last.id
            if shouldFollow { DispatchQueue.main.async { withAnimation { proxy.scrollTo("private-chat-bottom", anchor: .bottom) } } }
        } else if !userNavigated {
            positionChatAtBottom(proxy)
        }
    }
    private func positionChatAtBottom(_ proxy: ScrollViewProxy) {
        let peerID = selected?.id
        let requestGeneration = generation
        for delay in [0.0, 0.1, 0.3] {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                guard active, selected?.id == peerID, generation == requestGeneration, !userNavigated else { return }
                var transaction = Transaction(animation: nil); transaction.disablesAnimations = true
                withTransaction(transaction) { proxy.scrollTo("private-chat-bottom", anchor: .bottom) }
            }
        }
    }
    @ViewBuilder private func bubbleBody(_ message: PrivateChatMessage, mine: Bool) -> some View {
        if showsBubbleChrome(message) {
            messageBody(message).padding(12).background(mine ? Color.blue.opacity(0.12) : Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        } else {
            messageBody(message)
        }
    }
    private func showsBubbleChrome(_ message: PrivateChatMessage) -> Bool {
        if message.type == 2, message.mediaURL != nil { return false }
        if message.voiceMD5 != nil { return false }
        return true
    }
    private func mediaSize(_ aspect: CGFloat?) -> CGSize {
        guard let aspect, aspect > 0, aspect.isFinite else { return CGSize(width: 220, height: 220) }
        let maximumWidth: CGFloat = 220, maximumHeight: CGFloat = 280
        var width = maximumWidth
        var height = maximumWidth / aspect
        if height > maximumHeight { height = maximumHeight; width = height * aspect }
        return CGSize(width: width, height: height)
    }
    @ViewBuilder private func messageBody(_ message: PrivateChatMessage) -> some View {
        if message.type == 2, let url = message.mediaURL {
            let key = url.absoluteString
            let size = mediaSize(mediaAspects[key])
            Button {
                let image = ImageContent(thumbnailURL: url, originalURL: url, width: 0, height: 0, showOriginalButton: false)
                ImagePreviewCoordinator.shared.present(ImagePreviewSession(images: [image], initialIndex: 0))
            } label: {
                TiebaRemoteImage(
                    primaryURL: url,
                    targetPixelSize: 660,
                    contentMode: .fit,
                    showsProgress: true,
                    onImageResolved: { image in
                        guard image.size.height > 0 else { return }
                        let aspect = image.size.width / image.size.height
                        Task { @MainActor in if mediaAspects[key] != aspect { mediaAspects[key] = aspect } }
                    }
                ).frame(width: size.width, height: size.height)
            }.accessibilityLabel("查看私信图片")
        } else if let md5 = message.voiceMD5, let voice = VoiceContent(md5: md5, durationMilliseconds: message.duration * 1_000) {
            VoicePlaybackControl(voice: voice)
        } else {
            InlineContentText(blocks: TiebaEmoticon.blocks(from: message.text), allowsTextSelection: true, maximumContentWidth: 280)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
    private func markAllRead() {
        guard active, selected == nil, !loading else { return }
        do {
            try client.markAllRead(account: account)
            for index in conversations.indices { conversations[index].unread = 0 }
            onUnreadChange(false)
        } catch {
            self.error = error.localizedDescription
        }
    }
    @MainActor private func reload() async {
        guard active else { return }
        let targetID = selected?.id, requestRevision = revision, requestGeneration = generation
        if let peer = selected, let page = try? client.cachedMessages(peer, account: account) {
            applyChatPage(page)
        } else if selected == nil, let page = try? client.cachedConversations(account: account) {
            conversations = page.items; hasMore = page.hasMore; cursor = page.cursor
        }
        loading = true; error = nil
        defer { if active && generation == requestGeneration && selected?.id == targetID && revision == requestRevision { loading = false } }
        do {
            if let peer = selected {
                let page = try await client.messages(peer, account: account)
                try Task.checkCancellation(); guard active, generation == requestGeneration, selected?.id == peer.id else { return }
                if let hasUnread = try? client.hasCachedUnread(account: account) {
                    onUnreadChange(hasUnread)
                }
                applyChatPage(page)
            } else {
                let page = try await client.conversations(account: account)
                try Task.checkCancellation(); guard active, generation == requestGeneration, selected == nil else { return }
                onUnreadChange(page.items.contains { $0.unread > 0 })
                conversations = page.items; hasMore = page.hasMore; cursor = page.cursor; loading = false
                for peer in page.items {
                    try Task.checkCancellation()
                    guard let id = Int64(peer.userID) else { continue }
                    let user = UserSummary(id: id, name: "", displayName: "", portrait: "")
                    if let profile = try? await environment.api.userProfile(account: account, user: user), active, !Task.isCancelled,
                       let index = conversations.firstIndex(where: { $0.id == peer.id }) {
                        conversations[index].name = profile.user.displayNameResolved; conversations[index].portrait = profile.user.portrait
                    }
                }
            }
        } catch { if active && generation == requestGeneration && !Task.isCancelled { self.error = error.localizedDescription } }
    }
    private func applyChatPage(_ page: PrivateMessagePage<PrivateChatMessage>) {
        let ids = Set(page.items.map(\.id))
        let older = Array(messages.prefix { !ids.contains($0.id) })
        messages = older + page.items
        if older.isEmpty { hasMore = page.hasMore; cursor = page.cursor }
    }
    private func loadMore() {
        guard let before = cursor, !loading, !sending else { return }
        loading = true
        let requestGeneration = generation, target = selected
        action = Task { @MainActor in
            defer { if active && generation == requestGeneration { loading = false } }
            do {
                if let peer = target {
                    let page = try await client.messages(peer, account: account, before: before)
                    try Task.checkCancellation(); guard active, generation == requestGeneration else { return }
                    let fresh = page.items.filter { item in !messages.contains { $0.id == item.id } }
                    messages.insert(contentsOf: fresh, at: 0); hasMore = page.hasMore && !fresh.isEmpty; cursor = page.cursor
                } else {
                    let page = try await client.conversations(account: account, before: before)
                    try Task.checkCancellation(); guard active, generation == requestGeneration else { return }
                    let fresh = page.items.filter { item in !conversations.contains { $0.id == item.id } }
                    conversations += fresh; hasMore = page.hasMore && !fresh.isEmpty; cursor = page.cursor
                }
            } catch { if active && generation == requestGeneration && !Task.isCancelled { self.error = error.localizedDescription } }
        }
    }
    private func resolveRecipient() {
        resolving = true; recipientError = nil
        let id = recipient.trimmingCharacters(in: .whitespacesAndNewlines)
        action = Task { @MainActor in
            defer { resolving = false }
            do {
                let user = try await environment.api.userByTiebaID(id, account: account)
                try Task.checkCancellation(); guard active else { return }
                try openRecipient(user)
            } catch { if active && !Task.isCancelled { recipientError = error.localizedDescription } }
        }
    }
    private func openRecipient(_ user: UserSummary) throws {
        guard active else { throw CancellationError() }
        switchConversation(try PrivateConversation.recipient(user, accountID: account.uid))
        newMessage = false
    }
    private func switchConversation(_ peer: PrivateConversation?) {
        saveReadingPosition()
        action?.cancel(); generation += 1
        showsEmoticons = false
        previousPosition = nil; currentAnchor = nil; chatAtBottom = false; chatInitialized = false
        displayedLastID = nil; showReadingJump = false; readingPromptHandled = false; viewportHeight = 0; userNavigated = false
        selected = peer; messages = []; draft = ""; error = nil
        cursor = nil; hasMore = false; loading = false; sendsInFlight = 0
    }
    private func send(_ peer: PrivateConversation) {
        guard active, !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        sendsInFlight += 1; error = nil
        let text = draft, requestGeneration = generation
        draft = ""
        Task { @MainActor in
            defer { if active && generation == requestGeneration { sendsInFlight = max(0, sendsInFlight - 1) } }
            do {
                let stored = try await environment.accountStore.load()
                guard let stored, stored.uid == account.uid else { throw PrivateMessageError.invalidUser }

                try Task.checkCancellation(); guard active else { return }
                let key = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(10).lowercased()
                _ = try await client.send(text, to: peer, account: stored, key: key)
                try Task.checkCancellation(); guard active, generation == requestGeneration, selected?.id == peer.id else { return }
                if let page = try? client.cachedMessages(peer, account: account) { messages = page.items }
            } catch {
                if active && generation == requestGeneration && selected?.id == peer.id && !Task.isCancelled {
                    if draft.isEmpty { draft = text }
                    self.error = error.localizedDescription
                }
            }
        }
    }

}

private struct BottomAnchoredScroll: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 17.0, *) { content.defaultScrollAnchor(.bottom) } else { content }
    }
}
