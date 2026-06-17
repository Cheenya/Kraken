import Foundation

public struct KrakenDesktopSimulator: Sendable {
    private let now: @Sendable () -> Date
    private let makeId: @Sendable () -> String

    public init(
        now: @escaping @Sendable () -> Date = { Date() },
        makeId: @escaping @Sendable () -> String = { UUID().uuidString }
    ) {
        self.now = now
        self.makeId = makeId
    }

    public func makeInitialState() -> KrakenDesktopState {
        let current = now()
        let relationships = [
            Relationship(
                relationshipId: "rel-xiaomi",
                peerDisplayName: "Xiaomi тестовый",
                peerFingerprint: "A17C9E2048F0DA11",
                state: .active,
                cryptoProfileId: "standard-reviewed-primitives-v1",
                admissionDecisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                updatedAt: current
            ),
            Relationship(
                relationshipId: "rel-samsung",
                peerDisplayName: "Samsung лабораторный",
                peerFingerprint: "C0D3A911E80477B2",
                state: .pendingHandshake,
                cryptoProfileId: "experimental-adamova-lc32-prime-offsets-v1",
                admissionDecisionHash: "sha256:desktop-adamova-demo",
                updatedAt: current
            ),
        ]
        let routes = relationships.map { relationship in
            PeerRouteSnapshot(
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                kind: relationship.state == .active ? .directLan : .none,
                transportId: relationship.state == .active ? "lan-nsd-tcp" : nil,
                bandwidthClass: relationship.state == .active ? .high : .none,
                hopCount: relationship.state == .active ? 1 : nil,
                lastSeenAt: relationship.state == .active ? current : nil
            )
        }
        let admission = AdmissionResult(
            profileId: "standard-reviewed-primitives-v1",
            decision: .notApplicableStandardProfile,
            decisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            nativeBackendVersion: "not-applicable-standard-profile",
            riskFlags: [],
            evaluatedAt: current
        )
        return KrakenDesktopState(
            localIdentity: nil,
            relationships: relationships,
            messages: [
                LocalMessage(
                    messageId: "msg-welcome",
                    relationshipId: "rel-xiaomi",
                    peerFingerprint: "A17C9E2048F0DA11",
                    direction: .incoming,
                    status: .deliveredToPeer,
                    body: "Kraken Desktop подключён.",
                    createdAt: current,
                    updatedAt: current
                ),
            ],
            routes: routes,
            admissionResult: admission,
            lastEvent: "Kraken Desktop инициализирован"
        )
    }

    public func createIdentity(in state: KrakenDesktopState, displayName: String) -> KrakenDesktopState {
        var next = state
        let current = now()
        let normalizedName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        next.localIdentity = LocalIdentity(
            identityId: "identity-\(makeId())",
            displayName: normalizedName.isEmpty ? "Kraken Desktop" : normalizedName,
            publicKeyEncoded: "desktop-public-key-\(makeId().prefix(8))",
            privateKeyReference: "macos-keychain-placeholder",
            fingerprint: String(makeId().replacingOccurrences(of: "-", with: "").prefix(16)).uppercased(),
            createdAt: current
        )
        next.lastEvent = "Создана локальная личность"
        return next
    }

    public func importPeer(in state: KrakenDesktopState, name: String) -> KrakenDesktopState {
        var next = state
        let current = now()
        let peerName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let fingerprint = String(makeId().replacingOccurrences(of: "-", with: "").prefix(16)).uppercased()
        next.relationships.insert(
            Relationship(
                relationshipId: "rel-\(makeId())",
            peerDisplayName: peerName.isEmpty ? "Новое устройство" : peerName,
                peerFingerprint: fingerprint,
                state: .pendingHandshake,
                cryptoProfileId: "standard-reviewed-primitives-v1",
                admissionDecisionHash: "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                updatedAt: current
            ),
            at: 0
        )
        next.routes.insert(
            PeerRouteSnapshot(
                relationshipId: next.relationships[0].relationshipId,
                peerFingerprint: fingerprint,
                kind: .none,
                transportId: nil,
                bandwidthClass: .none,
                hopCount: nil,
                lastSeenAt: nil
            ),
            at: 0
        )
        next.lastEvent = "устройство добавлено для офлайн-рукопожатия"
        return next
    }

