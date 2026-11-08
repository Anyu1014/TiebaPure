import Foundation
import Combine
import CryptoKit

@MainActor
final class AutoReplyStore: ObservableObject {
    @Published private(set) var state = AutoReplyState()
    @Published private(set) var accountID: String?
    @Published private(set) var loaded = false
    private let root: URL
    private let defaults: UserDefaults
    private var loading: Task<AutoReplyState, Error>?
    init(root: URL? = nil, defaults: UserDefaults = .standard) {
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("TiebaPure/Automation", isDirectory: true)
        self.defaults = defaults
    }
    func enabled(_ uid: String) -> Bool { defaults.bool(forKey: "auto-reply-enabled-" + uid) }
    func background(_ uid: String) -> Bool { defaults.bool(forKey: "auto-reply-background-" + uid) }
    func setEnabled(_ uid: String, _ value: Bool) { objectWillChange.send(); defaults.set(value, forKey: "auto-reply-enabled-" + uid) }
    func setBackground(_ uid: String, _ value: Bool) { objectWillChange.send(); defaults.set(value, forKey: "auto-reply-background-" + uid) }
    func selectAccount(_ uid: String?) {
        guard accountID != uid else { return }
        loading?.cancel(); loading = nil; state = AutoReplyState(); loaded = false; accountID = uid
    }
    private func file(_ uid: String) -> URL {
        let hash = SHA256.hash(data: Data(uid.utf8)).map { String(format: "%02x", $0) }.joined()
        return root.appendingPathComponent(hash + ".json")
    }
    func load(_ uid: String) async throws {
        guard accountID == uid else { throw CancellationError() }
        if loaded { return }
        let url = file(uid)
        if loading == nil {
            loading = Task.detached {
                if !FileManager.default.fileExists(atPath: url.path) { return AutoReplyState() }
                let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard size <= 32 * 1024 * 1024 else { throw AutoReplyError.capacity }
                let state = try JSONDecoder().decode(AutoReplyState.self, from: Data(contentsOf: url))
                guard state.version == 1, state.rules.count <= 20, state.jobs.count <= 5000 else { throw AutoReplyError.capacity }
                try state.rules.forEach { try $0.validate() }
                return state
            }
        }
        let result: AutoReplyState
        do { result = try await loading!.value }
        catch { if accountID == uid { loading = nil }; throw error }
        guard accountID == uid, !Task.isCancelled else { throw CancellationError() }
        if !loaded { state = result; loaded = true }
        loading = nil
    }
    func commit(_ uid: String, _ mutation: (inout AutoReplyState) throws -> Void) throws {
        guard loaded, accountID == uid else { throw CancellationError() }
        var next = state
        try mutation(&next)
        // A late send receipt must not restore a history entry the user deleted.
        if let deleted = next.deletedThreadIDs {
            for id in next.jobs.keys where deleted.contains(id) { next.jobs[id] = nil }
        }
        let data = try JSONEncoder().encode(next)
        guard data.count <= 32 * 1024 * 1024 else { throw AutoReplyError.capacity }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        var rootURL = root
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try rootURL.setResourceValues(values)
#if os(iOS)
        try data.write(to: file(uid), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
#else
        try data.write(to: file(uid), options: .atomic)
#endif
        state = next
    }
    func deleteJobs(_ uid: String, ids: Set<Int64>) throws {
        try commit(uid) { state in
            let removed = ids.intersection(state.jobs.keys)
            state.deletedThreadIDs = (state.deletedThreadIDs ?? []).union(removed)
            for id in removed { state.jobs[id] = nil }
        }
    }
    func saveRule(_ uid: String, rule: AutoReplyRule) throws {
        try rule.validate()
        try commit(uid) { state in
            guard state.rules.filter({ $0.id != rule.id }).count < 20 else { throw AutoReplyError.tooManyRules }
            guard !state.rules.contains(where: { $0.id != rule.id && $0.forum.id == rule.forum.id }) else { throw AutoReplyError.duplicateForum }
            cancelPending(rule.id, state: &state)
            if state.rules.contains(where: { $0.id == rule.id && $0.forum.id != rule.forum.id }) { state.cursors[rule.id] = nil }
            state.rules.removeAll { $0.id == rule.id }; state.rules.append(rule)
        }
    }
    func removeRule(_ uid: String, id: String) throws {
        try commit(uid) { state in cancelPending(id, state: &state); state.rules.removeAll { $0.id == id }; state.cursors[id] = nil }
    }
    private func cancelPending(_ ruleID: String, state: inout AutoReplyState) {
        for (id, var job) in state.jobs where job.ruleID == ruleID && [.queued, .review].contains(job.status) {
            job.status = .skipped; job.note = "规则已修改或删除"; state.jobs[id] = job
        }
    }
}
