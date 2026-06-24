from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import Any

from .codec import HandshakeQrCodec
from .models import (
    BandwidthClass,
    KrakenDesktopState,
    LocalIdentity,
    PeerRouteKind,
    PeerRouteSnapshot,
    Relationship,
    RelationshipState,
)


class HandshakeImportService:
    @classmethod
    def import_payload(
        cls,
        state: KrakenDesktopState,
        raw_payload: str,
        *,
        identity: LocalIdentity | None,
        now_millis: int | None = None,
    ) -> str | None:
        if identity is None:
            return "Сначала создайте личность Kraken на Windows."
        normalized = HandshakeQrCodec.normalized_scanned_payload(raw_payload)
        try:
            payload = json.loads(normalized)
        except json.JSONDecodeError:
            return "Этот QR не является JSON-payload Kraken."
        if not isinstance(payload, dict):
            return "Этот QR не является JSON-объектом Kraken."

        kind = HandshakeQrCodec.detect_kind(normalized)
        if kind == "invite":
            return cls._import_invite(state, payload, identity=identity, now_millis=now_millis)
        if kind == "response":
            return cls._import_response(state, payload, identity=identity)
        if kind == "confirmation":
            return cls._import_confirmation(state, payload, identity=identity)
        return f"QR считан, но этот тип payload пока не поддержан: {payload.get('type') or 'без типа'}."

    @classmethod
    def _import_invite(
        cls,
        state: KrakenDesktopState,
        payload: dict[str, Any],
        *,
        identity: LocalIdentity,
        now_millis: int | None,
    ) -> str | None:
        error = cls._require(payload, ["invite_id", "inviter_fingerprint", "inviter_public_key_encoded"])
        if error:
            return error
        if int(payload.get("version", 0)) != 1:
            return "Версия QR-приглашения не поддерживается."
        expires_at = payload.get("expires_at_epoch_millis")
        if expires_at is not None and int(expires_at) <= (now_millis or _now_millis()):
            return "QR-приглашение истекло. Обновите QR на втором устройстве."
        inviter_fingerprint = str(payload["inviter_fingerprint"])
        inviter_public_key = str(payload["inviter_public_key_encoded"])
        if _normalized(inviter_fingerprint) == _normalized(identity.fingerprint) or inviter_public_key == identity.public_key_encoded:
            return "Нельзя импортировать собственный QR."

        relationship_id = _offline_relationship_id(
            invite_id=str(payload["invite_id"]),
            inviter_fingerprint=inviter_fingerprint,
            responder_fingerprint=identity.fingerprint,
        )
        cls._upsert_active_relationship(
            state,
            relationship_id=relationship_id,
            peer_display_name=str(payload.get("inviter_display_name") or "Устройство Kraken").strip() or "Устройство Kraken",
            peer_fingerprint=inviter_fingerprint,
            crypto_profile_id=str(payload.get("crypto_profile_id") or "standard-reviewed-primitives-v1"),
            admission_decision_hash=str(payload.get("admission_decision_hash") or "sha256:standard-reviewed-primitives-v1:not-applicable:v1"),
            profile_policy_version=payload.get("profile_policy_version", 1),
            route_kind=PeerRouteKind.DIRECT_LAN,
            transport_id="windows-lan-tcp",
        )
        state.last_event = f"QR импортирован: {payload.get('inviter_display_name') or 'Устройство Kraken'}"
        return None

    @classmethod
    def _import_response(cls, state: KrakenDesktopState, payload: dict[str, Any], *, identity: LocalIdentity) -> str | None:
        error = cls._require(payload, ["response_id", "invite_id", "inviter_fingerprint", "responder_fingerprint", "responder_public_key_encoded"])
        if error:
            return error
        if _normalized(str(payload["inviter_fingerprint"])) != _normalized(identity.fingerprint):
            return "Этот ответный QR адресован другой личности Kraken."
        responder_fingerprint = str(payload["responder_fingerprint"])
        if _normalized(responder_fingerprint) == _normalized(identity.fingerprint) or payload["responder_public_key_encoded"] == identity.public_key_encoded:
            return "Нельзя принять собственный ответный QR."
        relationship_id = _offline_relationship_id(
            invite_id=str(payload["invite_id"]),
            inviter_fingerprint=identity.fingerprint,
            responder_fingerprint=responder_fingerprint,
        )
        cls._upsert_active_relationship(
            state,
            relationship_id=relationship_id,
            peer_display_name=str(payload.get("responder_display_name") or "Устройство Kraken").strip() or "Устройство Kraken",
            peer_fingerprint=responder_fingerprint,
            crypto_profile_id=str(payload.get("crypto_profile_id") or "standard-reviewed-primitives-v1"),
            admission_decision_hash=str(payload.get("admission_decision_hash") or "sha256:standard-reviewed-primitives-v1:not-applicable:v1"),
            profile_policy_version=payload.get("profile_policy_version", 1),
            route_kind=PeerRouteKind.DIRECT_LAN,
            transport_id="windows-lan-tcp",
        )
        state.last_event = f"Ответный QR принят: {payload.get('responder_display_name') or 'Устройство Kraken'}"
        return None

    @classmethod
    def _import_confirmation(cls, state: KrakenDesktopState, payload: dict[str, Any], *, identity: LocalIdentity) -> str | None:
        error = cls._require(payload, ["confirmation_id", "response_id", "invite_id", "inviter_fingerprint", "responder_fingerprint"])
        if error:
            return error
        if _normalized(str(payload["responder_fingerprint"])) != _normalized(identity.fingerprint):
            return "Этот финальный QR адресован другой личности Kraken."
        if _normalized(str(payload["inviter_fingerprint"])) == _normalized(identity.fingerprint):
            return "Этот финальный QR предназначен второму устройству, а не владельцу приглашения."
        inviter_fingerprint = str(payload["inviter_fingerprint"])
        relationship_id = _offline_relationship_id(
            invite_id=str(payload["invite_id"]),
            inviter_fingerprint=inviter_fingerprint,
            responder_fingerprint=identity.fingerprint,
        )
        cls._upsert_active_relationship(
            state,
            relationship_id=relationship_id,
            peer_display_name=str(payload.get("realm_name") or "Устройство Kraken").strip() or "Устройство Kraken",
            peer_fingerprint=inviter_fingerprint,
            crypto_profile_id=str(payload.get("crypto_profile_id") or "standard-reviewed-primitives-v1"),
            admission_decision_hash=str(payload.get("admission_decision_hash") or "sha256:standard-reviewed-primitives-v1:not-applicable:v1"),
            profile_policy_version=payload.get("profile_policy_version", 1),
            route_kind=PeerRouteKind.DIRECT_LAN,
            transport_id="windows-lan-tcp",
        )
        state.last_event = "Финальный QR принят"
        return None

    @staticmethod
    def _require(payload: dict[str, Any], keys: list[str]) -> str | None:
        for key in keys:
            value = payload.get(key)
            if value is None or str(value).strip() == "":
                return f"QR-приглашение неполное: не найдено поле {key}."
        return None

    @staticmethod
    def _upsert_active_relationship(
        state: KrakenDesktopState,
        *,
        relationship_id: str,
        peer_display_name: str,
        peer_fingerprint: str,
        crypto_profile_id: str,
        admission_decision_hash: str,
        profile_policy_version: int | None,
        route_kind: PeerRouteKind,
        transport_id: str,
    ) -> None:
        now = datetime.now(tz=UTC)
        relationship = Relationship(
            relationship_id=relationship_id,
            peer_display_name=peer_display_name,
            peer_fingerprint=peer_fingerprint,
            state=RelationshipState.ACTIVE,
            crypto_profile_id=crypto_profile_id,
            admission_decision_hash=admission_decision_hash,
            profile_policy_version=profile_policy_version,
            updated_at=now,
        )
        for index, existing in enumerate(state.relationships):
            if existing.relationship_id == relationship_id or _normalized(existing.peer_fingerprint) == _normalized(peer_fingerprint):
                state.relationships[index] = relationship
                break
        else:
            state.relationships.insert(0, relationship)
        route = PeerRouteSnapshot(
            relationship_id=relationship.relationship_id,
            peer_fingerprint=relationship.peer_fingerprint,
            kind=route_kind,
            transport_id=transport_id,
            bandwidth_class=BandwidthClass.HIGH,
            hop_count=1,
            last_seen_at=now,
        )
        for index, existing in enumerate(state.routes):
            if existing.relationship_id == relationship.relationship_id:
                state.routes[index] = route
                break
        else:
            state.routes.insert(0, route)


def _offline_relationship_id(invite_id: str, inviter_fingerprint: str, responder_fingerprint: str) -> str:
    return f"relationship-{_stable(invite_id)}-{_stable(inviter_fingerprint)}-{_stable(responder_fingerprint)}"


def _stable(value: str) -> str:
    normalized = "".join(character for character in value if character.isalnum())
    return normalized[:12] or "unknown"


def _normalized(value: str) -> str:
    return "".join(character for character in value if character.isalnum()).upper()


def _now_millis() -> int:
    return int(datetime.now(tz=UTC).timestamp() * 1000)