    public func activateRelationship(in state: KrakenDesktopState, relationshipId: String) -> KrakenDesktopState {
        var next = state
        let current = now()
        next.relationships = next.relationships.map { relationship in
            guard relationship.relationshipId == relationshipId else { return relationship }
            var updated = relationship
            updated.state = .active
            updated.updatedAt = current
            return updated
        }
        next.routes = next.routes.map { route in
            guard route.relationshipId == relationshipId else { return route }
            return PeerRouteSnapshot(
                relationshipId: route.relationshipId,
                peerFingerprint: route.peerFingerprint,
                kind: .directLan,
                transportId: "lan-nsd-tcp",
                bandwidthClass: .high,
                hopCount: 1,
                lastSeenAt: current
            )
        }
        next.lastEvent = "Связь с устройством активирована"
        return next
    }

    public func sendMessage(in state: KrakenDesktopState, relationshipId: String, body: String) -> KrakenDesktopState {
        guard let relationship = state.relationships.first(where: { $0.relationshipId == relationshipId }) else {
            var failed = state
            failed.lastEvent = "Сообщение не отправлено: устройство не найдено"
            return failed
        }
        guard relationship.state.isMessageCapable else {
            var failed = state
            failed.lastEvent = "Сообщение не отправлено: связь с устройством не активна"
            return failed
        }
        var next = state
        let current = now()
        let route = state.routes.first(where: { $0.relationshipId == relationshipId })
        let status: MessageStatus = route?.kind == PeerRouteKind.none ? .readyForTransport : .sentToTransport
        next.messages.append(
            LocalMessage(
                messageId: "msg-\(makeId())",
                relationshipId: relationship.relationshipId,
                peerFingerprint: relationship.peerFingerprint,
                direction: .outgoing,
                status: status,
                body: body.trimmingCharacters(in: .whitespacesAndNewlines),
                createdAt: current,
                updatedAt: current
            )
        )
        next.lastEvent = status == .sentToTransport
            ? "Сообщение передано транспортному симулятору"
            : "Сообщение ожидает доступный маршрут"
        return next
    }

    public func confirmLatestDelivery(in state: KrakenDesktopState, relationshipId: String) -> KrakenDesktopState {
        var next = state
        let current = now()
        guard let index = next.messages.lastIndex(where: {
            $0.relationshipId == relationshipId && $0.direction == .outgoing
        }) else {
            next.lastEvent = "Нет исходящего сообщения для подтверждения"
            return next
        }
        next.messages[index].status = .deliveredToPeer
        next.messages[index].updatedAt = current
        next.lastEvent = "Подтверждение доставки применено к последнему сообщению"
        return next
    }

    public func cycleRoute(in state: KrakenDesktopState, relationshipId: String) -> KrakenDesktopState {
        var next = state
        let current = now()
        next.routes = next.routes.map { route in
            guard route.relationshipId == relationshipId else { return route }
            let nextKind: PeerRouteKind = switch route.kind {
            case .none: .directBle
            case .directBle: .directLan
            case .directLan: .routedMesh
            case .routedMesh: .none
            }
            return PeerRouteSnapshot(
                relationshipId: route.relationshipId,
                peerFingerprint: route.peerFingerprint,
                kind: nextKind,
                transportId: KrakenFormatters.routeTransportId(for: nextKind),
                bandwidthClass: KrakenFormatters.bandwidth(for: nextKind),
                hopCount: nextKind == .none ? nil : (nextKind == .routedMesh ? 2 : 1),
                lastSeenAt: nextKind == .none ? nil : current
            )
        }
        next.lastEvent = "Маршрут устройства обновлён"
        return next
    }

    public func evaluateAdmission(in state: KrakenDesktopState, experimental: Bool) -> KrakenDesktopState {
        var next = state
        let current = now()
        if experimental {
            next.admissionResult = AdmissionResult(
                profileId: "experimental-adamova-lc32-prime-offsets-v1",
                decision: .accept,
                decisionHash: "sha256:desktop-adamova-accept:v1",
                nativeBackendVersion: "desktop-simulated-adamova-stage-a",
                riskFlags: [],
                evaluatedAt: current
            )
            next.lastEvent = "Экспериментальный профиль Adamova принят"
        } else {
            next.admissionResult = AdmissionResult(
                profileId: "experimental-adamova-risk-demo-v1",
                decision: .rejectSmallTorsionRisk,
                decisionHash: "sha256:desktop-adamova-reject-small-torsion:v1",
                nativeBackendVersion: "desktop-simulated-adamova-stage-a",
                riskFlags: ["rational_2_torsion"],
                evaluatedAt: current
            )
            next.lastEvent = "Рискованный профиль Adamova отклонён"
        }
        return next
    }
}
