import Foundation

enum AccountLikesRequestPolicy {
    static let pageSize = 20
    static func fields(account: Account, page: Int, filter: AccountLikesFilter) throws -> [String: String] {
        let page = try TiebaRequestValuePolicy.signedPage(page)
        return ["BDUSS": account.bduss, "stoken": account.stoken, "_client_version": TiebaClientVersion.v12.rawValue,
                "tab_id": String(filter.rawValue), "pn": String(page), "rn": String(pageSize)]
    }

    static func validate(response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, http.url == TiebaEndpoint.accountLikes.url else {
            throw TiebaHTTPError.invalidResponse
        }
        guard (200..<300).contains(http.statusCode) else { throw TiebaHTTPError.badStatus(code: http.statusCode, body: Data()) }
        guard ["application/json", "text/json", "application/x-javascript", "application/javascript", "text/javascript"].contains(http.mimeType?.lowercased() ?? "") else {
            throw TiebaHTTPError.invalidMIMEType(http.mimeType)
        }
    }
}

struct AccountLikesResponseDTO: Decodable {
    var errorCode: Int
    var errorMessage: String
    var items: [AccountLikedContent]
    var hasMore: Bool

    private enum CodingKeys: String, CodingKey { case errorCode = "error_code", errorMessage = "error_msg", data }
    private struct DataDTO: Decodable {
        var threads: [ThreadDTO]
        var hasMore: Bool
        enum CodingKeys: String, CodingKey { case threads = "thread_list", hasMore = "has_more" }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            let flag = try c.likesRequiredInt(forKey: .hasMore)
            guard flag == 0 || flag == 1 else { throw DecodingError.dataCorruptedError(forKey: .hasMore, in: c, debugDescription: "Invalid pagination flag") }
            hasMore = flag == 1
            threads = hasMore || c.contains(.threads) ? try c.decode([ThreadDTO].self, forKey: .threads) : []
        }
    }
    private struct UserDTO: Decodable {
        var name: String
        enum CodingKeys: String, CodingKey { case name, nameShow = "name_show", showNickname = "show_nickname" }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = [c.likesString(forKey: .nameShow), c.likesString(forKey: .showNickname), c.likesString(forKey: .name)].compactMap { $0 }.first { !$0.isEmpty } ?? "贴吧用户"
        }
    }
    private struct ForumDTO: Decodable {
        var id: Int64?
        var name: String
        enum CodingKeys: String, CodingKey { case id, name }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = c.likesString(forKey: .id).flatMap(Int64.init).flatMap { $0 > 0 ? $0 : nil }
            name = c.likesString(forKey: .name) ?? ""
        }
    }
    private struct ContentDTO: Decodable {
        var blocks: [ContentBlock]
        enum CodingKeys: String, CodingKey { case type, text, c, src, link, uid }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            let type = Int(c.likesString(forKey: .type) ?? "0") ?? 0
            let text = c.likesString(forKey: .text) ?? ""
            switch type {
            case 2:
                let code = c.likesString(forKey: .c).flatMap { $0.isEmpty ? nil : $0 } ?? text
                blocks = code.isEmpty ? [] : [.emoticon(code: code)]
            case 3, 20: blocks = [.text("[图片]")]
            case 5: blocks = [.text("[视频]")]
            case 10: blocks = [.text("[语音]")]
            case 4: blocks = [.mention(userID: c.likesString(forKey: .uid).flatMap(Int64.init), text: text)]
            case 1: blocks = [.link(title: text, url: TiebaURL.make(c.likesString(forKey: .link)))]
            default: blocks = TiebaEmoticon.blocks(from: text)
            }
        }
    }
    private struct PostDTO: Decodable {
        var id: UInt64?
        var parentID: UInt64?
        var user: UserDTO?
        var blocks: [ContentBlock]
        enum CodingKeys: String, CodingKey { case id, parentID = "quote_id", user = "author", content }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = c.likesString(forKey: .id).flatMap(UInt64.init).flatMap { $0 > 0 && $0 <= UInt64(Int64.max) ? $0 : nil }
            parentID = c.likesString(forKey: .parentID).flatMap(UInt64.init).flatMap { $0 > 0 && $0 <= UInt64(Int64.max) ? $0 : nil }
            user = try c.decodeIfPresent(UserDTO.self, forKey: .user)
            blocks = try c.decodeIfPresent([ContentDTO].self, forKey: .content)?.flatMap(\.blocks) ?? []
        }
    }
    private struct ThreadDTO: Decodable {
        var item: AccountLikedContent?
        enum CodingKeys: String, CodingKey {
            case id, tid, threadID = "thread_id", title, forum = "forum_info", forumID = "fid", forumName = "fname", user = "author"
            case post = "top_agree_post", content = "first_post_content", abstract
        }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            guard let tid = [c.likesString(forKey: .id), c.likesString(forKey: .tid), c.likesString(forKey: .threadID)].compactMap({ $0.flatMap(Int64.init) }).first(where: { $0 > 0 }) else { item = nil; return }
            let forum = try c.decodeIfPresent(ForumDTO.self, forKey: .forum)
            let author = try c.decodeIfPresent(UserDTO.self, forKey: .user)
            let post = try c.decodeIfPresent(PostDTO.self, forKey: .post)
            guard post == nil || post?.id != nil else { item = nil; return }
            let mainContent = try c.decodeIfPresent([ContentDTO].self, forKey: .content)?.flatMap(\.blocks) ?? []
            let abstract = (try? c.decode([ContentDTO].self, forKey: .abstract).flatMap(\.blocks)) ?? c.likesString(forKey: .abstract).map(TiebaEmoticon.blocks(from:)) ?? []
            item = AccountLikedContent(threadID: tid,
                forumID: forum?.id ?? c.likesString(forKey: .forumID).flatMap(Int64.init).flatMap { $0 > 0 ? $0 : nil },
                forumName: forum?.name ?? c.likesString(forKey: .forumName) ?? "",
                title: c.likesString(forKey: .title) ?? "无标题帖子",
                authorDisplayName: post?.user?.name ?? author?.name ?? "贴吧用户",
                blocks: post?.blocks ?? (mainContent.isEmpty ? abstract : mainContent), postID: post?.id, parentPostID: post?.parentID)
        }
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        errorCode = try c.likesRequiredInt(forKey: .errorCode)
        errorMessage = c.likesString(forKey: .errorMessage) ?? ""
        if errorCode == 0 {
            let data = try c.decode(DataDTO.self, forKey: .data)
            items = data.threads.compactMap(\.item)
            hasMore = data.hasMore
        } else { items = []; hasMore = false }
    }
}

