from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime
from enum import Enum

from .models import (
    BandwidthClass,
    KrakenDesktopState,
    LocalMessage,
    MessageDirection,
    MessageStatus,
    PeerRouteKind,
    PeerRouteSnapshot,
    Relationship,
    RelationshipState,
)


class LanEventDirection(str, Enum):
    INBOUND = "inbound"
    OUTBOUND = "outbound"


class LanEventStatus(str, Enum):
    ACCEPTED = "accepted"
    ACKED = "acked"
    FAILED = "failed"


class BleEventDirection(str, Enum):
    INBOUND = "inbound"
    OUTBOUND = "outbound"


class BleEventStatus(str, Enum):
    ACCEPTED = "accepted"
    QUEUED = "queued"
    FAILED = "failed"


@dataclass(slots=True)
class BleTransferEvent:
    direction: BleEventDirection
    status: BleEventStatus
    at_epoch_millis: int
    peer_fingerprint: str | None
    packet_id: str | None
    message_id: str | None
    payload_json: str | None = None
    sender_display_name: str | None = None
    sender_fingerprint: str | None = None
    recipient_fingerprint: str | None = None
    relationship_id: str | None = None
    chunk_count: int | None = None
    error: str | None = None


@dataclass(slots=True)
class LanTransferEvent:
    direction: LanEventDirection
    status: LanEventStatus
    at_epoch_millis: int
    source: str | None
    target: str | None
    packet_id: str | None
    message_id: str | None
    payload_json: str | None = None
    sender_display_name: str | None = None
    sender_fingerprint: str | None = None
    recipient_fingerprint: str | None = None
    relationship_id: str | None = None
    error: str | None = None

    def as_ble(self, peer_fingerprint: str | None = None) -> BleTransferEvent:
        status = {
            LanEventStatus.ACCEPTED: BleEventStatus.ACCEPTED,
            LanEventStatus.ACKED: BleEventStatus.QUEUED,
            LanEventStatus.FAILED: BleEventStatus.FAILED,
        }[self.status]
        return BleTransferEvent(
            direction=BleEventDirection(self.direction.value),
            status=status,
            at_epoch_millis=self.at_epoch_millis,
            peer_fingerprint=peer_fingerprint or self.sender_fingerprint or self.recipient_fingerprint,
            packet_id=self.packet_id,
            message_id=self.message_id,
            payload_json=self.payload_json,
            sender_display_name=self.sender_display_name,
            sender_fingerprint=peer_fingerprint or self.sender_fingerprint,
            recipient_fingerprint=self.recipient_fingerprint,
            relationship_id=self.relationship_id,
            error=self.error,
        )


class LanTimelineReducer:
    @staticmethod
    def apply(event: LanTransferEvent, state: KrakenDesktopState, now: datetime | None = None) -> tuple[KrakenDesktopState, str | None]:
        current = now or _datetime_from_millis(event.at_epoch_millis)
        selected_relationship_id = None
        if event.direction is LanEventDirection.OUTBOUND:
            _apply_lan_outbound(event, state, current)
        else:
            selected_relationship_id = _apply_lan_inbound(event, state, current)
        _refresh_route(event, state, current, PeerRouteKind.DIRECT_LAN, "windows-lan-tcp", BandwidthClass.HIGH)
        return state, selected_relationship_id


class BleTimelineReducer:
    @staticmethod
    def apply(event: BleTransferEvent, state: KrakenDesktopState, now: datetime | None = None) -> tuple[KrakenDesktopState, str | None]:
        current = now or _datetime_from_millis(event.at_epoch_millis)
        selected_relationship_id = None
        if event.direction is BleEventDirection.OUTBOUND:
            _apply_ble_outbound(event, state, current)
        else:
            selected_relationship_id = _apply_ble_inbound(event, state, current)
        _refresh_route(event, state, current, PeerRouteKind.DIRECT_BLE, "ble-gatt", BandwidthClass.LOW)
        return state, selected_relationship_id


def _apply_lan_outbound(event: LanTransferEvent, state: KrakenDesktopState, now: datetime) -> None:
    if event.message_id is None:
        return
    message = next((item for item in state.messages if item.message_id == event.message_id), None)
    if message is None:
        return
    message.status = MessageStatus.DELIVERED_TO_PEER if event.status is LanEventStatus.ACKED else MessageStatus.FAILED
    message.updated_at = now


def _apply_ble_outbound(event: BleTransferEvent, state: KrakenDesktopState, now: datetime) -> None:
    if event.message_id is None:
        return
    message = next((item for item in state.messages if item.message_id == event.message_id), None)
    if message is None:
        return
    if event.status is BleEventStatus.FAILED:
        message.status = MessageStatus.FAILED
        message.updated_at = now


