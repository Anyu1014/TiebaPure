import SwiftUI

extension AccountLikedContent {
    var readerRoute: ReaderSplitThreadRoute {
        let destination: ThreadDetailInitialDestination?
        if let parentPostID, let postID { destination = .subpost(parentPostID: parentPostID, subpostID: postID) }
        else { destination = postID == nil ? .mainPost : nil }
        return ReaderSplitThreadRoute(threadID: threadID, forumID: forumID,
            initialPostID: parentPostID ?? postID, initialDestination: destination)
    }
}

struct AccountLikesView: View {
    let account: Account?
    var openThreadInParent: ((ReaderSplitThreadRoute) -> Void)?
    @EnvironmentObject private var environment: AppEnvironment
    @StateObject private var loader = AccountLikesLoader()
    @State private var filter = AccountLikesFilter.all
    @State private var activeRoute: ReaderSplitThreadRoute?

    private struct LoadIdentity: Hashable {
        var session: AccountSessionIdentity?
        var filter: AccountLikesFilter
    }

    var body: some View {
        VStack(spacing: 0) {
            if account != nil {
                Picker("点赞类型", selection: $filter) {
                    ForEach(AccountLikesFilter.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
                .padding()
                .accessibilityIdentifier("account-likes-filter")
            }
            content
        }
        .navigationTitle("我的点赞")
        .navigationBarTitleDisplayMode(.inline)
        .background(TiebaPureTheme.ColorToken.readerGroupedBackground)
        .task(id: LoadIdentity(session: account?.sessionIdentity, filter: filter)) {
            loader.prepare(account: account, filter: filter)
            if !loader.didLoad { await loader.reload(account: account, filter: filter, api: environment.api) }
        }
        .onChange(of: account?.sessionIdentity) { _ in loader.prepare(account: account, filter: filter) }
        .onChange(of: filter) { _ in loader.prepare(account: account, filter: filter) }
        .onDisappear { loader.cancel() }
        .navigationDestination(isPresented: Binding(get: { activeRoute != nil }, set: { if !$0 { activeRoute = nil } })) {
            if let route = activeRoute {
                ThreadDetailView(account: account, threadID: route.threadID, forumID: route.forumID,
                    initialPostID: route.initialPostID, initialDestination: route.initialDestination)
                .interactiveNavigationPopStateSync { activeRoute = nil }
            }
        }
        .fullScreenInteractiveNavigationPop()
    }

    @ViewBuilder private var content: some View {
        if account == nil {
            ReaderStateView(kind: .empty, title: "登录后查看我的点赞", message: "查看贴吧账号中的云端点赞内容。", systemImage: "hand.thumbsup")
        } else if loader.isLoading && loader.items.isEmpty {
            ReaderStateView.loading("正在加载我的点赞")
        } else if let error = loader.errorMessage, loader.items.isEmpty {
            ReaderStateView.error(message: error) { Task { await reload() } }
        } else {
            List {
                if loader.items.isEmpty && loader.didLoad && !loader.hasMore {
                    Text("暂无点赞内容").foregroundStyle(.secondary)
                }
                ForEach(loader.items) { entry in
                    Button {
                        if let openThreadInParent { openThreadInParent(entry.readerRoute) } else { activeRoute = entry.readerRoute }
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(entry.kindTitle).font(.caption).foregroundStyle(.secondary)
                                Spacer()
                                Text(entry.forumName.isEmpty ? "贴吧" : entry.forumName.hasSuffix("吧") ? entry.forumName : "\(entry.forumName)吧").font(.caption).foregroundStyle(.secondary)
                            }
                            Text(entry.title).font(.headline).foregroundStyle(.primary).lineLimit(2)
                            if !entry.preview.isEmpty { Text(entry.preview).font(.body).foregroundStyle(.primary).lineLimit(4) }
                            Text(entry.authorDisplayName).font(.caption).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("account-like-\(entry.id)")
                }
                if let error = loader.errorMessage {
                    InlineLoadErrorView(message: error) { Task { await loader.loadMore(account: account, api: environment.api) } }
                } else if loader.hasMore {
                    HStack { Spacer(); ProgressView(); Spacer() }
                        .task(id: loader.nextPage) { await loader.loadMore(account: account, api: environment.api) }
                }
                if !loader.hasMore {
                    Text("当前为2023年1月1日至今的云端点赞内容")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .listStyle(.insetGrouped)
            .refreshable { await reload() }
        }
    }

    private func reload() async {
        await loader.reload(account: account, filter: filter, api: environment.api)
    }
}
