import CryptoKit
import XCTest
@testable import TiebaPure

@MainActor
final class ContentDraftRecoveryTests: XCTestCase {
    private func makeDirectory() throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("TiebaPure-DraftRecovery-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return directory
    }

    private func target(_ threadID: Int64) -> ContentSubmissionTarget {
        ContentSubmissionTarget(kind: .threadReply, forumID: 1, forumName: "fixture", forumDisplayName: "测试吧", threadID: threadID, threadTitle: "测试帖", parentPostID: nil, parentFloor: nil, subpostID: nil, replyUserID: nil, replyUserDisplayName: nil)
    }

    private func draft(_ threadID: Int64, account: String = "account") -> ContentDraft {
        ContentDraft(accountID: account, target: target(threadID), title: "", body: "草稿\(threadID)", images: [], updatedAt: Date())
    }

    private func store(_ primary: any ContentDraftPersistenceBackend, recovery: URL) -> ContentDraftStore {
        ContentDraftStore(persistence: primary, recoveryStore: { ContentDraftRecoveryStore(directoryURL: recovery) })
    }

    func testVerifiedMissingDraftOpensBlankAndBecomesWritableWithoutManualReset() async throws {
        let directory = try makeDirectory(), current = target(987)
        let value = ContentDraftStore(resolvingPersistence: { UnavailableContentDraftPersistenceBackend() },
            recoveryStore: { ContentDraftRecoveryStore(directoryURL: directory) },
            confirmsMissingDraft: { account, target in account == "account" && target == current })
        let outcome = await value.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(outcome, .loaded(nil))
        let updated = draft(987)
        try await value.saveAsync(updated)
        let recovered = await value.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(recovered, .loaded(updated))
    }

    func testUnknownOrExistingDraftNeverBecomesAnEmptyEditor() async throws {
        let directory = try makeDirectory(), current = target(988)
        let value = ContentDraftStore(resolvingPersistence: { UnavailableContentDraftPersistenceBackend() },
            recoveryStore: { ContentDraftRecoveryStore(directoryURL: directory) }, confirmsMissingDraft: { _, _ in false })
        let outcome = await value.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(outcome, .unavailable)
        XCTAssertNil(try ContentDraftRecoveryStore(directoryURL: directory).backend(accountID: "account", target: current))
    }

    func testResetCreatesWritableBlankDraftWhenPrimaryRemainsPermanentlyUnavailable() async throws {
        let directory = try makeDirectory()
        let current = target(1)
        let first = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        let before = await first.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(before, .unavailable)
        try first.reset(accountID: "account", target: current)
        let cleared = await first.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(cleared, .loaded(nil))
        let next = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        let reopened = await next.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(reopened, .loaded(nil))
        let updated = draft(1)
        try await next.saveAsync(updated)
        let final = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        let saved = await final.loadAsync(accountID: "account", target: current)
        XCTAssertEqual(saved, .loaded(updated))
    }

    func testResetOnlyOverridesCurrentAccountAndTargetEvenWhenOriginalStoreRecovers() async throws {
        let directory = try makeDirectory()
        let original = try FileContentDraftPersistenceBackend(directoryURL: directory.appendingPathComponent("original"))
        let current = draft(1), other = draft(2), otherAccount = draft(1, account: "other-account")
        for value in [current, other, otherAccount] { XCTAssertTrue(original.save(value)) }
        let recovery = directory.appendingPathComponent("recovery")
        let first = store(UnavailableContentDraftPersistenceBackend(), recovery: recovery)
        try first.reset(accountID: current.accountID, target: current.target)
        XCTAssertEqual(original.draft(accountID: other.accountID, target: other.target), other)
        XCTAssertEqual(original.draft(accountID: otherAccount.accountID, target: otherAccount.target), otherAccount)
        let reopened = store(original, recovery: recovery)
        let cleared = await reopened.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(cleared, .loaded(nil))
        XCTAssertEqual(reopened.draft(accountID: other.accountID, target: other.target), other)
        XCTAssertEqual(reopened.draft(accountID: otherAccount.accountID, target: otherAccount.target), otherAccount)
        let updated = draft(1)
        try await reopened.saveAsync(updated)
        XCTAssertEqual(reopened.draft(accountID: updated.accountID, target: updated.target), updated)
    }

