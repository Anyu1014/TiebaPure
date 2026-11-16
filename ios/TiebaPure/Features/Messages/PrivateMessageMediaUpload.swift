import Foundation
import CryptoKit

enum PrivateMessageMediaUpload {
    static func recordDiagnostic(_ kind: String, _ detail: String) {
        guard ["图片", "语音", "播放", "目标认证"].contains(kind),
              let directory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        let url = directory.appendingPathComponent("private-media-diagnostic-\(kind).txt")
        try? Data(detail.utf8).write(to: url, options: .atomic)
    }

    @MainActor
    static func targetHostDiagnostic(account: Account, session: URLSession) async -> String {
        var cookieStatus = "unknown"
        do {
            var request = URLRequest(url: TiebaEndpoint.protobufBase.appending(path: "/dc/common/tbs"))
            request.setValue(account.minimalCookieHeader, forHTTPHeaderField: "Cookie")
            let (data, rawResponse) = try await BoundedURLSession(session: session).data(for: request, maximumBytes: 65_536)
            if let response = rawResponse as? HTTPURLResponse, response.statusCode == 200,
               ["application/json", "text/html"].contains(response.mimeType?.lowercased() ?? ""),
               let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let value = json["is_login"] {
                switch String(describing: value).lowercased() {
                case "1", "true": cookieStatus = "true"
                case "0", "false": cookieStatus = "false"
                default: break
                }
            }
        } catch is CancellationError { return "cancelled" } catch {}
        guard !Task.isCancelled else { return "cancelled" }
        var postStatus = "unknown"
        do {
            let api = TiebaAPI(client: TiebaHTTPClient(session: session, maximumResponseBytes: 65_536))
            _ = try await api.strictlyRefreshedPostingTBS(for: account)
            postStatus = "true"
        } catch is CancellationError { return "cancelled" }
        catch let error as ContentSubmissionError {
            if error == .sessionExpired || error == .notLoggedIn { postStatus = "false" }
            if case .business(code: 1, message: _) = error { postStatus = "false" }
        } catch {}
        return "host-cookie-login=\(cookieStatus)\nhost-post-login=\(postStatus)"
    }

    @MainActor
    static func diagnoseTargetHostIfNeeded(_ error: Error, account: Account, injectedSession: URLSession?) async {
        guard injectedSession == nil, !Task.isCancelled,
              case .business(code: 1, message: _) = error as? ContentSubmissionError else { return }
        let configuration = VoiceAudioClient.makeConfiguration()
        configuration.timeoutIntervalForRequest = 8
        configuration.timeoutIntervalForResource = 16
        let session = SecureRemoteURLSession.make(configuration: configuration, redirectScope: .baiduHTTPS)
        defer { session.invalidateAndCancel() }
        let detail = await targetHostDiagnostic(account: account, session: session)
        if !Task.isCancelled { recordDiagnostic("目标认证", detail) }
    }

    static func diagnosticMessageFlags(_ message: String) -> String {
        let text = message.lowercased()
        let categories = [
            ("signature", ["签名", "sign"]),
            ("authentication", ["登录", "登陆", "认证", "鉴权", "凭证", "token", "bduss"]),
            ("bduss", ["bduss"]),
            ("stoken", ["stoken"]),
            ("expired", ["过期", "失效", "expired"]),
            ("missing", ["缺少", "缺失", "为空", "missing"]),
            ("invalid", ["无效", "非法", "错误", "invalid"]),
            ("parameter", ["参数", "param"]),
            ("permission", ["权限", "禁止", "拒绝", "permission"]),
            ("rate-limit", ["频繁", "频率", "次数", "limit"]),
            ("server", ["系统", "服务器", "server"]),
            ("network", ["网络", "network"]),
            ("upload", ["上传", "upload"])
        ]
        let flags = categories.compactMap { category, words in words.contains(where: text.contains) ? category : nil }
        return flags.isEmpty ? "other" : flags.joined(separator: ",")
    }

