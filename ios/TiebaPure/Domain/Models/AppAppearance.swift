import SwiftUI

enum AppAppearance: String, CaseIterable, Identifiable, Sendable {
    case system
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system:
            return "跟随系统"
        case .light:
            return "浅色"
        case .dark:
            return "深色"
        }
    }

    var systemImage: String {
        switch self {
        case .system:
            return "circle.lefthalf.filled"
        case .light:
            return "sun.max"
        case .dark:
            return "moon.stars"
        }
    }

    var preferredColorScheme: ColorScheme? {
        switch self {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }
}

@MainActor
final class AppAppearanceStore: ObservableObject {
    nonisolated static let storageKey = "dev.infinityf4p.tiebapure.appearance"

    @Published private(set) var selection: AppAppearance
    @Published private(set) var bottomBarPosition: BottomBarPosition
    @Published private(set) var bottomBarShowsLabels: Bool
    @Published private(set) var bottomBarAutoCollapse: Bool

    private let defaults: UserDefaults
    private let key: String

    init(
        defaults: UserDefaults = .standard,
        key: String = AppAppearanceStore.storageKey
    ) {
        self.defaults = defaults
        self.key = key
        bottomBarPosition = BottomBarPosition(rawValue: defaults.string(forKey: key + ".tab-position") ?? "") ?? .left
        bottomBarShowsLabels = defaults.object(forKey: key + ".tab-labels") as? Bool ?? true
        bottomBarAutoCollapse = defaults.object(forKey: key + ".tab-auto-collapse") as? Bool ?? true

        if let rawValue = defaults.string(forKey: key),
           let storedSelection = AppAppearance(rawValue: rawValue) {
            selection = storedSelection
        } else {
            selection = .system
            if defaults.object(forKey: key) != nil {
                defaults.removeObject(forKey: key)
            }
        }
    }

    func select(_ appearance: AppAppearance) {
        guard appearance != selection else { return }

        if appearance == .system {
            defaults.removeObject(forKey: key)
        } else {
            defaults.set(appearance.rawValue, forKey: key)
        }
        selection = appearance
    }

    func setBottomBarPosition(_ position: BottomBarPosition) {
        defaults.set(position.rawValue, forKey: key + ".tab-position"); bottomBarPosition = position
    }
    func setBottomBarShowsLabels(_ shows: Bool) {
        defaults.set(shows, forKey: key + ".tab-labels"); bottomBarShowsLabels = shows
    }
    func setBottomBarAutoCollapse(_ enabled: Bool) {
        defaults.set(enabled, forKey: key + ".tab-auto-collapse"); bottomBarAutoCollapse = enabled
    }
    func reset() {
        defaults.removeObject(forKey: key)
        defaults.removeObject(forKey: key + ".tab-position"); defaults.removeObject(forKey: key + ".tab-labels")
        defaults.removeObject(forKey: key + ".tab-auto-collapse")
        selection = .system
        bottomBarPosition = .left; bottomBarShowsLabels = true
        bottomBarAutoCollapse = true
    }

    static func live() -> AppAppearanceStore {
        let store = AppAppearanceStore()

#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if arguments.contains("UITEST_RESET_APPEARANCE") {
            store.reset()
        }
#endif

        return store
    }
}
