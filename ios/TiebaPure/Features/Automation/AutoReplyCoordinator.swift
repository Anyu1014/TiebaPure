import Foundation
import SwiftUI
import UIKit
import BackgroundTasks

@MainActor
final class LiveAutoReplyBackend: AutoReplyBackend {
    let api: any TiebaAPIService
    let submissions: ContentSubmissionCoordinator
    init(api: any TiebaAPIService, submissions: ContentSubmissionCoordinator) { self.api = api; self.submissions = submissions }
    func followed(_ account: Account) async throws -> [Forum] { try await api.followedForums(account: account) }
    func threads(_ forum: Forum, page: Int, account: Account) async throws -> [AutoReplyThread] {
        try await api.forumThreads(account: account, forumName: forum.name, page: page, category: .publishTime).map {
            AutoReplyThread(id: $0.id, title: $0.title, authorID: String($0.author.id), createdAt: $0.createdAt?.timeIntervalSince1970 ?? 0, pinned: $0.isTop)
        }
    }
    func body(_ thread: AutoReplyThread, account: Account, forum: Forum) async throws -> String {
        let page = try await api.threadPage(account: account, threadID: thread.id, page: 1, forumID: forum.id, postID: nil, seeLz: false, sortType: .ascending)
        guard let first = page.mainPost ?? page.posts.first(where: { $0.floor == 1 }), !page.mainPostIsSummaryFallback else { throw AutoReplyError.unreadableBody }
        return first.blocks.map { block in
            switch block {
            case let .text(text): return text
            case let .link(title, _): return title
            case let .mention(_, text): return text
            case let .emoticon(code): return code
            default: return ""
            }
        }.joined(separator: "\n")
    }
    func reply(_ job: AutoReplyJob, account: Account) async throws -> UInt64? {
        let thread = ThreadSummary(id: job.id, title: job.title, author: UserSummary(id: 0, name: "", displayName: "", portrait: "", level: nil), replyCount: 0, viewCount: 0, blocks: [])
        let target = ContentSubmissionTarget.threadReply(thread: thread, forum: job.forum)
        return try await submissions.submit(account: account, request: ContentSubmissionRequest(target: target, title: "", body: job.message, images: [])).postID
    }
}

