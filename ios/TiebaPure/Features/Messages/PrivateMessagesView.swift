import SwiftUI
import PhotosUI

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
    @State private var sending = false
    @State private var uncertain = false
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
    @State private var photo: PhotosPickerItem?
    @State private var image: ContentSubmissionImage?
    @State private var preparing = false
    @State private var showsEmoticons = false
    @StateObject private var recorder = PrivateVoiceRecorder()

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
                Button { revision += 1 } label: { Image(systemName: "arrow.clockwise").frame(minWidth: 44, minHeight: 44) }.disabled(loading || sending || preparing || recorder.recording || recorder.requestingPermission).accessibilityLabel("刷新私信")
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
            guard (try? await environment.accountStore.load())?.sessionIdentity == account.sessionIdentity, !Task.isCancelled else { active = false; return }
            active = true; await reload()
        }
        .task {
            while !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 15_000_000_000) } catch { return }
                if active && scenePhase == .active && !loading && !sending && !preparing && !recorder.recording && !recorder.requestingPermission { revision += 1 }
            }
        }
        .onReceive(environment.accountStore.accountDidChange) { current in
            if current?.sessionIdentity != account.sessionIdentity { active = false; generation += 1; action?.cancel(); draft = ""; image = nil; recorder.discard(); messages = []; conversations = [] }
        }
        .onDisappear { active = false; generation += 1; action?.cancel(); recorder.discard(); preparing = false; sending = false; loading = false }
        .onChange(of: photo) { _ in preparePhoto() }
        .onChange(of: recorder.error) { value in if let value { error = value } }
        .onChange(of: scenePhase) { phase in if phase != .active { recorder.stop() } }
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
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 14) {
                        if hasMore { Button("加载更早消息") { loadMore() }.disabled(loading || sending || preparing || recorder.recording || recorder.requestingPermission) }
                        if messages.isEmpty && !loading && error == nil { Text("暂无消息").foregroundStyle(.secondary).padding(24) }
                        ForEach(messages) { message in
                            let mine = message.senderID == account.uid
                            HStack {
                                if mine { Spacer(minLength: 48) }
                                VStack(alignment: mine ? .trailing : .leading, spacing: 5) {
                                    HStack(alignment: .bottom, spacing: 6) {
                                        if mine && message.read { Text("已读").font(.caption2).foregroundStyle(.secondary) }
                                        messageBody(message).padding(12).background(mine ? Color.blue.opacity(0.12) : Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
                                    }
                                    if message.timestamp > 0 { Text(Date(timeIntervalSince1970: Double(message.timestamp)), format: .dateTime.month().day().hour().minute()).font(.caption2).foregroundStyle(.secondary) }
                                }
                                if !mine { Spacer(minLength: 48) }
                            }.id(message.id)
                        }
                    }.padding()
                }
                .onChange(of: messages.last?.id) { value in if let value, !loading { withAnimation { proxy.scrollTo(value, anchor: .bottom) } } }
            }
            VStack(spacing: 8) {
                if preparing { ProgressView("正在准备图片") }
                if let image {
                    HStack {
                        if let thumbnail = UIImage(data: image.data) { Image(uiImage: thumbnail).resizable().scaledToFit().frame(width: 56, height: 56) }
                        Text("图片将单独发送").font(.caption)
                        Spacer()
                        Button("移除") { self.image = nil; photo = nil }.disabled(sending)
                    }
                }
                if recorder.recording || recorder.attachment != nil {
                    HStack {
                        Text(recorder.recording ? "正在录音，最长60秒" : "语音 \(recorder.duration)秒").font(.caption)
                        Spacer()
                        Button("取消录音") { recorder.discard() }.disabled(sending)
                    }
                }
                HStack(alignment: .center, spacing: 4) {
                    Button {
                        if recorder.recording { recorder.stop() }
                        else {
                            let currentGeneration = generation
                            action = Task { @MainActor in
                                do {
                                    try await recorder.start()
                                    guard active, generation == currentGeneration, scenePhase == .active else { recorder.discard(); return }
                                } catch { if active { self.error = error.localizedDescription } }
                            }
                        }
                    } label: { Image(systemName: recorder.recording ? "stop.circle.fill" : "mic").font(.system(size: 21)).frame(width: 44, height: 44) }
                    .accessibilityLabel(recorder.recording ? "停止录音" : "录制语音").disabled(sending || preparing || image != nil || recorder.requestingPermission)
                    TextField("输入私信", text: $draft, axis: .vertical)
                        .lineLimit(1...5).textFieldStyle(.plain)
                        .padding(.horizontal, 12).padding(.vertical, 10).frame(minHeight: 44)
                        .background(Color(uiColor: .tertiarySystemFill), in: RoundedRectangle(cornerRadius: 20))
                        .accessibilityIdentifier("private-message-input")
                        .disabled(sending || recorder.recording)
                    Button { showsEmoticons.toggle() } label: { Image(systemName: "face.smiling").font(.system(size: 21)).frame(width: 44, height: 44) }.accessibilityLabel("贴吧表情").disabled(sending || recorder.recording)
                    PhotosPicker(selection: $photo, matching: .images) { Image(systemName: "photo").font(.system(size: 21)).frame(width: 44, height: 44) }.accessibilityLabel("选择私信图片").disabled(sending || preparing || recorder.recording || recorder.requestingPermission || recorder.attachment != nil)
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
                Button(sending ? "发送中" : image != nil ? "发送图片" : recorder.attachment != nil ? "发送语音" : "发送") { send(peer) }
                    .disabled(sending || uncertain || loading || preparing || recorder.recording || recorder.requestingPermission || (image == nil && recorder.attachment == nil && (draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || draft.count > 5_000)))
            }.padding(.horizontal).padding(.vertical, 8).background(.regularMaterial)
        }
    }
    @ViewBuilder private func messageBody(_ message: PrivateChatMessage) -> some View {
        if message.type == 2, let url = message.mediaURL {
            Button {
                let image = ImageContent(thumbnailURL: url, originalURL: url, width: 0, height: 0, showOriginalButton: false)
                ImagePreviewCoordinator.shared.present(ImagePreviewSession(images: [image], initialIndex: 0))
            } label: {
                TiebaRemoteImage(primaryURL: url, targetPixelSize: 660, contentMode: .fit, showsProgress: true).frame(width: 220, height: 220)
            }.accessibilityLabel("查看私信图片")
        } else if let md5 = message.voiceMD5, let voice = VoiceContent(md5: md5, durationMilliseconds: message.duration * 1_000) {
            VoicePlaybackControl(voice: voice)
        } else {
            InlineContentText(blocks: TiebaEmoticon.blocks(from: message.text), allowsTextSelection: true, maximumContentWidth: 280)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
    private func preparePhoto() {
        guard let photo else { return }
        let requestGeneration = generation
        preparing = true
        action = Task { @MainActor in
            defer { if generation == requestGeneration { preparing = false } }
            do {
                guard let data = try await photo.loadTransferable(type: Data.self) else { throw PrivateMessageError.invalidResponse }
                let decoded = try await Task.detached { try ContentComposerImageDecoder.decode(data) }.value
                try Task.checkCancellation()
                guard active, generation == requestGeneration else { return }
                image = decoded
            } catch { if active && generation == requestGeneration && !Task.isCancelled { self.error = error.localizedDescription } }
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
        loading = true; error = nil; hasMore = false; cursor = nil
        defer { if active && generation == requestGeneration && selected?.id == targetID && revision == requestRevision { loading = false } }
        do {
            if let peer = selected {
                let page = try await client.messages(peer, account: account)
                try Task.checkCancellation(); guard active, generation == requestGeneration, selected?.id == peer.id else { return }
                if let hasUnread = try? client.hasCachedUnread(account: account) {
                    onUnreadChange(hasUnread)
                }
                messages = page.items; hasMore = page.hasMore; cursor = page.cursor
                if uncertain { error = PrivateMessageError.unknownSend.localizedDescription }
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
    private func loadMore() {
        guard let before = cursor, !loading, !sending, !preparing, !recorder.recording, !recorder.requestingPermission else { return }
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
        action?.cancel(); generation += 1
        recorder.discard(); image = nil; photo = nil; preparing = false; showsEmoticons = false
        selected = peer; messages = []; draft = ""; uncertain = false; error = nil
        cursor = nil; hasMore = false; loading = false; sending = false
    }
    private func send(_ peer: PrivateConversation) {
        guard !sending, !uncertain, active else { return }
        sending = true; error = nil
        let text = draft, requestGeneration = generation
        let attachment = image, voice = recorder.attachment, duration = recorder.duration
        action = Task { @MainActor in
            defer { if active && generation == requestGeneration { sending = false } }
            do {
                guard try await environment.accountStore.load()?.sessionIdentity == account.sessionIdentity else { throw CancellationError() }
                try Task.checkCancellation(); guard active else { return }
                let key = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(10).lowercased()
                let payload: String, type: Int
                if let attachment {
                    let groupID = try await client.personalGroupID(account: account)
                    try Task.checkCancellation()
                    guard active, generation == requestGeneration, selected?.id == peer.id else { throw CancellationError() }
                    payload = try await PrivateMessageMediaUpload.image(attachment, account: account, groupID: groupID, clientID: client.cuid); type = 2
                } else if let voice {
                    payload = try await PrivateMessageMediaUpload.voice(voice, duration: duration, account: account, clientID: client.cuid); type = 3
                } else { payload = text; type = 1 }
                try Task.checkCancellation()
                guard active, generation == requestGeneration, selected?.id == peer.id,
                      try await environment.accountStore.load()?.sessionIdentity == account.sessionIdentity else { throw CancellationError() }
                _ = try await client.send(payload, to: peer, account: account, key: key, type: type)
                try Task.checkCancellation(); guard active, generation == requestGeneration, selected?.id == peer.id else { return }
                if type == 1 { draft = "" }
                else if type == 2 { image = nil; photo = nil }
                else { recorder.discard() }
                revision += 1
            } catch {
                if active && generation == requestGeneration && selected?.id == peer.id && !Task.isCancelled {
                    if case PrivateMessageError.unknownSend = error { uncertain = true } else { uncertain = false }
                    self.error = error.localizedDescription
                }
            }
        }
    }
}
