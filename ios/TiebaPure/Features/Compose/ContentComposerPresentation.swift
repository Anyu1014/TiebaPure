import Foundation
import SwiftUI

struct ContentComposerRoute: Identifiable, Equatable {
    let id = UUID()
    var target: ContentSubmissionTarget
}

enum ContentSubmissionRiskPolicy {
    static let acknowledgementKey = "TiebaPure.contentSubmissionRiskAcknowledged.v2"

    static func hasAcknowledged(defaults: UserDefaults = .standard) -> Bool {
        defaults.bool(forKey: acknowledgementKey)
    }

    static func acknowledge(defaults: UserDefaults = .standard) {
        defaults.set(true, forKey: acknowledgementKey)
    }

    static func reset(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: acknowledgementKey)
    }
}

private enum ContentComposerPresentationError: LocalizedError {
    case draftPersistenceUnavailable

    var errorDescription: String? {
        "草稿暂时无法保存，请保留当前页面后重试。"
    }
}

enum ContentComposerDraftLoadState: Equatable {
    case loading
    case loaded
    case unavailable
    case damaged(ContentDraftDamage)
    case deletingDamaged(ContentDraftDamage)
    case damagedDeleteFailed(ContentDraftDamage)

    static func afterDamagedDraftDeletion(
        damage: ContentDraftDamage,
        succeeded: Bool
    ) -> ContentComposerDraftLoadState {
        succeeded ? .loaded : .damagedDeleteFailed(damage)
    }
}

private extension ContentDraftDamage {
    var userFacingMessage: String {
        switch self {
        case .targetMetadata:
            return "本机草稿的发布位置数据无法恢复。为避免发到错误位置，编辑器不会打开这份草稿。"
        case .attachmentContainer:
            return "本机草稿的图片数据无法恢复。为避免覆盖现有内容，编辑器不会打开这份草稿。"
        }
    }

    var accessibilityDescription: String {
        switch self {
        case .targetMetadata:
            return "发布位置数据损坏"
        case .attachmentContainer:
            return "图片数据损坏"
        }
    }
}

struct ContentComposerPresentation: View {
    @EnvironmentObject private var environment: AppEnvironment
    @Environment(\.dismiss) private var dismiss

    let account: Account
    let target: ContentSubmissionTarget
    let onDismiss: () -> Void
    var onSent: (ContentSubmissionReceipt) -> Void = { _ in }
    var onDraftCleanupFailure: () -> Void = {}

    @State private var draft: ContentDraft?
    @State private var draftLoadState: ContentComposerDraftLoadState = .loading
    @State private var preservesUnreadDraft = false
    @State private var showsRiskConfirmation = false
    @State private var showsDamagedDraftDeletionConfirmation = false
    @State private var showsDraftClearConfirmation = false
    @State private var isClearingDraft = false
    @State private var draftClearError: String?
#if DEBUG
    @State private var fixtureDraftLoadAttempts = 0
#endif