    @MainActor
    static func requestContext(account: Account, clientID: String, bootstrap: TiebaPostingBootstrapResult? = nil) -> (fields: [String: String], headers: [String: String]) {
        let version = TiebaContentSubmissionRequestFactory.clientVersion
        var builder = TiebaRequestBuilder.live()
        let cuid = bootstrap?.identity.cuidGalaxy2 ?? clientID
        builder.clientID = bootstrap?.clientID ?? clientID
        var fields = builder.officialCommonFields(bduss: account.bduss, baiduID: account.baiduID, clientVersion: version)
        fields["stoken"] = account.stoken
        fields["cuid"] = cuid
        fields["cuid_galaxy2"] = cuid
        if let bootstrap {
            fields["c3_aid"] = bootstrap.identity.c3AID
            if !bootstrap.sampleID.isEmpty { fields["sample_id"] = bootstrap.sampleID }
            if !bootstrap.zID.isEmpty { fields["z_id"] = bootstrap.zID }
        }
        fields["support_image"] = "jepgwebp"
        fields["scr_w"] = String(builder.screenWidth)
        fields["scr_h"] = String(builder.screenHeight)
        fields["scr_dip"] = String(builder.screenScale)
        var headers = builder.officialHeaders(clientVersion: version)
        headers["User-Agent"] = "tieba/\(version) skin/default"
        headers["cuid"] = cuid
        headers["cuid_galaxy2"] = cuid
        headers["Cookie"] = "CUID=\(cuid); ka=open; \(account.minimalCookieHeader)"
        return (fields, headers)
    }

    static func imageUploadCookie(account: Account) -> String {
        var cookies = ["ka=open"]
        if let baiduID = account.baiduID, !baiduID.isEmpty { cookies.append("BAIDUID=\(baiduID)") }
        return cookies.joined(separator: "; ")
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
        let bootstrap = try await injectedSession == nil ? TiebaPostingBootstrap.live().bootstrap(bduss: account.bduss) : nil
        try Task.checkCancellation()
        let (common, headers) = await requestContext(account: account, clientID: clientID, bootstrap: bootstrap)
        var imageHeaders = headers
        imageHeaders["Cookie"] = imageUploadCookie(account: account)
        var result: Data?
        for (index, offset) in stride(from: 0, to: image.data.count, by: chunkSize).enumerated() {
            try Task.checkCancellation()
            let end = min(offset + chunkSize, image.data.count)
            let form = MultipartFormData(boundary: "TiebaPurePrivateImage-\(UUID().uuidString)")
            var fields = common.merging(["resourceId": resource, "chunkNo": String(index + 1), "isFinish": end == image.data.count ? "1" : "0", "size": String(image.data.count), "width": String(metadata.pixelWidth), "height": String(metadata.pixelHeight), "groupId": String(groupID), "alt": "json", "is_bjh": "0", "saveOrigin": "1", "pic_water_type": "3"]) { _, new in new }
            fields["sign"] = TiebaFormSigner.sign(fields: fields, secret: "tiebaclient!!!")
            for (key, value) in fields.sorted(by: { $0.key < $1.key }) { form.addField(name: key, value: value) }
            form.addFile(name: "chunk", filename: "file", contentType: nil, data: image.data.subdata(in: offset..<end))
            let raw = try await client.postRaw(.uploadPicture, body: form.finalize(), contentType: "multipart/form-data; boundary=\(form.boundary)", headers: imageHeaders)
            _ = try validatedResponse(raw, kind: "图片")
            result = raw
        }
        guard let result else { throw PrivateMessageError.invalidResponse }
        return try imageContent(result, width: metadata.pixelWidth, height: metadata.pixelHeight)
        } catch {
            await diagnoseTargetHostIfNeeded(error, account: account, injectedSession: injectedSession)
            throw uploadError(error, kind: "图片")
        }
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
            recordDiagnostic(kind, "response=invalid-json")
            throw ContentSubmissionError.unsupported(message: "私信\(kind)上传响应格式无法识别。")
        }
        let error = result["error"] as? [String: Any]
        guard let raw = error?["errno"] ?? result["error_code"], let code = Int(String(describing: raw)) else {
            recordDiagnostic(kind, "response=missing-status")
            throw ContentSubmissionError.unsupported(message: "私信\(kind)上传响应缺少有效状态码。")
        }
        let keys = ["error", "error_code", "error_msg", "errmsg", "info", "picInfo"].filter { result[$0] != nil }.joined(separator: ",")
        let message = (result["error_msg"] as? String) ?? (result["errmsg"] as? String) ?? (error?["errmsg"] as? String) ?? ""
        recordDiagnostic(kind, "response-code=\(code)\nresponse-known-fields=\(keys)\nmessage-category=\(diagnosticMessageFlags(message))\nmessage-length=\(message.count)")
        guard code == 0 else { throw ContentSubmissionError.business(code: code, message: "私信\(kind)上传未通过（错误码 \(code)）。") }
        return result
    }

    static func uploadError(_ error: Error, kind: String) -> Error {
        if case let TiebaHTTPError.badStatus(code, _) = error {
            recordDiagnostic(kind, "http-status=\(code)")
            return ContentSubmissionError.business(code: code, message: "私信\(kind)上传失败（HTTP \(code)）。")
        }
        return error
    }
}
