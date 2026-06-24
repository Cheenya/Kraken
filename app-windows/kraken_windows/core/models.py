from __future__ import annotations

from dataclasses import asdict, dataclass, is_dataclass
from datetime import UTC, datetime
from enum import Enum
from typing import Any


def utc_now() -> datetime:
    return datetime.now(tz=UTC)


def epoch_millis(value: datetime) -> int:
    return int(value.timestamp() * 1000)


class RelationshipState(str, Enum):
    PENDING_IMPORT = "PENDING_IMPORT"
    PENDING_HANDSHAKE = "PENDING_HANDSHAKE"
    ACTIVE = "ACTIVE"
    UNLINK_REQUESTED = "UNLINK_REQUESTED"
    UNLINKED = "UNLINKED"
    BLOCKED_BY_PEER = "BLOCKED_BY_PEER"
    REJOIN_REQUIRED = "REJOIN_REQUIRED"

    @property
    def title(self) -> str:
        return {
            RelationshipState.PENDING_IMPORT: "ожидает импорта",
            RelationshipState.PENDING_HANDSHAKE: "ожидает рукопожатия",
            RelationshipState.ACTIVE: "активен",
            RelationshipState.UNLINK_REQUESTED: "разрыв запрошен",
            RelationshipState.UNLINKED: "отвязан",
            RelationshipState.BLOCKED_BY_PEER: "заблокирован",
            RelationshipState.REJOIN_REQUIRED: "нужен повторный вход",
        }[self]

    @property
    def is_message_capable(self) -> bool:
        return self is RelationshipState.ACTIVE


class MessageDirection(str, Enum):
    OUTGOING = "OUTGOING"
    INCOMING = "INCOMING"


class MessageStatus(str, Enum):
    LOCAL_PENDING = "LOCAL_PENDING"
    READY_FOR_TRANSPORT = "READY_FOR_TRANSPORT"
    SENT_TO_TRANSPORT = "SENT_TO_TRANSPORT"
    DELIVERED_TO_PEER = "DELIVERED_TO_PEER"
    FAILED = "FAILED"

    @property
    def title(self) -> str:
        return {
            MessageStatus.LOCAL_PENDING: "локально",
            MessageStatus.READY_FOR_TRANSPORT: "готово к маршруту",
            MessageStatus.SENT_TO_TRANSPORT: "передано транспорту",
            MessageStatus.DELIVERED_TO_PEER: "доставлено",
            MessageStatus.FAILED: "ошибка",
        }[self]


class PeerRouteKind(str, Enum):
    NONE = "NONE"
    DIRECT_BLE = "DIRECT_BLE"
    DIRECT_LAN = "DIRECT_LAN"
    ROUTED_MESH = "ROUTED_MESH"

    @property
    def title(self) -> str:
        return {
            PeerRouteKind.NONE: "нет маршрута",
            PeerRouteKind.DIRECT_BLE: "Bluetooth напрямую",
            PeerRouteKind.DIRECT_LAN: "Wi-Fi/LAN напрямую",
            PeerRouteKind.ROUTED_MESH: "через ретранслятор",
        }[self]


class BandwidthClass(str, Enum):
    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class AdamovaAdmissionDecision(str, Enum):
    ACCEPT = "ACCEPT"
    REJECT_SINGULAR = "REJECT_SINGULAR"
    REJECT_SMALL_TORSION_RISK = "REJECT_SMALL_TORSION_RISK"
    REFERENCE_VALIDATION_REQUIRED = "REFERENCE_VALIDATION_REQUIRED"
    SIZE_GUARDED = "SIZE_GUARDED"
    NATIVE_UNAVAILABLE = "NATIVE_UNAVAILABLE"
    NOT_APPLICABLE_STANDARD_PROFILE = "NOT_APPLICABLE_STANDARD_PROFILE"

    @property
    def title(self) -> str:
        return {
            AdamovaAdmissionDecision.ACCEPT: "принят",
            AdamovaAdmissionDecision.REJECT_SINGULAR: "отклонён: сингулярность",
            AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK: "отклонён: риск кручения",
            AdamovaAdmissionDecision.REFERENCE_VALIDATION_REQUIRED: "нужна сверка",
            AdamovaAdmissionDecision.SIZE_GUARDED: "ограничен размером",
            AdamovaAdmissionDecision.NATIVE_UNAVAILABLE: "нативное ядро недоступно",
            AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE: "стандартный профиль",
        }[self]

    @property
    def accepted_for_packet_policy(self) -> bool:
        return self in {
            AdamovaAdmissionDecision.ACCEPT,
            AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE,
        }