    func testResetReplacesDamagedRecoveredGenerationWithoutTouchingOtherRecoveredDrafts() async throws {
        let directory = try makeDirectory()
        let current = draft(1), other = draft(2)
        let value = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        for entry in [current, other] {
            try value.reset(accountID: entry.accountID, target: entry.target)
            try await value.saveAsync(entry)
        }
        func digest(_ text: String) -> String { SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined() }
        let currentDirectory = directory.appendingPathComponent(digest(current.accountID)).appendingPathComponent(digest(current.target.draftKey))
        let descendants = try XCTUnwrap(FileManager.default.enumerator(at: currentDirectory, includingPropertiesForKeys: nil))
        for url in descendants.allObjects.compactMap({ $0 as? URL }) where url.lastPathComponent.hasPrefix("content-drafts-catalog.json") {
            try Data("broken".utf8).write(to: url)
        }
        let damaged = await value.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(damaged, .unavailable)
        try value.reset(accountID: current.accountID, target: current.target)
        let reset = await value.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(reset, .loaded(nil))
        XCTAssertEqual(value.draft(accountID: other.accountID, target: other.target), other)
        try await value.saveAsync(current)
        XCTAssertEqual(value.draft(accountID: current.accountID, target: current.target), current)
    }
    func testResetRebuildsCorruptedActivationAndRefusesSymbolicRecoveryDirectory() async throws {
        let directory = try makeDirectory()
        let current = draft(1)
        let value = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        try value.reset(accountID: current.accountID, target: current.target)
        func digest(_ text: String) -> String { SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined() }
        let targetDirectory = directory.appendingPathComponent(digest(current.accountID)).appendingPathComponent(digest(current.target.draftKey))
        for name in ["activation.json", "activation.json.backup"] {
            try Data("broken".utf8).write(to: targetDirectory.appendingPathComponent(name))
        }
        let corrupted = await value.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(corrupted, .unavailable)
        try value.reset(accountID: current.accountID, target: current.target)
        try await value.saveAsync(current)
        XCTAssertEqual(value.draft(accountID: current.accountID, target: current.target), current)
        let link = directory.appendingPathComponent("symbolic-recovery")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: directory)
        let unsafe = store(UnavailableContentDraftPersistenceBackend(), recovery: link)
        XCTAssertThrowsError(try unsafe.reset(accountID: current.accountID, target: current.target))
    }

    func testMissingCommittedRecoveryStoreRequiresExplicitResetInsteadOfSilentlyLosingDraft() async throws {
        let directory = try makeDirectory()
        let current = draft(1)
        let value = store(UnavailableContentDraftPersistenceBackend(), recovery: directory)
        try value.reset(accountID: current.accountID, target: current.target)
        try await value.saveAsync(current)
        let descendants = try XCTUnwrap(FileManager.default.enumerator(at: directory, includingPropertiesForKeys: nil))
        let generations = descendants.allObjects.compactMap { $0 as? URL }.filter { UUID(uuidString: $0.lastPathComponent) != nil }
        for generation in generations { try FileManager.default.removeItem(at: generation) }
        let missing = await value.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(missing, .unavailable)
        try value.reset(accountID: current.accountID, target: current.target)
        let reset = await value.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(reset, .loaded(nil))
    }

    func testMissingRecoveryMarkerCannotResurrectOriginalDraft() async throws {
        let directory = try makeDirectory()
        let primary = try FileContentDraftPersistenceBackend(directoryURL: directory.appendingPathComponent("original"))
        let current = draft(1)
        XCTAssertTrue(primary.save(current))
        let recovery = directory.appendingPathComponent("recovery")
        let unavailable = store(UnavailableContentDraftPersistenceBackend(), recovery: recovery)
        try unavailable.reset(accountID: current.accountID, target: current.target)
        let descendants = try XCTUnwrap(FileManager.default.enumerator(at: recovery, includingPropertiesForKeys: nil))
        for file in descendants.allObjects.compactMap({ $0 as? URL }) where file.lastPathComponent.hasPrefix("activation.json") {
            try FileManager.default.removeItem(at: file)
        }
        let reopened = store(primary, recovery: recovery)
        let missing = await reopened.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(missing, .unavailable)
        try reopened.reset(accountID: current.accountID, target: current.target)
        let reset = await reopened.loadAsync(accountID: current.accountID, target: current.target)
        XCTAssertEqual(reset, .loaded(nil))
    }

}