    var body: some View {
        Group {
            switch draftLoadState {
            case .loaded:
                ContentComposerView(
                    target: target,
                    initialTitle: draft?.title ?? "",
                    initialBody: draft?.body ?? "",
                    initialImages: draft?.images ?? [],
                    draftPersistenceNotice: preservesUnreadDraft ? "原有草稿已保留。本次编辑暂不能保存草稿，请发送后再退出。" : nil,
                    onCancel: close,
                    onSaveDraft: saveDraft,
                    onSend: submit,
                    onSent: finish
                )
            case .loading:
                draftStateNavigation {
                    ReaderStateView.loading("正在恢复草稿")
                        .accessibilityIdentifier("content-composer-loading")
                }
            case .unavailable:
                draftStateNavigation {
                    VStack(spacing: 16) {
                        ReaderStateView.error(
                            title: "无法恢复草稿",
                            message: "本机草稿暂时无法读取。你可以重试，或清空此发布位置的草稿信息后重新编辑。",
                            actionTitle: "重试",
                            action: retryDraftLoad
                        )
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("content-composer-draft-load-error")
                        Button(role: .destructive) {
                            showsDraftClearConfirmation = true
                        } label: {
                            if isClearingDraft { ProgressView("正在清空草稿") }
                            else { Text("清空草稿信息") }
                        }
                        .frame(minHeight: 44)
                        .disabled(isClearingDraft)
                        .accessibilityIdentifier("content-composer-clear-draft")
                        if let draftClearError {
                            Text(draftClearError).foregroundStyle(.red).multilineTextAlignment(.center)
                        }
                        Button("暂不恢复草稿，继续编辑") {
                            preservesUnreadDraft = true
                            draft = nil
                            draftLoadState = .loaded
                            presentRiskConfirmationIfNeeded()
                        }
                        .frame(minHeight: 44)
                        .disabled(isClearingDraft)
                        .accessibilityIdentifier("content-composer-continue-without-draft")
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal, 16)
                }
            case let .damaged(damage):
                damagedDraftNavigation(
                    damage: damage,
                    deletionFailed: false
                )
            case let .deletingDamaged(damage):
                draftStateNavigation {
                    ReaderStateView.loading("正在删除损坏草稿")
                        .accessibilityIdentifier("content-composer-draft-deleting")
                        .accessibilityValue(damage.accessibilityDescription)
                }
            case let .damagedDeleteFailed(damage):
                damagedDraftNavigation(
                    damage: damage,
                    deletionFailed: true
                )
            }
        }
        .task {
            await loadDraft()
        }
        .alert("实验性发布功能", isPresented: $showsRiskConfirmation) {
            Button("取消", role: .cancel) {
                close()
            }
            Button("了解并继续") {
                ContentSubmissionRiskPolicy.acknowledge()
            }
        } message: {
            Text("TiebaPure 通过非官方实验接口发帖和回复。使用时可能触发贴吧风控，导致内容被隐藏或删除、发帖或回帖等账号功能受限；极端情况下账号可能被冻结。若发送结果无法确认，应用不会自动重发，请先刷新页面核对。")
        }
        .confirmationDialog(
            "删除损坏草稿？",
            isPresented: $showsDamagedDraftDeletionConfirmation,
            titleVisibility: .visible
        ) {
            Button("删除草稿并继续", role: .destructive) {
                deleteDamagedDraft()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("损坏草稿无法恢复。删除后将打开空编辑器，此操作无法撤销。")
        }
        .confirmationDialog("清空此位置的草稿信息？", isPresented: $showsDraftClearConfirmation, titleVisibility: .visible) {
            Button("清空当前草稿", role: .destructive, action: clearUnavailableDraft)
            Button("取消", role: .cancel) {}
        } message: {
            Text("只清空当前账号在此发布位置的草稿信息，其他草稿不受影响。清空后将打开可保存新草稿的空白编辑器。")
        }
    }

    private func draftStateNavigation<Content: View>(
        @ViewBuilder content: () -> Content
    ) -> some View {
        NavigationStack {
            content()
                .background(Color(uiColor: .systemGroupedBackground))
                .navigationTitle("发布内容")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消", action: close)
                            .frame(minWidth: 44, minHeight: 44)
                    }
                }
        }
    }

    private func damagedDraftNavigation(
        damage: ContentDraftDamage,
        deletionFailed: Bool
    ) -> some View {
        draftStateNavigation {
            ReaderStateView.error(
                title: deletionFailed ? "删除草稿失败" : "草稿已损坏",
                message: deletionFailed
                    ? "损坏草稿仍保留在本机，编辑器不会覆盖它。请重试删除或稍后再试。"
                    : damage.userFacingMessage,
                actionTitle: deletionFailed ? "重试删除" : "删除草稿并继续",
                action: {
                    showsDamagedDraftDeletionConfirmation = true
                }
            )
            .accessibilityIdentifier(
                deletionFailed
                    ? "content-composer-draft-delete-failed"
                    : "content-composer-draft-damaged"
            )
        }
    }

    @MainActor
    private func loadDraft() async {
#if DEBUG
        if ProcessInfo.processInfo.arguments.contains("UITEST_FAIL_CONTENT_DRAFT_LOAD_ONCE"),
           fixtureDraftLoadAttempts == 0 {
            fixtureDraftLoadAttempts += 1
            draft = nil
            draftLoadState = .unavailable
            return
        }
#endif
        let outcome = await environment.contentDraftStore.loadAsync(
            accountID: account.id,
            target: target
        )
        guard Task.isCancelled == false else { return }
        switch outcome {
        case let .loaded(loadedDraft):
            draft = loadedDraft
            draftLoadState = .loaded
            presentRiskConfirmationIfNeeded()
        case let .damaged(damage):
            draft = nil
            draftLoadState = .damaged(damage)
        case .unavailable:
            draft = nil
            draftLoadState = .unavailable
        }
    }

    private func presentRiskConfirmationIfNeeded() {
        if ContentSubmissionRiskPolicy.hasAcknowledged() == false {
            showsRiskConfirmation = true
        }
    }

    private func retryDraftLoad() {
        guard isClearingDraft == false else { return }
        draftClearError = nil
        draftLoadState = .loading
        Task {
            await loadDraft()
        }
    }

    private func clearUnavailableDraft() {
        guard isClearingDraft == false else { return }
        isClearingDraft = true
        draftClearError = nil
        Task { @MainActor in
            defer { isClearingDraft = false }
            do {
                try environment.contentDraftStore.reset(accountID: account.id, target: target)
                let outcome = await environment.contentDraftStore.loadAsync(accountID: account.id, target: target)
                guard outcome == .loaded(nil) else { throw ContentDraftPersistenceError.unavailable }
                guard Task.isCancelled == false else { return }
                draft = nil
                preservesUnreadDraft = false
                draftLoadState = .loaded
                presentRiskConfirmationIfNeeded()
            } catch {
                draftClearError = "草稿暂时无法清空，请重试。"
            }
        }
    }

    private func deleteDamagedDraft() {
        let damage: ContentDraftDamage
        switch draftLoadState {
        case let .damaged(currentDamage), let .damagedDeleteFailed(currentDamage):
            damage = currentDamage
        default:
            return
        }

        draftLoadState = .deletingDamaged(damage)
        Task { @MainActor in
            let didDelete = environment.contentDraftStore.delete(
                accountID: account.id,
                target: target
            )
            guard Task.isCancelled == false else { return }
            draft = nil
            draftLoadState = .afterDamagedDraftDeletion(
                damage: damage,
                succeeded: didDelete
            )
            if didDelete {
                presentRiskConfirmationIfNeeded()
            }
        }
    }

    private func saveDraft(_ request: ContentSubmissionRequest) async throws {
        guard preservesUnreadDraft == false else {
            throw ContentComposerPresentationError.draftPersistenceUnavailable
        }
        let updatedAt = Date()
        do {
            try await environment.contentDraftStore.saveAsync(
                accountID: account.id,
                target: request.target,
                title: request.title,
                body: request.body,
                images: request.images,
                updatedAt: updatedAt
            )
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw ContentComposerPresentationError.draftPersistenceUnavailable
        }
        draft = ContentDraft(
            accountID: account.id,
            target: request.target,
            title: request.title,
            body: request.body,
            images: request.images,
            updatedAt: updatedAt
        )
    }

    private func submit(_ request: ContentSubmissionRequest) async throws -> ContentSubmissionReceipt {
        try await environment.contentSubmissionCoordinator.submit(
            account: account,
            request: request
        )
    }

    private func finish(_ receipt: ContentSubmissionReceipt) {
        draft = nil
        let didDeleteDraft = preservesUnreadDraft || environment.contentDraftStore.delete(accountID: account.id, target: target)
        onSent(receipt)
        DispatchQueue.main.async {
            close()
        }
        if didDeleteDraft == false {
            onDraftCleanupFailure()
        }
    }

    private func close() {
        dismiss()
        onDismiss()
    }
}