def _apply_lan_inbound(event: LanTransferEvent, state: KrakenDesktopState, now: datetime) -> str | None:
    if event.status is not LanEventStatus.ACCEPTED or event.message_id is None:
        return None
    if any(message.message_id == event.message_id for message in state.messages):
        return None
    relationship = _ensure_relationship(
        state,
        relationship_id=event.relationship_id or f"rel-lan-{event.sender_fingerprint}",
        peer_display_name=event.sender_display_name or "LAN-устройство",
        peer_fingerprint=event.sender_fingerprint,
        route_kind=PeerRouteKind.DIRECT_LAN,
        transport_id="windows-lan-tcp",
        bandwidth_class=BandwidthClass.HIGH,
        now=now,
    )
    if relationship is None:
        return None
    state.messages.append(
        LocalMessage(
            message_id=event.message_id,
            relationship_id=relationship.relationship_id,
            peer_fingerprint=relationship.peer_fingerprint,
            direction=MessageDirection.INCOMING,
            status=MessageStatus.DELIVERED_TO_PEER,
            body=_message_body(event.payload_json),
            created_at=now,
            updated_at=now,
        )
    )
    return relationship.relationship_id


def _apply_ble_inbound(event: BleTransferEvent, state: KrakenDesktopState, now: datetime) -> str | None:
    if event.status is not BleEventStatus.ACCEPTED or event.message_id is None:
        return None
    if any(message.message_id == event.message_id for message in state.messages):
        return None
    sender_fingerprint = event.sender_fingerprint or event.peer_fingerprint
    relationship = _ensure_relationship(
        state,
        relationship_id=event.relationship_id or f"rel-ble-{sender_fingerprint}",
        peer_display_name=event.sender_display_name or "Bluetooth-устройство",
        peer_fingerprint=sender_fingerprint,
        route_kind=PeerRouteKind.DIRECT_BLE,
        transport_id="ble-gatt",
        bandwidth_class=BandwidthClass.LOW,
        now=now,
    )
    if relationship is None:
        return None
    state.messages.append(
        LocalMessage(
            message_id=event.message_id,
            relationship_id=relationship.relationship_id,
            peer_fingerprint=relationship.peer_fingerprint,
            direction=MessageDirection.INCOMING,
            status=MessageStatus.DELIVERED_TO_PEER,
            body=_message_body(event.payload_json),
            created_at=now,
            updated_at=now,
        )
    )
    return relationship.relationship_id


def _ensure_relationship(
    state: KrakenDesktopState,
    *,
    relationship_id: str,
    peer_display_name: str,
    peer_fingerprint: str | None,
    route_kind: PeerRouteKind,
    transport_id: str,
    bandwidth_class: BandwidthClass,
    now: datetime,
) -> Relationship | None:
    if not peer_fingerprint:
        return None
    existing = next((item for item in state.relationships if item.peer_fingerprint == peer_fingerprint or item.relationship_id == relationship_id), None)
    if existing is not None:
        return existing
    relationship = Relationship(
        relationship_id=relationship_id,
        peer_display_name=peer_display_name,
        peer_fingerprint=peer_fingerprint,
        state=RelationshipState.ACTIVE,
        crypto_profile_id="standard-reviewed-primitives-v1",
        admission_decision_hash="sha256:standard-reviewed-primitives-v1:not-applicable:v1",
        updated_at=now,
    )
    state.relationships.insert(0, relationship)
    state.routes.insert(
        0,
        PeerRouteSnapshot(
            relationship_id=relationship.relationship_id,
            peer_fingerprint=relationship.peer_fingerprint,
            kind=route_kind,
            transport_id=transport_id,
            bandwidth_class=bandwidth_class,
            hop_count=1,
            last_seen_at=now,
        ),
    )
    return relationship


def _refresh_route(
    event: LanTransferEvent | BleTransferEvent,
    state: KrakenDesktopState,
    now: datetime,
    kind: PeerRouteKind,
    transport_id: str,
    bandwidth_class: BandwidthClass,
) -> None:
    status = event.status
    if status.value == "failed":
        return
    relationship = _find_relationship(event, state)
    if relationship is None:
        return
    route = PeerRouteSnapshot(
        relationship_id=relationship.relationship_id,
        peer_fingerprint=relationship.peer_fingerprint,
        kind=kind,
        transport_id=transport_id,
        bandwidth_class=bandwidth_class,
        hop_count=1,
        last_seen_at=now,
    )
    for index, item in enumerate(state.routes):
        if item.relationship_id == route.relationship_id:
            state.routes[index] = route
            return
    state.routes.insert(0, route)


def _find_relationship(event: LanTransferEvent | BleTransferEvent, state: KrakenDesktopState) -> Relationship | None:
    if event.relationship_id:
        relationship = next((item for item in state.relationships if item.relationship_id == event.relationship_id), None)
        if relationship is not None:
            return relationship
    fingerprints = [
        getattr(event, "peer_fingerprint", None),
        event.sender_fingerprint,
        event.recipient_fingerprint,
    ]
    return next((item for item in state.relationships if item.peer_fingerprint in fingerprints), None)


def _message_body(payload_json: str | None) -> str:
    if payload_json is None:
        return ""
    try:
        payload = json.loads(payload_json)
    except json.JSONDecodeError:
        return payload_json
    if isinstance(payload, dict):
        for key in ("body", "text"):
            if isinstance(payload.get(key), str):
                return payload[key]
    return payload_json


def _datetime_from_millis(value: int) -> datetime:
    return datetime.fromtimestamp(value / 1000, tz=UTC)
