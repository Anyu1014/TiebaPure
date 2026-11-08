import Foundation
import CryptoKit

enum PrivateVoiceUpload {
    static func upload(_ url: URL, duration: Int, account: Account, clientID: String, session injectedSession: URLSession? = nil) async throws -> String {
        guard url.isFileURL, (1...60).contains(duration), BaiduCredentialPolicy.isValid(account) else { throw PrivateMessageError.invalidResponse }
        do {
        let values = try url.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
        guard values.isRegularFile == true, let size = values.fileSize, (1...2_097_152).contains(size) else { throw PrivateMessageError.invalidResponse }
        let data = try Data(contentsOf: url)
        guard data.count == size,
              data.starts(with: Data("#!AMR\n".utf8)) || data.count >= 44 && data.starts(with: Data("RIFF".utf8)) && data.subdata(in: 8..<12) == Data("WAVE".utf8) else { throw PrivateMessageError.invalidResponse }
        let digest = Insecure.MD5.hash(data: data).map { String(format: "%02x", $0) }.joined()
        let session = injectedSession ?? VoiceAudioClient.makeSession()
        defer { if injectedSession == nil { session.invalidateAndCancel() } }
        let client = TiebaHTTPClient(session: session, maximumResponseBytes: 65_536)
        let (common, headers) = await PrivateMessageMediaUpload.requestContext(account: account, clientID: clientID)
        let chunkSize = 30_720
        for (index, offset) in stride(from: 0, to: size, by: chunkSize).enumerated() {
            try Task.checkCancellation()
            let end = min(offset + chunkSize, size)
            var fields = common.merging(["chunk_md5": digest, "voice_md5": digest, "length": String(end - offset), "offset": String(offset), "total_length": String(size), "chunk_no": String(index + 1), "total_num": String((size + chunkSize - 1) / chunkSize)]) { _, new in new }
            fields["sign"] = TiebaFormSigner.sign(fields: fields, secret: "tiebaclient!!!")
            let form = MultipartFormData(boundary: "TiebaPureVoice-\(UUID().uuidString)")
            for (key, value) in fields.sorted(by: { $0.key < $1.key }) { form.addField(name: key, value: value) }
            form.addFile(name: "voice_chunk", filename: "file", contentType: nil, data: data.subdata(in: offset..<end))
            let response = try await client.postRaw(.privateVoiceChunk, body: form.finalize(), contentType: "multipart/form-data; boundary=\(form.boundary)", headers: headers)
            _ = try PrivateMessageMediaUpload.validatedResponse(response, kind: "语音")
        }
        try Task.checkCancellation()
        let fields = common.merging(["voice_md5": digest, "type": "2"]) { _, new in new }
        let response = try await client.postFormData(.privateVoiceFinish, fields: fields, headers: headers, signingSecret: "tiebaclient!!!")
        let result = try PrivateMessageMediaUpload.validatedResponse(response, kind: "语音")
        guard let info = result["info"] as? [String: Any], let rawID = info["voice_md5"] as? String,
              let voiceID = VoiceAudioURLPolicy.normalizedVoiceID(rawID) else { throw ContentSubmissionError.unsupported(message: "私信语音上传收据缺少有效 voice_md5。") }
        let voice: [String: Any] = ["type": 10, "voice_md5": voiceID, "during_time": duration, "has_read": 0, "isUploaded": true]
        return String(decoding: try JSONSerialization.data(withJSONObject: [voice]), as: UTF8.self)
        } catch { throw PrivateMessageMediaUpload.uploadError(error, kind: "语音") }
    }
}
