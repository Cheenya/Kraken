from __future__ import annotations

import json
import uuid
from collections.abc import Callable
from datetime import datetime
from typing import Any

from .models import (
    AdmissionResult,
    AdamovaAdmissionDecision,
    BandwidthClass,
    KrakenDesktopState,
    LocalIdentity,
    LocalMessage,
    MessageDirection,
    MessageStatus,
    PeerRouteKind,
    PeerRouteSnapshot,
    Relationship,
    RelationshipState,
    utc_now,
)


class KrakenDesktopSimulator:
    def __init__(
        self,
        now: Callable[[], datetime] = utc_now,
        make_id: Callable[[], str] | None = None,
    ) -> None:
        self._now = now
        self._make_id = make_id or (lambda: str(uuid.uuid4()))

    def make_initial_state(self) -> KrakenDesktopState:
        current = self._now()
        relationships = [
            Relationship(
                relationship_id="rel-xiaomi",
                peer_display_name="Xiaomi тестовый",
                peer_fingerprint="A17C9E2048F0DA11",
                state=RelationshipState.ACTIVE,
                crypto_profile_id="standard-reviewed-primitives-v1",
                admission_decision_hash="sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                updated_at=current,
            ),
            Relationship(
                relationship_id="rel-samsung",
                peer_display_name="Samsung лабораторный",
                peer_fingerprint="C0D3A911E80477B2",
                state=RelationshipState.PENDING_HANDSHAKE,
                crypto_profile_id="experimental-adamova-lc32-prime-offsets-v1",
                admission_decision_hash="sha256:desktop-adamova-demo",
                updated_at=current,
            ),
        ]
        routes = [
            PeerRouteSnapshot(
                relationship_id=relationship.relationship_id,
                peer_fingerprint=relationship.peer_fingerprint,
                kind=PeerRouteKind.DIRECT_LAN if relationship.state is RelationshipState.ACTIVE else PeerRouteKind.NONE,
                transport_id="lan-nsd-tcp" if relationship.state is RelationshipState.ACTIVE else None,
                bandwidth_class=BandwidthClass.HIGH if relationship.state is RelationshipState.ACTIVE else BandwidthClass.NONE,
                hop_count=1 if relationship.state is RelationshipState.ACTIVE else None,
                last_seen_at=current if relationship.state is RelationshipState.ACTIVE else None,
            )
            for relationship in relationships
        ]
        return KrakenDesktopState(
            local_identity=None,
            relationships=relationships,
            messages=[
                LocalMessage(
                    message_id="msg-welcome",
                    relationship_id="rel-xiaomi",
                    peer_fingerprint="A17C9E2048F0DA11",
                    direction=MessageDirection.INCOMING,
                    status=MessageStatus.DELIVERED_TO_PEER,
                    body="Kraken Desktop подключён.",
                    created_at=current,
                    updated_at=current,
                )
            ],
            routes=routes,
            admission_result=AdmissionResult(
                profile_id="standard-reviewed-primitives-v1",
                decision=AdamovaAdmissionDecision.NOT_APPLICABLE_STANDARD_PROFILE,
                decision_hash="sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                native_backend_version="not-applicable-standard-profile",
                risk_flags=[],
                evaluated_at=current,
            ),
            last_event="Kraken Desktop инициализирован",
        )

    def create_identity(self, state: KrakenDesktopState, display_name: str) -> KrakenDesktopState:
        current = self._now()
        normalized_name = display_name.strip() or "Kraken Windows"
        state.local_identity = LocalIdentity(
            identity_id=f"identity-{self._make_id()}",
            display_name=normalized_name,
            public_key_encoded=f"windows-public-key-{self._make_id()[:8]}",
            private_key_reference="windows-credential-manager-placeholder",
            fingerprint=self._make_id().replace("-", "")[:16].upper(),
            created_at=current,
        )
        state.last_event = "Создана локальная личность"
        return state

    def import_peer(self, state: KrakenDesktopState, name: str) -> KrakenDesktopState:
        current = self._now()
        peer_name = name.strip() or "Новое устройство"
        fingerprint = self._make_id().replace("-", "")[:16].upper()
        relationship = Relationship(
            relationship_id=f"rel-{self._make_id()}",
            peer_display_name=peer_name,
            peer_fingerprint=fingerprint,
            state=RelationshipState.PENDING_HANDSHAKE,
            crypto_profile_id="standard-reviewed-primitives-v1",
            admission_decision_hash="sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            updated_at=current,
        )
        state.relationships.insert(0, relationship)
        state.routes.insert(
            0,
            PeerRouteSnapshot(
                relationship_id=relationship.relationship_id,
                peer_fingerprint=fingerprint,
                kind=PeerRouteKind.NONE,
                transport_id=None,
                bandwidth_class=BandwidthClass.NONE,
                hop_count=None,
                last_seen_at=None,
            ),
        )
        state.last_event = "устройство добавлено для офлайн-рукопожатия"
        return state

    def activate_relationship(self, state: KrakenDesktopState, relationship_id: str) -> KrakenDesktopState:
        current = self._now()
        for relationship in state.relationships:
            if relationship.relationship_id == relationship_id:
                relationship.state = RelationshipState.ACTIVE
                relationship.updated_at = current
        for route in state.routes:
            if route.relationship_id == relationship_id:
                route.kind = PeerRouteKind.DIRECT_LAN
                route.transport_id = "lan-nsd-tcp"
                route.bandwidth_class = BandwidthClass.HIGH
                route.hop_count = 1
                route.last_seen_at = current
        state.last_event = "Связь с устройством активирована"
        return state

    def send_message(self, state: KrakenDesktopState, relationship_id: str, body: str) -> KrakenDesktopState:
        relationship = self._relationship(state, relationship_id)
        if relationship is None:
            state.last_event = "Сообщение не отправлено: устройство не найдено"
            return state
        if not relationship.state.is_message_capable:
            state.last_event = "Сообщение не отправлено: связь с устройством не активна"
            return state

        current = self._now()
        route = self._route(state, relationship_id)
        status = MessageStatus.READY_FOR_TRANSPORT if route is None or route.kind is PeerRouteKind.NONE else MessageStatus.SENT_TO_TRANSPORT
        state.messages.append(
            LocalMessage(
                message_id=f"msg-{self._make_id()}",
                relationship_id=relationship.relationship_id,
                peer_fingerprint=relationship.peer_fingerprint,
                direction=MessageDirection.OUTGOING,
                status=status,
                body=body.strip(),
                created_at=current,
                updated_at=current,
            )
        )
        state.last_event = (
            "Сообщение передано транспортному симулятору"
            if status is MessageStatus.SENT_TO_TRANSPORT
            else "Сообщение ожидает доступный маршрут"
        )
        return state

    def confirm_latest_delivery(self, state: KrakenDesktopState, relationship_id: str) -> KrakenDesktopState:
        current = self._now()
        for message in reversed(state.messages):
            if message.relationship_id == relationship_id and message.direction is MessageDirection.OUTGOING:
                message.status = MessageStatus.DELIVERED_TO_PEER
                message.updated_at = current
                state.last_event = "Подтверждение доставки применено к последнему сообщению"
                return state
        state.last_event = "Нет исходящего сообщения для подтверждения"
        return state

    def cycle_route(self, state: KrakenDesktopState, relationship_id: str) -> KrakenDesktopState:
        current = self._now()
        for route in state.routes:
            if route.relationship_id != relationship_id:
                continue
            route.kind = {
                PeerRouteKind.NONE: PeerRouteKind.DIRECT_BLE,
                PeerRouteKind.DIRECT_BLE: PeerRouteKind.DIRECT_LAN,
                PeerRouteKind.DIRECT_LAN: PeerRouteKind.ROUTED_MESH,
                PeerRouteKind.ROUTED_MESH: PeerRouteKind.NONE,
            }[route.kind]
            route.transport_id = route_transport_id(route.kind)
            route.bandwidth_class = bandwidth_for(route.kind)
            route.hop_count = None if route.kind is PeerRouteKind.NONE else (2 if route.kind is PeerRouteKind.ROUTED_MESH else 1)
            route.last_seen_at = None if route.kind is PeerRouteKind.NONE else current
        state.last_event = "Маршрут устройства обновлён"
        return state

    def evaluate_admission(self, state: KrakenDesktopState, experimental: bool) -> KrakenDesktopState:
        current = self._now()
        if experimental:
            state.admission_result = AdmissionResult(
                profile_id="experimental-adamova-lc32-prime-offsets-v1",
                decision=AdamovaAdmissionDecision.ACCEPT,
                decision_hash="sha256:desktop-adamova-accept:v1",
                native_backend_version="windows-simulated-adamova-stage-a",
                risk_flags=[],
                evaluated_at=current,
            )
            state.last_event = "Экспериментальный профиль Adamova принят"
        else:
            state.admission_result = AdmissionResult(
                profile_id="experimental-adamova-risk-demo-v1",
                decision=AdamovaAdmissionDecision.REJECT_SMALL_TORSION_RISK,
                decision_hash="sha256:desktop-adamova-reject-small-torsion:v1",
                native_backend_version="windows-simulated-adamova-stage-a",
                risk_flags=["rational_2_torsion"],
                evaluated_at=current,
            )
            state.last_event = "Рискованный профиль Adamova отклонён"
        return state

    def apply_inbound_lan_message(
        self,
        state: KrakenDesktopState,
        *,
        message_id: str,
        payload_json: str,
        sender_fingerprint: str,
        sender_display_name: str | None = None,
        relationship_id: str | None = None,
    ) -> str | None:
        if any(message.message_id == message_id for message in state.messages):
            return None
        current = self._now()
        relationship = self._ensure_relationship(
            state,
            relationship_id=relationship_id or f"rel-lan-{sender_fingerprint}",
            peer_display_name=sender_display_name or "LAN-устройство",
            peer_fingerprint=sender_fingerprint,
            route_kind=PeerRouteKind.DIRECT_LAN,
            transport_id="windows-lan-tcp",
            bandwidth_class=BandwidthClass.HIGH,
            now=current,
        )
        state.messages.append(
            LocalMessage(
                message_id=message_id,
                relationship_id=relationship.relationship_id,
                peer_fingerprint=relationship.peer_fingerprint,
                direction=MessageDirection.INCOMING,
                status=MessageStatus.DELIVERED_TO_PEER,
                body=message_body_from_payload_json(payload_json),
                created_at=current,
                updated_at=current,
            )
        )
        state.last_event = "Входящий LAN payload добавлен в таймлайн"
        return relationship.relationship_id

    def _ensure_relationship(
        self,
        state: KrakenDesktopState,
        *,
        relationship_id: str,
        peer_display_name: str,
        peer_fingerprint: str,
        route_kind: PeerRouteKind,
        transport_id: str,
        bandwidth_class: BandwidthClass,
        now: datetime,
    ) -> Relationship:
        existing = next(
            (relationship for relationship in state.relationships if relationship.peer_fingerprint == peer_fingerprint),
            None,
        )
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

    @staticmethod
    def _relationship(state: KrakenDesktopState, relationship_id: str) -> Relationship | None:
        return next((relationship for relationship in state.relationships if relationship.relationship_id == relationship_id), None)

    @staticmethod
    def _route(state: KrakenDesktopState, relationship_id: str) -> PeerRouteSnapshot | None:
        return next((route for route in state.routes if route.relationship_id == relationship_id), None)


def route_transport_id(kind: PeerRouteKind) -> str | None:
    return {
        PeerRouteKind.NONE: None,
        PeerRouteKind.DIRECT_BLE: "ble-gatt",
        PeerRouteKind.DIRECT_LAN: "lan-nsd-tcp",
        PeerRouteKind.ROUTED_MESH: "routed-relay",
    }[kind]


def bandwidth_for(kind: PeerRouteKind) -> BandwidthClass:
    return {
        PeerRouteKind.NONE: BandwidthClass.NONE,
        PeerRouteKind.DIRECT_BLE: BandwidthClass.LOW,
        PeerRouteKind.DIRECT_LAN: BandwidthClass.HIGH,
        PeerRouteKind.ROUTED_MESH: BandwidthClass.MEDIUM,
    }[kind]


def message_body_from_payload_json(payload_json: str | None) -> str:
    if not payload_json:
        return ""
    try:
        decoded: Any = json.loads(payload_json)
    except json.JSONDecodeError:
        return payload_json
    if isinstance(decoded, dict):
        if isinstance(decoded.get("body"), str):
            return decoded["body"]
        if isinstance(decoded.get("text"), str):
            return decoded["text"]
    return payload_json
