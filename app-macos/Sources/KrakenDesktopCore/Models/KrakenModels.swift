import Foundation

public struct LocalIdentity: Codable, Equatable, Sendable {
    public var identityId: String
    public var displayName: String
    public var publicKeyEncoded: String
    public var privateKeyReference: String
    public var fingerprint: String
    public var createdAt: Date

    public init(
        identityId: String,
        displayName: String,
        publicKeyEncoded: String,
        privateKeyReference: String,
        fingerprint: String,
        createdAt: Date
    ) {
        self.identityId = identityId
        self.displayName = displayName
        self.publicKeyEncoded = publicKeyEncoded
        self.privateKeyReference = privateKeyReference
        self.fingerprint = fingerprint
        self.createdAt = createdAt
    }
}

public enum RelationshipState: String, Codable, CaseIterable, Sendable {
    case pendingImport = "PENDING_IMPORT"
    case pendingHandshake = "PENDING_HANDSHAKE"
    case active = "ACTIVE"
    case unlinkRequested = "UNLINK_REQUESTED"
    case unlinked = "UNLINKED"
    case blockedByPeer = "BLOCKED_BY_PEER"
    case rejoinRequired = "REJOIN_REQUIRED"

    public var title: String {
        switch self {
        case .pendingImport: "ожидает импорта"
        case .pendingHandshake: "ожидает рукопожатия"
        case .active: "активен"
        case .unlinkRequested: "разрыв запрошен"
        case .unlinked: "отвязан"
        case .blockedByPeer: "заблокирован"
        case .rejoinRequired: "нужен повторный вход"
        }
    }

    public var isMessageCapable: Bool { self == .active }
}

public struct Relationship: Identifiable, Codable, Equatable, Sendable {
    public var id: String { relationshipId }
    public var relationshipId: String
    public var peerDisplayName: String
    public var peerFingerprint: String
    public var state: RelationshipState
    public var cryptoProfileId: String
    public var admissionDecisionHash: String
    public var profilePolicyVersion: Int?
    public var updatedAt: Date

    public init(
        relationshipId: String,
        peerDisplayName: String,
        peerFingerprint: String,
        state: RelationshipState,
        cryptoProfileId: String,
        admissionDecisionHash: String,
        profilePolicyVersion: Int? = 1,
        updatedAt: Date
    ) {
        self.relationshipId = relationshipId
        self.peerDisplayName = peerDisplayName
        self.peerFingerprint = peerFingerprint
        self.state = state
        self.cryptoProfileId = cryptoProfileId
        self.admissionDecisionHash = admissionDecisionHash
        self.profilePolicyVersion = profilePolicyVersion
        self.updatedAt = updatedAt
    }
}

public enum MessageDirection: String, Codable, Sendable {
    case outgoing = "OUTGOING"
    case incoming = "INCOMING"
}

public enum MessageStatus: String, Codable, CaseIterable, Sendable {
    case localPending = "LOCAL_PENDING"
    case readyForTransport = "READY_FOR_TRANSPORT"
    case sentToTransport = "SENT_TO_TRANSPORT"
    case deliveredToPeer = "DELIVERED_TO_PEER"
    case failed = "FAILED"

    public var title: String {
        switch self {
        case .localPending: "локально"
        case .readyForTransport: "готово к маршруту"
        case .sentToTransport: "передано транспорту"
        case .deliveredToPeer: "доставлено"
        case .failed: "ошибка"
        }
    }
}

public struct LocalMessage: Identifiable, Codable, Equatable, Sendable {
    public var id: String { messageId }
    public var messageId: String
    public var relationshipId: String
    public var peerFingerprint: String
    public var direction: MessageDirection
    public var status: MessageStatus
    public var body: String
    public var createdAt: Date
    public var updatedAt: Date

