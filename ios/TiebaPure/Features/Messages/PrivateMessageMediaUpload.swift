import Foundation
import CryptoKit

enum PrivateMessageMediaUpload {
    @MainActor
    static func requestContext(account: Account, clientID: String) -> (fields: [String: String], headers: [String: String]) {
        let version = "22.13.1.1"
        var builder = TiebaRequestBuilder.live()
        builder.clientID = clientID
        var fields = builder.officialCommonFields(bduss: account.bduss, baiduID: account.baiduID, clientVersion: version)
        fields["stoken"] = account.stoken
        fields["cuid"] = clientID
        fields["cuid_galaxy2"] = clientID
        fields["support_image"] = "jepgwebp"
        var headers = builder.officialHeaders(clientVersion: version)
        headers["cuid"] = clientID
        headers["cuid_galaxy2"] = clientID
        var cookies = ["CUID=\(clientID)", "ka=open", "BDUSS=\(account.bduss)", "stoken=\(account.stoken)"]
        if let baiduID = account.baiduID { cookies.append("BAIDUID=\(baiduID)") }
        headers["Cookie"] = cookies.joined(separator: "; ")
        return (fields, headers)
    }

    static func image(_ image: ContentSubmissionImage, account: Account, groupID: Int64, clientID: String, session injectedSession: URLSession? = nil) async throws -> String {
        guard BaiduCredentialPolicy.isValid(account) else { throw PrivateMessageError.invalidUser }
        guard groupID > 0 else { throw ContentSubmissionError.unsupported(message: "私信图片上传缺少当前账号的分组，请刷新会话后重试。") }
        do {
        let metadata = try ContentSubmissionImageInspector.inspect(image.data)
        let chunkSize = 512_000
        let resource = Insecure.MD5.hash(data: image.data).map { String(format: "%02X", $0) }.joined() + String(chunkSize)
        let session = injectedSession ?? VoiceAudioClient.makeSession()
        defer { if injectedSession == nil { session.invalidateAndCancel() } }
        let client = TiebaHTTPClient(session: session, maximumResponseBytes: 65_536)
        let (common, headers) = await requestContext(account: account, clientID: clientID)
        var result: Data?
        for (index, offset) in stride(from: 0, to: image.data.count, by: chunkSize).enumerated() {
            try Task.checkCancellation()
            let end = min(offset + chunkSize, image.data.count)
            let form = MultipartFormData(boundary: "TiebaPurePrivateImage-\(UUID().uuidString)")
            var fields = common.merging(["resourceId": resource, "chunkNo": String(index + 1), "isFinish": end == image.data.count ? "1" : "0", "size": String(image.data.count), "width": String(metadata.pixelWidth), "height": String(metadata.pixelHeight), "groupId": String(groupID), "alt": "json", "is_bjh": "0", "saveOrigin": "1", "pic_water_type": "3"]) { _, new in new }
            fields["sign"] = TiebaFormSigner.sign(fields: fields, secret: "tiebaclient!!!")
            for (key, value) in fields.sorted(by: { $0.key < $1.key }) { form.addField(name: key, value: value) }
            form.addFile(name: "chunk", filename: "file", contentType: nil, data: image.data.subdata(in: offset..<end))
            let raw = try await client.postRaw(.uploadPicture, body: form.finalize(), contentType: "multipart/form-data; boundary=\(form.boundary)", headers: headers)
            _ = try validatedResponse(raw, kind: "图片")
            result = raw
        }
        guard let result else { throw PrivateMessageError.invalidResponse }
        return try imageContent(result, width: metadata.pixelWidth, height: metadata.pixelHeight)
        } catch { throw uploadError(error, kind: "图片") }
    }

    static func imageContent(_ response: Data, width: Int, height: Int) throws -> String {
        let json = try validatedResponse(response, kind: "图片")
        guard let info = json["picInfo"] as? [String: Any] else { throw ContentSubmissionError.unsupported(message: "私信图片上传响应缺少 picInfo 收据。") }
        let candidates = ["originPic", "bigPic", "smallPic"].compactMap { info[$0] as? [String: Any] }
        guard let url = candidates.compactMap({ TiebaRemoteMediaPolicy.url($0["picUrl"] as? String) }).first else {
            throw ContentSubmissionError.unsupported(message: "私信图片上传收据缺少有效的图片地址。")
        }
        let thumbnail = TiebaRemoteMediaPolicy.url((info["smallPic"] as? [String: Any])?["picUrl"] as? String) ?? url
        let body = [["src": thumbnail.absoluteString, "big_src": url.absoluteString, "type": "3", "bsize": "\(width),\(height)"]]
        return String(decoding: try JSONSerialization.data(withJSONObject: body), as: UTF8.self)
    }

    static func voice(_ url: URL, duration: Int, account: Account, clientID: String) async throws -> String {
        try await PrivateVoiceUpload.upload(url, duration: duration, account: account, clientID: clientID)
    }

    static func validatedResponse(_ data: Data, kind: String) throws -> [String: Any] {
        guard let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ContentSubmissionError.unsupported(message: "私信\(kind)上传响应格式无法识别。")
        }
        let error = result["error"] as? [String: Any]
        guard let raw = error?["errno"] ?? result["error_code"], let code = Int(String(describing: raw)) else {
            throw ContentSubmissionError.unsupported(message: "私信\(kind)上传响应缺少有效状态码。")
        }
        guard code == 0 else { throw ContentSubmissionError.business(code: code, message: "私信\(kind)上传未通过（错误码 \(code)）。") }
        return result
    }

    static func uploadError(_ error: Error, kind: String) -> Error {
        if case let TiebaHTTPError.badStatus(code, _) = error {
            return ContentSubmissionError.business(code: code, message: "私信\(kind)上传失败（HTTP \(code)）。")
        }
        return error
    }
}