@dataclass(slots=True)
class LocalIdentity:
    identity_id: str
    display_name: str
    public_key_encoded: str
    private_key_reference: str
    fingerprint: str
    created_at: datetime


@dataclass(slots=True)
class Relationship:
    relationship_id: str
    peer_display_name: str
    peer_fingerprint: str
    state: RelationshipState
    crypto_profile_id: str
    admission_decision_hash: str
    updated_at: datetime
    profile_policy_version: int | None = 1


@dataclass(slots=True)
class LocalMessage:
    message_id: str
    relationship_id: str
    peer_fingerprint: str
    direction: MessageDirection
    status: MessageStatus
    body: str
    created_at: datetime
    updated_at: datetime


@dataclass(slots=True)
class PeerRouteSnapshot:
    relationship_id: str
    peer_fingerprint: str
    kind: PeerRouteKind
    transport_id: str | None
    bandwidth_class: BandwidthClass
    hop_count: int | None
    last_seen_at: datetime | None


@dataclass(slots=True)
class AdmissionResult:
    profile_id: str
    decision: AdamovaAdmissionDecision
    decision_hash: str
    native_backend_version: str
    risk_flags: list[str]
    evaluated_at: datetime


@dataclass(slots=True)
class KrakenDesktopState:
    local_identity: LocalIdentity | None
    relationships: list[Relationship]
    messages: list[LocalMessage]
    routes: list[PeerRouteSnapshot]
    admission_result: AdmissionResult
    last_event: str


@dataclass(slots=True)
class KrakenPacket:
    packet_id: str
    sender_fingerprint: str
    recipient_fingerprint: str
    relationship_id: str
    conversation_id: str
    message_id: str | None
    created_at_epoch_millis: int
    expires_at_epoch_millis: int
    payload_json: str
    protocol_version: int = 1
    packet_type: str = "MESSAGE"
    ttl_hops: int = 4
    payload_type: str = "LOCAL_MESSAGE_JSON"
    crypto_profile_id: str | None = "standard-reviewed-primitives-v1"
    session_profile_id: str | None = None
    admission_decision_hash: str | None = "sha256:standard-reviewed-primitives-v1:not-applicable:v1"
    profile_policy_version: int | None = 1
    proof_mode: str = "prototype-placeholder"


@dataclass(slots=True)
class LanFrameEnvelope:
    sender_peer_id: str
    sender_fingerprint: str
    packet: KrakenPacket
    frame_version: int = 1
    sender_display_name: str | None = None
    sender_reply_port: int | None = None


@dataclass(slots=True)
class LanEndpoint:
    host: str
    port: int
    fingerprint: str
    display_name: str | None = None


@dataclass(slots=True)
class BleFrameChunk:
    transfer_id: str
    sender_peer_id: str
    sender_fingerprint: str
    chunk_index: int
    chunk_count: int
    payload_size: int
    payload_crc32: int
    payload_base64: str
    frame_version: int = 1
    sender_display_name: str | None = None


