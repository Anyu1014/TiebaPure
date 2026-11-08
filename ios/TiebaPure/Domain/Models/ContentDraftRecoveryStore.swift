import CryptoKit
import Foundation

@MainActor
final class ContentDraftRecoveryStore {
    private struct Activation: Codable {
        let formatVersion: Int
        let generationID: String
    }

    private let directoryURL: URL
    private let fileManager: FileManager

    init(directoryURL: URL, fileManager: FileManager = .default) {
        self.directoryURL = directoryURL
        self.fileManager = fileManager
    }

    func backend(accountID: String, target: ContentSubmissionTarget) throws -> FileContentDraftPersistenceBackend? {
        guard let directory = try targetDirectory(accountID: accountID, target: target, create: false) else { return nil }
        let marker = try activationFile(in: directory)
        guard let activation = try marker.load() else {
            let children = try fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            guard children.contains(where: { UUID(uuidString: $0.lastPathComponent) != nil }) == false else {
                throw ContentDraftPersistenceStateError.invalidState
            }
            return nil
        }
        guard activation.formatVersion == 1, UUID(uuidString: activation.generationID) != nil else {
            throw ContentDraftPersistenceStateError.invalidState
        }
        let generation = directory.appendingPathComponent(activation.generationID, isDirectory: true)
        guard try prepareDirectory(generation, create: false) else { throw ContentDraftPersistenceError.unavailable }
        let backend = try FileContentDraftPersistenceBackend(
            directoryURL: generation,
            fileManager: fileManager
        )
        guard try backend.catalogPresence() != .absent else { throw ContentDraftPersistenceError.unavailable }
        return backend
    }

    func reset(accountID: String, target: ContentSubmissionTarget) throws {
        guard let directory = try targetDirectory(accountID: accountID, target: target, create: true) else {
            throw ContentDraftPersistenceError.unavailable
        }
        try resetDirectory(directory, accountID: accountID)
    }

    func clear(accountID: String) throws {
        guard let accountDirectory = try accountDirectory(accountID: accountID, create: false) else { return }
        for directory in try fileManager.contentsOfDirectory(at: accountDirectory, includingPropertiesForKeys: nil) {
            guard directory.lastPathComponent.count == 64,
                  directory.lastPathComponent.utf8.allSatisfy({ (48...57).contains($0) || (97...102).contains($0) }) else { continue }
            _ = try prepareDirectory(directory, create: false)
            try resetDirectory(directory, accountID: accountID)
        }
    }

    private func resetDirectory(_ directory: URL, accountID: String) throws {
        let marker = try activationFile(in: directory)
        do { _ = try marker.load() }
        catch { try marker.quarantineCorruptedGenerations() }
        let generationID = UUID().uuidString.lowercased()
        let backend = try FileContentDraftPersistenceBackend(
            directoryURL: directory.appendingPathComponent(generationID, isDirectory: true),
            fileManager: fileManager
        )
        guard backend.clear(accountID: accountID) else { throw ContentDraftPersistenceError.unavailable }
        // Commit the new writable store before hiding the unreadable original target.
        try marker.replace(Activation(formatVersion: 1, generationID: generationID))
        for old in (try? fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)) ?? []
            where old.lastPathComponent != generationID && UUID(uuidString: old.lastPathComponent) != nil {
            try? fileManager.removeItem(at: old)
        }
    }

    private func activationFile(in directory: URL) throws -> SecureCodableFile<Activation> {
        try SecureCodableFile(directoryURL: directory, fileName: "activation.json", fileManager: fileManager, maximumByteCount: 4_096)
    }

    private func targetDirectory(accountID: String, target: ContentSubmissionTarget, create: Bool) throws -> URL? {
        guard let accountDirectory = try accountDirectory(accountID: accountID, create: create) else { return nil }
        let directory = accountDirectory.appendingPathComponent(digest(target.draftKey), isDirectory: true)
        return try prepareDirectory(directory, create: create) ? directory : nil
    }

    private func accountDirectory(accountID: String, create: Bool) throws -> URL? {
        guard accountID.isEmpty == false,
              accountID == accountID.trimmingCharacters(in: .whitespacesAndNewlines),
              accountID.utf8.count <= 4_096 else { throw FileContentDraftPersistenceError.invalidAccountID }
        guard try prepareDirectory(directoryURL, create: create) else { return nil }
        let directory = directoryURL.appendingPathComponent(digest(accountID), isDirectory: true)
        return try prepareDirectory(directory, create: create) ? directory : nil
    }

    private func prepareDirectory(_ url: URL, create: Bool) throws -> Bool {
        if fileManager.fileExists(atPath: url.path) {
            let type = try fileManager.attributesOfItem(atPath: url.path)[.type] as? FileAttributeType
            guard type != .typeSymbolicLink else { throw SecureFilePersistenceError.pathIsSymbolicLink }
            guard type == .typeDirectory else { throw SecureFilePersistenceError.pathIsNotDirectory }
            return true
        }
        guard create else { return false }
        try fileManager.createDirectory(at: url, withIntermediateDirectories: true, attributes: [
            .posixPermissions: NSNumber(value: Int16(0o700)),
            .protectionKey: FileProtectionType.complete
        ])
        return true
    }

    private func digest(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
