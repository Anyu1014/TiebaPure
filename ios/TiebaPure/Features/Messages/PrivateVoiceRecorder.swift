import AVFoundation
import Combine

@MainActor
final class PrivateVoiceRecorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published private(set) var recording = false
    @Published private(set) var attachment: URL?
    @Published private(set) var duration = 0
    @Published private(set) var requestingPermission = false
    @Published private(set) var error: String?
    private var recorder: AVAudioRecorder?
    private var limit: Task<Void, Never>?
    private var startedAt: TimeInterval = 0

    func start() async throws {
        guard !requestingPermission && !recording else { return }
        requestingPermission = true
        defer { requestingPermission = false }
        error = nil
        let allowed = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard allowed else { throw PrivateVoiceError.permission }
        try Task.checkCancellation()
        discard()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("private-voice-\(UUID().uuidString).wav")
        do {
            let recorder = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatLinearPCM, AVSampleRateKey: 8_000, AVNumberOfChannelsKey: 1, AVLinearPCMBitDepthKey: 16, AVLinearPCMIsFloatKey: false, AVLinearPCMIsBigEndianKey: false])
            recorder.delegate = self
            guard recorder.record(forDuration: 60) else { throw PrivateVoiceError.recording }
            self.recorder = recorder
            startedAt = ProcessInfo.processInfo.systemUptime
            recording = true
            limit = Task { try? await Task.sleep(nanoseconds: 60_000_000_000); if !Task.isCancelled { stop() } }
        } catch {
            try? FileManager.default.removeItem(at: url)
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            throw error
        }
    }

    func stop() {
        guard let recorder, recording else { return }
        duration = min(60, max(1, Int((ProcessInfo.processInfo.systemUptime - startedAt).rounded(.up))))
        attachment = recorder.url
        recording = false
        recorder.stop()
        self.recorder = nil
        limit?.cancel()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    nonisolated func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        let url = recorder.url
        Task { @MainActor [weak self] in
            guard let self, self.recorder?.url == url else { return }
            if flag { stop() }
            else { discard(); error = "录音未能完成，请重新录制。" }
        }
    }

    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        let url = recorder.url
        Task { @MainActor [weak self] in
            guard let self, self.recorder?.url == url else { return }
            discard(); self.error = "录音未能完成，请重新录制。"
        }
    }

    func discard() {
        limit?.cancel()
        let url = recorder?.url ?? attachment
        recorder?.stop(); recorder = nil; recording = false; attachment = nil; duration = 0
        if let url { try? FileManager.default.removeItem(at: url) }
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

private enum PrivateVoiceError: LocalizedError {
    case permission, recording
    var errorDescription: String? {
        switch self {
        case .permission: return "请在系统设置中允许麦克风权限后录制语音。"
        case .recording: return "无法开始录音，请稍后重试。"
        }
    }
}