PACKET_KEYS = {
    "packet_id": "packet_id",
    "protocol_version": "protocol_version",
    "packet_type": "packet_type",
    "sender_fingerprint": "sender_fingerprint",
    "recipient_fingerprint": "recipient_fingerprint",
    "relationship_id": "relationship_id",
    "conversation_id": "conversation_id",
    "message_id": "message_id",
    "created_at_epoch_millis": "created_at_epoch_millis",
    "expires_at_epoch_millis": "expires_at_epoch_millis",
    "ttl_hops": "ttl_hops",
    "payload_type": "payload_type",
    "payload_json": "payload_json",
    "crypto_profile_id": "crypto_profile_id",
    "session_profile_id": "session_profile_id",
    "admission_decision_hash": "admission_decision_hash",
    "profile_policy_version": "profile_policy_version",
    "proof_mode": "proof_mode",
}


def packet_to_dict(packet: KrakenPacket, *, omit_none: bool = False) -> dict[str, Any]:
    value = {
        "packet_id": packet.packet_id,
        "protocol_version": packet.protocol_version,
        "packet_type": packet.packet_type,
        "sender_fingerprint": packet.sender_fingerprint,
        "recipient_fingerprint": packet.recipient_fingerprint,
        "relationship_id": packet.relationship_id,
        "conversation_id": packet.conversation_id,
        "message_id": packet.message_id,
        "created_at_epoch_millis": packet.created_at_epoch_millis,
        "expires_at_epoch_millis": packet.expires_at_epoch_millis,
        "ttl_hops": packet.ttl_hops,
        "payload_type": packet.payload_type,
        "payload_json": packet.payload_json,
        "crypto_profile_id": packet.crypto_profile_id,
        "session_profile_id": packet.session_profile_id,
        "admission_decision_hash": packet.admission_decision_hash,
        "profile_policy_version": packet.profile_policy_version,
        "proof_mode": packet.proof_mode,
    }
    return {key: item for key, item in value.items() if item is not None} if omit_none else value


def packet_from_dict(value: dict[str, Any]) -> KrakenPacket:
    return KrakenPacket(
        packet_id=str(value["packet_id"]),
        protocol_version=int(value.get("protocol_version", 1)),
        packet_type=str(value.get("packet_type", "MESSAGE")),
        sender_fingerprint=str(value["sender_fingerprint"]),
        recipient_fingerprint=str(value["recipient_fingerprint"]),
        relationship_id=str(value["relationship_id"]),
        conversation_id=str(value["conversation_id"]),
        message_id=value.get("message_id"),
        created_at_epoch_millis=int(value["created_at_epoch_millis"]),
        expires_at_epoch_millis=int(value["expires_at_epoch_millis"]),
        ttl_hops=int(value.get("ttl_hops", 4)),
        payload_type=str(value.get("payload_type", "LOCAL_MESSAGE_JSON")),
        payload_json=str(value.get("payload_json", "")),
        crypto_profile_id=value.get("crypto_profile_id", "standard-reviewed-primitives-v1"),
        session_profile_id=value.get("session_profile_id"),
        admission_decision_hash=value.get(
            "admission_decision_hash",
            "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
        ),
        profile_policy_version=value.get("profile_policy_version", 1),
        proof_mode=str(value.get("proof_mode", "prototype-placeholder")),
    )


def envelope_to_dict(envelope: LanFrameEnvelope, *, omit_none: bool = False) -> dict[str, Any]:
    value = {
        "frame_version": envelope.frame_version,
        "sender_peer_id": envelope.sender_peer_id,
        "sender_fingerprint": envelope.sender_fingerprint,
        "sender_display_name": envelope.sender_display_name,
        "sender_reply_port": envelope.sender_reply_port,
        "packet": packet_to_dict(envelope.packet, omit_none=omit_none),
    }
    return {key: item for key, item in value.items() if item is not None} if omit_none else value


def envelope_from_dict(value: dict[str, Any]) -> LanFrameEnvelope:
    packet = packet_from_dict(value["packet"])
    return LanFrameEnvelope(
        frame_version=int(value.get("frame_version", 1)),
        sender_peer_id=str(value["sender_peer_id"]),
        sender_fingerprint=str(value["sender_fingerprint"]),
        sender_display_name=value.get("sender_display_name"),
        sender_reply_port=value.get("sender_reply_port"),
        packet=packet,
    )


