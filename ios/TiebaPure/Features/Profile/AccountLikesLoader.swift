import Foundation

@MainActor
final class AccountLikesLoader: ObservableObject {
    @Published private(set) var items: [AccountLikedContent] = []
    @Published private(set) var isLoading = false
    @Published private(set) var didLoad = false
    @Published private(set) var errorMessage: String?
    @Published private(set) var hasMore = true
    @Published private(set) var nextPage = 1

    private var generation = 0
    private var session: AccountSessionIdentity?
    private var filter = AccountLikesFilter.all
    private var loadTask: Task<AccountLikedContentPage, Error>?

    func reload(account: Account?, filter: AccountLikesFilter, api: any TiebaAPIService) async {
        prepare(account: account, filter: filter)
        cancel()
        nextPage = 1
        hasMore = true
        await loadMore(account: account, api: api)
    }

    func prepare(account: Account?, filter: AccountLikesFilter) {
        guard session != account?.sessionIdentity || self.filter != filter else { return }
        reset()
        session = account?.sessionIdentity
        self.filter = filter
    }

    func loadMore(account: Account?, api: any TiebaAPIService) async {
        guard let account, account.sessionIdentity == session, !isLoading, hasMore else { return }
        let requestGeneration = generation
        let requestedPage = nextPage
        let requestedFilter = filter
        isLoading = true
        errorMessage = nil
        do {
            let task = Task { try await api.accountLikes(account: account, page: requestedPage, filter: requestedFilter) }
            loadTask = task
            let page = try await task.value
            guard requestGeneration == generation, account.sessionIdentity == session else { return }
            let previous = requestedPage == 1 ? [] : items
            var known = Set<String>()
            items = (previous + page.items).filter { known.insert($0.id).inserted }
            hasMore = page.hasMore
            nextPage = requestedPage + 1
        } catch is CancellationError {
            guard requestGeneration == generation else { return }
        } catch {
            guard requestGeneration == generation, account.sessionIdentity == session else { return }
            errorMessage = ReaderErrorMessage.message(for: error)
        }
        guard requestGeneration == generation else { return }
        loadTask = nil
        isLoading = false
        didLoad = true
    }

    func cancel() {
        loadTask?.cancel()
        loadTask = nil
        generation += 1
        isLoading = false
    }

    func reset() {
        cancel()
        items = []
        nextPage = 1
        hasMore = true
        didLoad = false
        errorMessage = nil
        session = nil
    }
}