extension TiebaAPI {
    func accountLikes(account: Account, page: Int, filter: AccountLikesFilter) async throws -> AccountLikedContentPage {
        let data = try await client.postFormData(.accountLikes,
            fields: AccountLikesRequestPolicy.fields(account: account, page: page, filter: filter),
            headers: requestBuilder.officialHeaders(baiduID: account.baiduID, clientVersion: TiebaClientVersion.v12.rawValue),
            signingSecret: "tiebaclient!!!", responseValidator: { try AccountLikesRequestPolicy.validate(response: $0) })
        let response = try JSONDecoder().decode(AccountLikesResponseDTO.self, from: data)
        try TiebaResponseValidator.validate(code: response.errorCode, message: response.errorMessage)
        return AccountLikedContentPage(items: response.items, currentPage: page, hasMore: response.hasMore)
    }
}

private extension KeyedDecodingContainer {
    func likesString(forKey key: Key) -> String? {
        if let value = try? decode(String.self, forKey: key) { return value }
        if let value = try? decode(Int64.self, forKey: key) { return String(value) }
        if let value = try? decode(UInt64.self, forKey: key) { return String(value) }
        return nil
    }
    func likesRequiredInt(forKey key: Key) throws -> Int {
        guard let value = likesString(forKey: key).flatMap(Int.init) else {
            throw DecodingError.dataCorruptedError(forKey: key, in: self, debugDescription: "Missing or invalid integer")
        }
        return value
    }
}