def state_to_jsonable(value: Any) -> Any:
    if isinstance(value, Enum):
        return value.value
    if isinstance(value, datetime):
        return value.isoformat()
    if is_dataclass(value):
        return {key: state_to_jsonable(item) for key, item in asdict(value).items()}
    if isinstance(value, list):
        return [state_to_jsonable(item) for item in value]
    if isinstance(value, dict):
        return {key: state_to_jsonable(item) for key, item in value.items()}
    return value


def parse_datetime(value: str | datetime | None) -> datetime | None:
    if value is None or isinstance(value, datetime):
        return value
    normalized = value.replace("Z", "+00:00")
    parsed = datetime.fromisoformat(normalized)
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=UTC)


def _required_datetime(value: str | datetime) -> datetime:
    parsed = parse_datetime(value)
    if parsed is None:
        raise ValueError("datetime_required")
    return parsed


def identity_from_dict(value: dict[str, Any] | None) -> LocalIdentity | None:
    if value is None:
        return None
    return LocalIdentity(
        identity_id=str(value["identity_id"]),
        display_name=str(value["display_name"]),
        public_key_encoded=str(value["public_key_encoded"]),
        private_key_reference=str(value["private_key_reference"]),
        fingerprint=str(value["fingerprint"]),
        created_at=_required_datetime(value["created_at"]),
    )


def relationship_from_dict(value: dict[str, Any]) -> Relationship:
    return Relationship(
        relationship_id=str(value["relationship_id"]),
        peer_display_name=str(value["peer_display_name"]),
        peer_fingerprint=str(value["peer_fingerprint"]),
        state=RelationshipState(value["state"]),
        crypto_profile_id=str(value["crypto_profile_id"]),
        admission_decision_hash=str(value["admission_decision_hash"]),
        updated_at=_required_datetime(value["updated_at"]),
        profile_policy_version=value.get("profile_policy_version", 1),
    )


def message_from_dict(value: dict[str, Any]) -> LocalMessage:
    return LocalMessage(
        message_id=str(value["message_id"]),
        relationship_id=str(value["relationship_id"]),
        peer_fingerprint=str(value["peer_fingerprint"]),
        direction=MessageDirection(value["direction"]),
        status=MessageStatus(value["status"]),
        body=str(value["body"]),
        created_at=_required_datetime(value["created_at"]),
        updated_at=_required_datetime(value["updated_at"]),
    )


def route_from_dict(value: dict[str, Any]) -> PeerRouteSnapshot:
    return PeerRouteSnapshot(
        relationship_id=str(value["relationship_id"]),
        peer_fingerprint=str(value["peer_fingerprint"]),
        kind=PeerRouteKind(value["kind"]),
        transport_id=value.get("transport_id"),
        bandwidth_class=BandwidthClass(value["bandwidth_class"]),
        hop_count=value.get("hop_count"),
        last_seen_at=parse_datetime(value.get("last_seen_at")),
    )


def admission_from_dict(value: dict[str, Any]) -> AdmissionResult:
    return AdmissionResult(
        profile_id=str(value["profile_id"]),
        decision=AdamovaAdmissionDecision(value["decision"]),
        decision_hash=str(value["decision_hash"]),
        native_backend_version=str(value["native_backend_version"]),
        risk_flags=list(value.get("risk_flags", [])),
        evaluated_at=_required_datetime(value["evaluated_at"]),
    )


def state_from_dict(value: dict[str, Any]) -> KrakenDesktopState:
    return KrakenDesktopState(
        local_identity=identity_from_dict(value.get("local_identity")),
        relationships=[relationship_from_dict(item) for item in value.get("relationships", [])],
        messages=[message_from_dict(item) for item in value.get("messages", [])],
        routes=[route_from_dict(item) for item in value.get("routes", [])],
        admission_result=admission_from_dict(value["admission_result"]),
        last_event=str(value.get("last_event", "")),
    )
