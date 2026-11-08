import SwiftUI

struct AutoReplySettingsView: View {
    @EnvironmentObject private var environment: AppEnvironment
    let account: Account
    @ObservedObject var coordinator: AutoReplyCoordinator
    @ObservedObject private var store: AutoReplyStore
    @State private var forums: [Forum] = []
    @State private var editing: AutoReplyRule?
    @State private var editorOpen = false
    @State private var errorMessage: String?
    @State private var selecting = false
    @State private var selected = Set<Int64>()
    @State private var deleting: Set<Int64>?
    init(account: Account, coordinator: AutoReplyCoordinator) {
        self.account = account; self.coordinator = coordinator
        _store = ObservedObject(wrappedValue: coordinator.store)
    }
    var body: some View {
        Form {
            Section {
                Toggle("启用自动回复", isOn: Binding(get: { store.enabled(account.id) }, set: coordinator.configureEnabled))
                Toggle("允许后台刷新", isOn: Binding(get: { store.background(account.id) }, set: coordinator.configureBackground))
                Text(coordinator.status).font(.footnote).foregroundStyle(.secondary)
            } footer: {
                Text("前台每60秒检查；后台仅在系统允许的短暂运行与刷新时段检查，无法持续实时保活。正常打开界面8秒后补扫，停线期间的匹配任务须确认才发送。首次添加只建立基线，不回复历史帖子。规则、缓存和任务历史按账号隔离，仅保存在本机。")
            }
            if let errorMessage { Section { Text(errorMessage).foregroundStyle(.red) } }
            Section {
                ForEach(store.state.rules) { rule in ruleRow(rule) }
                Button("添加已关注的吧") { editing = nil; editorOpen = true }.disabled(forums.isEmpty || !store.loaded)
                if forums.isEmpty { Text("暂无可选择的已关注贴吧，或列表尚未加载。").font(.footnote).foregroundStyle(.secondary) }
            } header: { Text("贴吧规则") }
            Section {
                if store.state.jobs.isEmpty { Text("命中关键词后的待确认、延迟任务和最终结果都会记录在这里。").foregroundStyle(.secondary) }
                ForEach(store.state.jobs.values.sorted { $0.discoveredAt > $1.discoveredAt }) { job in
                    HStack(alignment: .top, spacing: 8) {
                        if selecting {
                            Button { if !selected.insert(job.id).inserted { selected.remove(job.id) } } label: {
                                Image(systemName: selected.contains(job.id) ? "checkmark.circle.fill" : "circle").font(.title3).frame(minWidth: 44, minHeight: 44)
                            }
                            .buttonStyle(.borderless)
                            .accessibilityLabel("选择" + job.title)
                            .accessibilityValue(selected.contains(job.id) ? "已选择" : "未选择")
                        }
                        jobRow(job)
                    }
                }
            } header: {
                HStack {
                    Text("历史任务 · \(store.state.jobs.count)")
                    Spacer(minLength: 8)
                    Button(selecting ? "取消" : "选择") { selecting.toggle(); selected = [] }.disabled(store.state.jobs.isEmpty)
                    Button(selecting ? "删除(\(selected.count))" : "一键删除", role: .destructive) {
                        deleting = selecting ? selected : Set(store.state.jobs.keys)
                    }.disabled(selecting ? selected.isEmpty : store.state.jobs.isEmpty)
                }.textCase(nil).buttonStyle(.borderless)
            }
        }
        .navigationTitle("自动回复")
        .onChange(of: Set(store.state.jobs.keys)) { ids in selected.formIntersection(ids); if ids.isEmpty { selecting = false } }
        .alert("删除历史任务？", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), presenting: deleting) { ids in
            Button("取消", role: .cancel) { deleting = nil }
            Button("删除", role: .destructive) {
                do { try store.deleteJobs(account.id, ids: ids); selected = []; selecting = false }
                catch { errorMessage = error.localizedDescription }
                deleting = nil
            }
        } message: { ids in Text("将删除\(ids.count)条本机记录并取消尚未开始发送的任务，不会删除贴吧评论。正在发送的请求无法撤回。") }
        .task(id: account.sessionIdentity) {
            do {
                try await coordinator.loadForSettings(account)
                let loaded = try await environment.api.followedForums(account: account)
                guard !Task.isCancelled else { return }
                forums = loaded
            } catch is CancellationError { }
            catch { errorMessage = error.localizedDescription }
        }
        .sheet(isPresented: $editorOpen) {
            AutoReplyRuleEditor(forums: forums, original: editing) { rule in
                guard forums.contains(where: { $0.id == rule.forum.id }) else { throw AutoReplyError.invalidRule }
                try store.saveRule(account.id, rule: rule); coordinator.changed()
            }
        }
    }
    private func ruleRow(_ rule: AutoReplyRule) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(rule.forum.displayName).font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                Menu {
                    Button { editing = rule; editorOpen = true } label: { Label("编辑", systemImage: "pencil") }
                    Button(role: .destructive) { do { try store.removeRule(account.id, id: rule.id); coordinator.changed() } catch { errorMessage = error.localizedDescription } } label: { Label("移除", systemImage: "trash") }
                } label: { Image(systemName: "ellipsis").frame(minWidth: 44, minHeight: 44) }
                .accessibilityLabel(rule.forum.displayName + "规则操作")
                Toggle("启用" + rule.forum.displayName + "自动回复", isOn: Binding(get: { rule.enabled }, set: { value in
                    do { var updated = rule; updated.enabled = value; updated.revision = UUID().uuidString; try store.saveRule(account.id, rule: updated); coordinator.changed() }
                    catch { errorMessage = error.localizedDescription }
                })).labelsHidden()
            }.buttonStyle(.borderless)
            Text(rule.keywords.joined(separator: "、") + " · 延迟\(rule.delaySeconds)秒").font(.caption).foregroundStyle(.secondary)
            InlineContentText(blocks: [.text(rule.message)], style: .preview, lineLimit: 3)
        }.padding(.vertical, 6)
    }
    private func jobRow(_ job: AutoReplyJob) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack { Text(job.forum.displayName); Spacer(); Text(job.status.title) }.font(.caption).foregroundStyle(.secondary)
            Text(job.title).font(.headline)
            Text("命中：" + job.keywords.joined(separator: "、")).font(.caption)
            Text("发现：" + job.discoveredAt.formatted(date: .numeric, time: .standard)).font(.caption).foregroundStyle(.secondary)
            if job.status == .queued { Text("计划发送：" + job.dueAt.formatted(date: .omitted, time: .standard)).font(.caption) }
            InlineContentText(blocks: [.text(job.message)], style: .reply, lineLimit: job.status == .review ? Int.max : 4)
            if !job.note.isEmpty { Text(job.note).font(.footnote).foregroundStyle(.secondary) }
            if !selecting { HStack {
                NavigationLink("打开帖子") { ThreadDetailView(account: account, threadID: job.id, forumID: job.forum.id, initialPostID: job.postID) }
                if job.status == .review {
                    Button("同意回复") { review(job, approve: true) }.disabled(!store.enabled(account.id))
                    Button("忽略") { review(job, approve: false) }
                }
            }.buttonStyle(.borderless) }
        }.padding(.vertical, 6)
    }
    private func review(_ job: AutoReplyJob, approve: Bool) {
        do { try coordinator.engine.review(account.id, id: job.id, approve: approve) }
        catch { errorMessage = error.localizedDescription }
    }
}

