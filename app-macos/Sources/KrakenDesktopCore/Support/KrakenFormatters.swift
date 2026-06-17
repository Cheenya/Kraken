import Foundation

public enum KrakenFormatters {
    public static func compactFingerprint(_ value: String) -> String {
        guard value.count > 10 else { return value }
        return "\(value.prefix(4))...\(value.suffix(4))"
    }

    public static func routeTransportId(for kind: PeerRouteKind) -> String? {
        switch kind {
        case .none: nil
        case .directBle: "ble-gatt"
        case .directLan: "lan-nsd-tcp"
        case .routedMesh: "routed-mesh"
        }
    }

    public static func bandwidth(for kind: PeerRouteKind) -> BandwidthClass {
        switch kind {
        case .none: .none
        case .directBle: .low
        case .directLan: .high
        case .routedMesh: .low
        }
    }
}