    public init(
        messageId: String,
        relationshipId: String,
        peerFingerprint: String,
        direction: MessageDirection,
        status: MessageStatus,
        body: String,
        createdAt: Date,
        updatedAt: Date
    ) {
        self.messageId = messageId
        self.relationshipId = relationshipId
        self.peerFingerprint = peerFingerprint
        self.direction = direction
        self.status = status
        self.body = body
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public enum PeerRouteKind: String, Codable, CaseIterable, Sendable {
    case none = "NONE"
    case directBle = "DIRECT_BLE"
    case directLan = "DIRECT_LAN"
    case routedMesh = "ROUTED_MESH"

    public var title: String {
        switch self {
        case .none: "нет маршрута"
        case .directBle: "Bluetooth напрямую"
        case .directLan: "Wi-Fi/LAN напрямую"
        case .routedMesh: "через ретранслятор"
        }
    }
}

public enum BandwidthClass: String, Codable, Sendable {
    case none = "NONE"
    case low = "LOW"
    case medium = "MEDIUM"
    case high = "HIGH"
}

public struct PeerRouteSnapshot: Identifiable, Codable, Equatable, Sendable {
    public var id: String { relationshipId }
    public var relationshipId: String
    public var peerFingerprint: String
    public var kind: PeerRouteKind
    public var transportId: String?
    public var bandwidthClass: BandwidthClass
    public var hopCount: Int?
    public var lastSeenAt: Date?

    public init(
        relationshipId: String,
        peerFingerprint: String,
        kind: PeerRouteKind,
        transportId: String?,
        bandwidthClass: BandwidthClass,
        hopCount: Int?,
        lastSeenAt: Date?
    ) {
        self.relationshipId = relationshipId
        self.peerFingerprint = peerFingerprint
        self.kind = kind
        self.transportId = transportId
        self.bandwidthClass = bandwidthClass
        self.hopCount = hopCount
        self.lastSeenAt = lastSeenAt
    }
}

public enum AdamovaAdmissionDecision: String, Codable, Sendable {
    case accept = "ACCEPT"
    case rejectSingular = "REJECT_SINGULAR"
    case rejectSmallTorsionRisk = "REJECT_SMALL_TORSION_RISK"
    case referenceValidationRequired = "REFERENCE_VALIDATION_REQUIRED"
    case sizeGuarded = "SIZE_GUARDED"
    case nativeUnavailable = "NATIVE_UNAVAILABLE"
    case notApplicableStandardProfile = "NOT_APPLICABLE_STANDARD_PROFILE"

    public var title: String {
        switch self {
        case .accept: "принят"
        case .rejectSingular: "отклонён: сингулярность"
        case .rejectSmallTorsionRisk: "отклонён: риск кручения"
        case .referenceValidationRequired: "нужна сверка"
        case .sizeGuarded: "ограничен размером"
        case .nativeUnavailable: "нативное ядро недоступно"
        case .notApplicableStandardProfile: "стандартный профиль"
        }
    }

    public var acceptedForPacketPolicy: Bool {
        self == .accept || self == .notApplicableStandardProfile
    }
}

public struct AdmissionResult: Codable, Equatable, Sendable {
    public var profileId: String
    public var decision: AdamovaAdmissionDecision
    public var decisionHash: String
    public var nativeBackendVersion: String
    public var riskFlags: [String]
    public var evaluatedAt: Date

    public init(
        profileId: String,
        decision: AdamovaAdmissionDecision,
        decisionHash: String,
        nativeBackendVersion: String,
        riskFlags: [String],
        evaluatedAt: Date
    ) {
        self.profileId = profileId
        self.decision = decision
        self.decisionHash = decisionHash
        self.nativeBackendVersion = nativeBackendVersion
        self.riskFlags = riskFlags
        self.evaluatedAt = evaluatedAt
    }
}

public struct KrakenDesktopState: Codable, Equatable, Sendable {
    public var localIdentity: LocalIdentity?
    public var relationships: [Relationship]
    public var messages: [LocalMessage]
    public var routes: [PeerRouteSnapshot]
    public var admissionResult: AdmissionResult
    public var lastEvent: String

    public init(
        localIdentity: LocalIdentity?,
        relationships: [Relationship],
        messages: [LocalMessage],
        routes: [PeerRouteSnapshot],
        admissionResult: AdmissionResult,
        lastEvent: String
    ) {
        self.localIdentity = localIdentity
        self.relationships = relationships
        self.messages = messages
        self.routes = routes
        self.admissionResult = admissionResult
        self.lastEvent = lastEvent
    }
}
