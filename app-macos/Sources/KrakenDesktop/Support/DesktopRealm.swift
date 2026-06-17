import Foundation

struct DesktopRealm: Identifiable, Equatable {
    enum State: String {
        case active
        case pendingReview
        case archived

        var title: String {
            switch self {
            case .active: "активен"
            case .pendingReview: "ожидает проверки"
            case .archived: "архив"
            }
        }
    }

    var id: String { realmId }
    var realmId: String
    var name: String
    var state: State
    var memberCount: Int
    var pendingRequests: Int
    var updatedAt: Date
}