private struct AutoReplyRuleEditor: View {
    @Environment(\.dismiss) private var dismiss
    let forums: [Forum]
    let original: AutoReplyRule?
    let save: (AutoReplyRule) throws -> Void
    @State private var selectedID: Int64
    @State private var keywords: String
    @State private var message: String
    @State private var delaySeconds: Int
    @State private var errorMessage: String?
    @State private var selection = NSRange(location: 0, length: 0)
    @State private var bodyFocused = false
    @State private var showsEmoticons = false
    init(forums: [Forum], original: AutoReplyRule?, save: @escaping (AutoReplyRule) throws -> Void) {
        self.forums = forums; self.original = original; self.save = save
        _selectedID = State(initialValue: forums.first(where: { $0.id == original?.forum.id })?.id ?? forums.first?.id ?? 0)
        _keywords = State(initialValue: original?.keywords.joined(separator: "\n") ?? "")
        _message = State(initialValue: original?.message ?? "")
        _delaySeconds = State(initialValue: original?.delaySeconds ?? 30)
    }
    var body: some View {
        NavigationStack {
            Form {
                Section("已关注的吧") { Picker("贴吧", selection: $selectedID) { ForEach(forums) { Text($0.displayName).tag($0.id) } } }
                Section("关键词，每行一个；任一命中") { TextEditor(text: $keywords).frame(minHeight: 100) }
                Section("自定义回复（标题和首楼正文均参与匹配）") {
                    NativeEmoticonEditor(text: $message, selection: $selection, isFocused: bodyFocused, onFocus: { bodyFocused = true }, onBlur: { bodyFocused = false }).frame(minHeight: 160)
                    Text("\(message.count)/10000").font(.caption).foregroundStyle(.secondary)
                    Button("贴吧表情") { bodyFocused = false; showsEmoticons.toggle() }
                    if showsEmoticons {
                        ScrollView(.horizontal) { HStack { ForEach(ContentComposerEmoticonCatalog.entries) { entry in
                            Button { insert(entry.token) } label: {
                                if let image = TiebaEmoticon.cachedImage(for: entry.imageName) { Image(uiImage: image).resizable().frame(width: 28, height: 28) }
                                else { Text(entry.label) }
                            }.accessibilityLabel("插入" + entry.label)
                        } } }
                    }
                }
                Section("发送延迟（0至3600秒）") { TextField("秒数", value: $delaySeconds, format: .number).keyboardType(.numberPad) }
                if let errorMessage { Section { Text(errorMessage).foregroundStyle(.red) } }
            }
            .navigationTitle(original == nil ? "添加规则" : "编辑规则")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("取消") { bodyFocused = false; dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("保存") {
                    bodyFocused = false
                    do {
                        guard let forum = forums.first(where: { $0.id == selectedID }) else { throw AutoReplyError.invalidRule }
                        let rule = AutoReplyRule(id: original?.id ?? UUID().uuidString, forum: forum, keywords: AutoReplyRule.keywords(keywords), message: message, delaySeconds: delaySeconds, enabled: original?.enabled ?? true)
                        try rule.validate(); try save(rule); dismiss()
                    } catch { errorMessage = error.localizedDescription }
                } }
            }
        }
    }
    private func insert(_ token: String) {
        let text = message as NSString
        let start = min(selection.location, text.length)
        let length = min(selection.length, text.length - start)
        message = text.replacingCharacters(in: NSRange(location: start, length: length), with: token)
        selection = NSRange(location: start + token.utf16.count, length: 0)
    }
}

