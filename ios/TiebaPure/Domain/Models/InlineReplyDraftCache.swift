import Foundation

// shortcut: Input-bar drafts last for this app session; add separate persistence if restart recovery is requested.
// Never read, replace, or delete full-editor drafts on disk.
@MainActor
final class InlineReplyDraftCache {
    private var session: AccountSessionIdentity?
    private var drafts: [String: ContentSubmissionRequest] = [:]

    func draft(account: Account, target: ContentSubmissionTarget) -> ContentSubmissionRequest {
        if session != account.sessionIdentity {
            drafts.removeAll()
            session = account.sessionIdentity
        }
        return drafts[target.draftKey] ?? ContentSubmissionRequest(target: target, title: "", body: "", images: [])
    }

    func finish(_ request: ContentSubmissionRequest, session: AccountSessionIdentity) {
        guard self.session == session, drafts[request.target.draftKey] == request else { return }
        drafts.removeValue(forKey: request.target.draftKey)
    }

    func update(_ request: ContentSubmissionRequest, session: AccountSessionIdentity) {
        guard self.session == session else { return }
        drafts[request.target.draftKey] = request
    }
}