@MainActor
final class AutoReplyCoordinator: ObservableObject {
    let store: AutoReplyStore
    let engine: AutoReplyEngine
    @Published private(set) var status = "自动回复未开启"
    @Published private(set) var foreground = true
    @Published private(set) var reviewEpoch = 0
    @Published private(set) var isScanning = false
    @Published private(set) var readyForPrompts = false
    @Published private(set) var promptJobIDs = Set<Int64>()
    private let accounts: AccountStore
    private var account: Account?
    private var controller: Task<Void, Never>?
    private var draining: Task<Void, Never>?
    private var generation = 0
    private var lastScan = Date.distantPast
    private var backgroundLease: UIBackgroundTaskIdentifier = .invalid
    private var backgroundWindow: Task<Void, Never>?
    init(accounts: AccountStore, api: any TiebaAPIService, submissions: ContentSubmissionCoordinator, settings: ContentSubmissionSettingsStore, store: AutoReplyStore? = nil) {
        let resolved = store ?? AutoReplyStore()
        self.accounts = accounts; self.store = resolved
        engine = AutoReplyEngine(store: resolved, api: LiveAutoReplyBackend(api: api, submissions: submissions), currentAccount: { try await accounts.load() }, repliesAllowed: { settings.repliesEnabled })
    }
    func accountChanged(_ value: Account?) {
        if account?.sessionIdentity == value?.sessionIdentity { return }
        pause(); account = value; store.selectAccount(value?.id)
        if foreground { restart() }
    }
    func pause() {
        generation += 1
        if let previous = controller {
            previous.cancel()
            let older = draining
            draining = Task { await older?.value; await previous.value }
        }
        controller = nil; backgroundWindow?.cancel(); backgroundWindow = nil; endLease()
    }
    func sceneChanged(_ phase: ScenePhase) {
        switch phase {
        case .active:
            let stale = Date().timeIntervalSince(lastScan) > 120
            foreground = true; backgroundWindow?.cancel(); backgroundWindow = nil; endLease()
            if controller == nil || stale { restart() }
        case .background:
            foreground = false
            guard let account, store.enabled(account.id), store.background(account.id) else { pause(); return }
            scheduleRefresh()
            if backgroundLease == .invalid {
                backgroundLease = UIApplication.shared.beginBackgroundTask(withName: "AutoReply") { [weak self] in Task { @MainActor in self?.pause() } }
            }
            backgroundWindow = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 20_000_000_000)
                guard !Task.isCancelled else { return }
                self?.pause()
            }
        default: break
        }
    }
    private func endLease() { if backgroundLease != .invalid { UIApplication.shared.endBackgroundTask(backgroundLease); backgroundLease = .invalid } }
    func configureEnabled(_ value: Bool) {
        guard let account else { return }
        store.setEnabled(account.id, value)
        if value { restart() } else { pause(); status = "自动回复已暂停" }
        scheduleRefresh()
    }
    func configureBackground(_ value: Bool) {
        guard let account else { return }
        store.setBackground(account.id, value); scheduleRefresh()
        if !value && !foreground { pause() }
    }
    func changed() { restart() }
    func loadForSettings(_ value: Account) async throws {
        guard account?.sessionIdentity == value.sessionIdentity else { throw CancellationError() }
        try await store.load(value.id)
    }
    private func restart() {
        pause()
        let previous = draining
        readyForPrompts = false; promptJobIDs = []
        guard foreground, let account, store.enabled(account.id) else { return }
        reviewEpoch += 1
        let current = generation
        controller = Task { [weak self] in
            await previous?.value
            guard let self, current == self.generation else { return }
            do {
                self.status = "等待界面加载，稍后检查新增帖子"
                try await Task.sleep(nanoseconds: 8_000_000_000)
                try await self.store.load(account.id)
                try self.engine.recover(account.id)
                var catchUp = true
                await withTaskGroup(of: Void.self) { group in
                    group.addTask { @MainActor [weak self] in
                        guard let self else { return }
                        while !Task.isCancelled && self.store.enabled(account.id) {
                            do {
                                self.isScanning = true
                                try await self.engine.scan(account, catchUp: catchUp)
                                self.lastScan = Date(); catchUp = false; self.status = "监测中 · 每60秒检查一次"
                            } catch is CancellationError { self.isScanning = false; break }
                            catch { if !catchUp { self.reviewEpoch += 1 }; catchUp = true; try? self.engine.deferQueued(account.id); self.status = "检查暂未完成：" + String(error.localizedDescription.prefix(160)) }
                            self.isScanning = false
                            self.promptJobIDs = self.engine.pendingPrompts
                            self.readyForPrompts = true
                            do { try await Task.sleep(nanoseconds: 60_000_000_000) } catch { break }
                        }
                    }
                    group.addTask { @MainActor [weak self] in
                        guard let self else { return }
                        while !Task.isCancelled && self.store.enabled(account.id) {
                            do { try await self.engine.sendDue(account); if !self.store.enabled(account.id) { self.status = "自动回复已暂停，请检查历史任务" } }
                            catch is CancellationError { break }
                            catch {
                                do { try self.engine.deferQueued(account.id) }
                                catch { self.store.setEnabled(account.id, false) }
                                self.reviewEpoch += 1; self.promptJobIDs = self.engine.pendingPrompts; self.readyForPrompts = true
                                self.status = "发送前检查失败，任务转为待确认：" + String(error.localizedDescription.prefix(160))
                            }
                            do { try await Task.sleep(nanoseconds: 1_000_000_000) } catch { break }
                        }
                    }
                }
            } catch is CancellationError { }
            catch { self.status = "自动回复已暂停：" + String(error.localizedDescription.prefix(160)); self.store.setEnabled(account.id, false) }
        }
    }
    static var refreshIdentifier: String { (Bundle.main.bundleIdentifier ?? "dev.anyu.tiebapure") + ".auto-reply-refresh" }
    func scheduleRefresh() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.refreshIdentifier)
        guard let account, store.enabled(account.id), store.background(account.id) else { return }
        let request = BGAppRefreshTaskRequest(identifier: Self.refreshIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        do { try BGTaskScheduler.shared.submit(request) }
        catch { status = "后台刷新暂不可安排；下次打开仍会补扫" }
    }
    func backgroundRefresh(_ task: BGAppRefreshTask) {
        scheduleRefresh()
        guard UIApplication.shared.applicationState != .active else { task.setTaskCompleted(success: true); return }
        foreground = false
        pause()
        let previous = draining
        let operation = Task { [weak self] in
            await previous?.value
            guard let self else { task.setTaskCompleted(success: false); return }
            do {
                guard let account = try await self.accounts.load(), self.store.enabled(account.id), self.store.background(account.id) else { task.setTaskCompleted(success: true); return }
                self.accountChanged(account)
                self.scheduleRefresh()
                try await self.store.load(account.id)
                try self.engine.recover(account.id)
                try await self.engine.scan(account, catchUp: true)
                task.setTaskCompleted(success: !Task.isCancelled)
            } catch { task.setTaskCompleted(success: false) }
        }
        task.expirationHandler = { operation.cancel() }
    }
}

@MainActor
enum AutoReplyBackground {
    static weak var coordinator: AutoReplyCoordinator?
    static var registered = false
    static func register() {
        guard !registered else { return }; registered = true
        BGTaskScheduler.shared.register(forTaskWithIdentifier: AutoReplyCoordinator.refreshIdentifier, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            Task { @MainActor in
                guard let coordinator else { refresh.setTaskCompleted(success: false); return }
                coordinator.backgroundRefresh(refresh)
            }
        }
    }
}
final class AutoReplyAppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool { AutoReplyBackground.register(); return true }
}