struct AutoReplyPromptHost: View {
    @ObservedObject var coordinator: AutoReplyCoordinator
    @ObservedObject private var store: AutoReplyStore
    let account: Account?
    @State private var prompted = false
    @State private var showsPrompt = false
    @State private var showsTasks = false
    init(coordinator: AutoReplyCoordinator, account: Account?) { self.coordinator = coordinator; self.account = account; _store = ObservedObject(wrappedValue: coordinator.store) }
    private var pending: Int { store.state.jobs.values.filter { $0.status == .review && coordinator.promptJobIDs.contains($0.id) }.count }
    var body: some View {
        Color.clear
            .onReceive(store.$state) { _ in Task { @MainActor in presentIfNeeded() } }
            .onChange(of: coordinator.foreground) { _ in presentIfNeeded() }
            .onChange(of: coordinator.isScanning) { _ in presentIfNeeded() }
            .onChange(of: coordinator.promptJobIDs) { _ in presentIfNeeded() }
            .onChange(of: coordinator.readyForPrompts) { _ in presentIfNeeded() }
            .onChange(of: coordinator.reviewEpoch) { _ in prompted = false }
            .onChange(of: account?.sessionIdentity) { _ in prompted = false; showsPrompt = false; showsTasks = false }
            .alert("补扫发现\(pending)条匹配新帖", isPresented: $showsPrompt) {
                Button("查看并确认") { showsTasks = true }
                Button("稍后", role: .cancel) { }
            } message: { Text("后台监测曾中断，这些任务不会直接发送。请查看关键词和回复内容后确认。") }
            .sheet(isPresented: $showsTasks) {
                if let account { NavigationStack { AutoReplySettingsView(account: account, coordinator: coordinator).toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { showsTasks = false } } } } }
            }
    }
    private func presentIfNeeded() {
        guard !prompted, coordinator.foreground, coordinator.readyForPrompts, !coordinator.isScanning, let account, store.accountID == account.id, store.enabled(account.id), pending > 0 else { return }
        prompted = true; showsPrompt = true
        try? coordinator.engine.acknowledgePrompts(account.id, ids: coordinator.promptJobIDs)
    }
}
