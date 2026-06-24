from __future__ import annotations

import base64
import json
import math
import urllib.parse
import uuid
import zlib
from dataclasses import asdict
from typing import Any

from .models import (
    BleFrameChunk,
    KrakenPacket,
    LanFrameEnvelope,
    envelope_from_dict,
    envelope_to_dict,
    packet_from_dict,
    packet_to_dict,
)


def _compact_json(value: str) -> str | None:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError:
        return None
    return json.dumps(parsed, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _json_bytes(value: dict[str, Any]) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _b64url_decode(value: str) -> bytes:
    padded = value + ("=" * (-len(value) % 4))
    return base64.urlsafe_b64decode(padded.encode("ascii"))


class LanFrameCodec:
    max_frame_bytes = 256 * 1024
    ack_byte = 0x06
    length_prefix_bytes = 4

    @classmethod
    def encode_envelope(cls, envelope: LanFrameEnvelope) -> bytes:
        if envelope.sender_fingerprint != envelope.packet.sender_fingerprint:
            raise ValueError("sender_fingerprint_mismatch")
        payload = _json_bytes(envelope_to_dict(envelope))
        if len(payload) > cls.max_frame_bytes:
            raise ValueError("frame_too_large")
        return len(payload).to_bytes(cls.length_prefix_bytes, "big") + payload

    @classmethod
    def decode_envelope_payload(cls, payload: bytes) -> LanFrameEnvelope:
        try:
            decoded = json.loads(payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("malformed_frame") from exc

        try:
            if isinstance(decoded, dict) and "packet" in decoded:
                envelope = envelope_from_dict(decoded)
            else:
                packet = packet_from_dict(decoded)
                envelope = LanFrameEnvelope(
                    sender_peer_id=f"lan-{packet.sender_fingerprint}",
                    sender_fingerprint=packet.sender_fingerprint,
                    packet=packet,
                )
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("malformed_frame") from exc

        if envelope.sender_fingerprint != envelope.packet.sender_fingerprint:
            raise ValueError("sender_fingerprint_mismatch")
        return envelope

    @classmethod
    def decode_frame(cls, frame: bytes) -> LanFrameEnvelope:
        if len(frame) < cls.length_prefix_bytes:
            raise ValueError("invalid_frame_length")
        length = int.from_bytes(frame[: cls.length_prefix_bytes], "big")
        if length <= 0 or length > cls.max_frame_bytes:
            raise ValueError("invalid_frame_length")
        payload = frame[cls.length_prefix_bytes :]
        if len(payload) != length:
            raise ValueError("invalid_frame_length")
        return cls.decode_envelope_payload(payload)


class KrakenPacketPolicyError(ValueError):
    pass


class KrakenPacketPolicyValidator:
    def __init__(self, seen_packet_ids: set[str] | None = None, max_seen_packet_ids: int = 512) -> None:
        self._seen_packet_ids: list[str] = list(seen_packet_ids or set())
        self._max_seen_packet_ids = max_seen_packet_ids

    def accept_inbound(self, packet: KrakenPacket, now_millis: int) -> None:
        packet_id = packet.packet_id.strip()
        if not packet_id:
            raise KrakenPacketPolicyError("packet-policy-missing-packet-id")
        if packet.expires_at_epoch_millis <= now_millis:
            raise KrakenPacketPolicyError(f"packet-policy-expired:{packet_id}")
        if packet.ttl_hops <= 0:
            raise KrakenPacketPolicyError(f"packet-policy-ttl-exhausted:{packet_id}")
        if packet_id in self._seen_packet_ids:
            raise KrakenPacketPolicyError(f"packet-policy-duplicate:{packet_id}")
        self._seen_packet_ids.append(packet_id)
        if len(self._seen_packet_ids) > self._max_seen_packet_ids:
            self._seen_packet_ids = self._seen_packet_ids[-self._max_seen_packet_ids :]


class OutboxBackoffPolicy:
    max_retry_delay = 30.0

    @classmethod
    def retry_delay(cls, attempts: int) -> float:
        if attempts <= 0:
            return 0.0
        return min(math.pow(2.0, attempts), cls.max_retry_delay)


class HandshakeQrCodec:
    invite_type_name = "one_time_invite"
    response_type_name = "kraken.handshake.response.v1"
    confirmation_type_name = "kraken.handshake.confirmation.v1"
    web_scheme = "https"
    web_host = "kraken.local"
    web_path = "/qr"

    @classmethod
    def encoded_qr_payload(cls, raw_payload: str) -> str:
        compact = _compact_json(raw_payload)
        if compact is None:
            raise ValueError("invalid_json_payload")
        compressor = zlib.compressobj(level=9, wbits=-15)
        compressed = compressor.compress(compact.encode("utf-8")) + compressor.flush()
        payload = _b64url_encode(compressed)
        return f"{cls.web_scheme}://{cls.web_host}{cls.web_path}?v=2&z=d&p={payload}"

    @classmethod
    def normalized_scanned_payload(cls, raw_payload: str, *, _depth: int = 0) -> str:
        trimmed = raw_payload.strip()
        if _depth >= 3:
            return trimmed

        try:
            decoded = json.loads(trimmed)
            if isinstance(decoded, str) and cls._looks_wrapped(decoded.strip()):
                return cls.normalized_scanned_payload(decoded.strip(), _depth=_depth + 1)
            if isinstance(decoded, dict):
                for key in ("payload_json", "payload", "data", "qr_payload", "raw_json"):
                    nested = decoded.get(key)
                    if isinstance(nested, str) and cls._looks_wrapped(nested.strip()):
                        return cls.normalized_scanned_payload(nested.strip(), _depth=_depth + 1)
                    if isinstance(nested, dict):
                        return cls.normalized_scanned_payload(
                            json.dumps(nested, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
                            _depth=_depth + 1,
                        )
        except json.JSONDecodeError:
            pass

        uri_payload = cls._payload_from_kraken_uri(trimmed)
        if uri_payload is not None:
            return cls.normalized_scanned_payload(uri_payload, _depth=_depth + 1)

        parsed = urllib.parse.urlparse(trimmed.replace("&amp;", "&").replace("&#38;", "&"))
        query = urllib.parse.parse_qs(parsed.query)
        for key in ("payload_json", "payload", "data", "qr", "json"):
            if query.get(key):
                candidate = cls._normalized_payload_candidate(query[key][0])
                if candidate is not None:
                    return cls.normalized_scanned_payload(candidate, _depth=_depth + 1)

        if parsed.fragment:
            fragment = urllib.parse.unquote(parsed.fragment).strip()
            if cls._looks_wrapped(fragment):
                return cls.normalized_scanned_payload(fragment, _depth=_depth + 1)
            fragment_query = urllib.parse.parse_qs(fragment)
            for key in ("payload_json", "payload", "data", "qr", "json"):
                if fragment_query.get(key):
                    candidate = cls._normalized_payload_candidate(fragment_query[key][0])
                    if candidate is not None:
                        return cls.normalized_scanned_payload(candidate, _depth=_depth + 1)

        percent_decoded = urllib.parse.unquote(trimmed)
        if percent_decoded != trimmed and cls._looks_wrapped(percent_decoded):
            return cls.normalized_scanned_payload(percent_decoded, _depth=_depth + 1)

        candidate = cls._decode_base64_payload(trimmed)
        if candidate is not None and cls._looks_wrapped(candidate):
            return cls.normalized_scanned_payload(candidate, _depth=_depth + 1)

        return trimmed

    @classmethod
    def detect_kind(cls, raw_payload: str) -> str:
        normalized = cls.normalized_scanned_payload(raw_payload)
        try:
            decoded = json.loads(normalized)
        except json.JSONDecodeError:
            return "invalid"
        if not isinstance(decoded, dict):
            return "invalid"
        payload_type = str(decoded.get("type", "")).strip()
        if payload_type == cls.invite_type_name:
            return "invite"
        if payload_type == cls.response_type_name:
            return "response"
        if payload_type == cls.confirmation_type_name:
            return "confirmation"
        return "unknown" if payload_type else "unknown"

    @staticmethod
    def payload_summary(raw_payload: str) -> str:
        normalized = HandshakeQrCodec.normalized_scanned_payload(raw_payload)
        try:
            decoded = json.loads(normalized)
        except json.JSONDecodeError:
            return "Полезная нагрузка не является JSON-объектом."
        if not isinstance(decoded, dict):
            return "Полезная нагрузка не является JSON-объектом."
        payload_type = str(decoded.get("type") or "без типа")
        keys = ", ".join(sorted(decoded.keys())[:12]) or "нет полей"
        return f"Тип: {payload_type}. Поля: {keys}."

    @classmethod
    def _payload_from_kraken_uri(cls, payload: str) -> str | None:
        parsed = urllib.parse.urlparse(payload.replace("&amp;", "&").replace("&#38;", "&"))
        scheme = parsed.scheme.lower()
        host = parsed.netloc.lower()
        is_kraken_uri = scheme == "kraken" and host == "qr"
        is_intent_uri = scheme == "intent" and host == "qr" and "scheme=kraken" in parsed.fragment.lower()
        is_web_uri = scheme == cls.web_scheme and host == cls.web_host and parsed.path == cls.web_path
        if not (is_kraken_uri or is_intent_uri or is_web_uri):
            if payload.lower().startswith("kraken:"):
                tail = urllib.parse.unquote(payload[len("kraken:") :].strip("/ "))
                return tail if cls._looks_wrapped(tail) else None
            return None

        query = urllib.parse.parse_qs(parsed.query)
        encoded = (query.get("p") or query.get("payload") or [None])[0]
        if not encoded:
            return None
        try:
            data = _b64url_decode(encoded)
            if (query.get("z") or [""])[0] == "d":
                data = zlib.decompress(data, wbits=-15)
            decoded = data.decode("utf-8").strip()
        except (ValueError, zlib.error, UnicodeDecodeError):
            return None
        return _compact_json(decoded) or decoded

    @staticmethod
    def _normalized_payload_candidate(value: str) -> str | None:
        trimmed = urllib.parse.unquote(value).strip()
        if HandshakeQrCodec._looks_wrapped(trimmed):
            return trimmed
        decoded = HandshakeQrCodec._decode_base64_payload(trimmed)
        if decoded is not None and HandshakeQrCodec._looks_wrapped(decoded):
            return decoded
        return None

    @staticmethod
    def _decode_base64_payload(value: str) -> str | None:
        try:
            return _b64url_decode(value).decode("utf-8").strip()
        except (ValueError, UnicodeDecodeError):
            try:
                return base64.b64decode(value).decode("utf-8").strip()
            except (ValueError, UnicodeDecodeError):
                return None

    @staticmethod
    def _looks_wrapped(value: str) -> bool:
        lowered = value.strip().lower()
        return (
            lowered.startswith("{")
            or lowered.startswith('"')
            or lowered.startswith("kraken:")
            or lowered.startswith("kraken://")
            or lowered.startswith("intent://")
            or lowered.startswith("https://kraken.local/")
        )


class BleFrameCodec:
    service_uuid = "58a1257c-f4a8-48c8-99d5-917b9863d7c4"
    identity_characteristic_uuid = "58a1257d-f4a8-48c8-99d5-917b9863d7c4"
    packet_characteristic_uuid = "58a1257e-f4a8-48c8-99d5-917b9863d7c4"
    max_packet_bytes = 32 * 1024
    default_chunk_payload_bytes = 24
    max_gatt_write_bytes = 512

    @classmethod
    def encode_chunks(
        cls,
        packet: KrakenPacket,
        sender_peer_id: str,
        sender_fingerprint: str,
        sender_display_name: str | None = None,
        chunk_payload_bytes: int = default_chunk_payload_bytes,
    ) -> list[bytes]:
        if not 1 <= chunk_payload_bytes <= 1024:
            raise ValueError("invalid_chunk_payload_size")
        if packet.sender_fingerprint != sender_fingerprint:
            raise ValueError("sender_fingerprint_mismatch")
        payload = _json_bytes(packet_to_dict(packet))
        if not 0 < len(payload) <= cls.max_packet_bytes:
            raise ValueError("packet_too_large")
        transfer_id = f"{packet.packet_id}-{uuid.uuid4()}"
        payload_crc32 = zlib.crc32(payload) & 0xFFFFFFFF
        chunk_count = (len(payload) + chunk_payload_bytes - 1) // chunk_payload_bytes
        chunks: list[bytes] = []

        for index in range(chunk_count):
            start = index * chunk_payload_bytes
            end = min(start + chunk_payload_bytes, len(payload))
            chunk = BleFrameChunk(
                transfer_id=transfer_id,
                sender_peer_id=sender_peer_id,
                sender_fingerprint=sender_fingerprint,
                sender_display_name=sender_display_name,
                chunk_index=index,
                chunk_count=chunk_count,
                payload_size=len(payload),
                payload_crc32=payload_crc32,
                payload_base64=base64.b64encode(payload[start:end]).decode("ascii"),
            )
            data = _json_bytes(_chunk_to_dict(chunk))
            if len(data) > cls.max_gatt_write_bytes:
                raise ValueError("packet_too_large")
            chunks.append(data)
        return chunks

    @classmethod
    def decode_chunk(cls, data: bytes) -> BleFrameChunk:
        try:
            value = json.loads(data.decode("utf-8"))
            chunk = _chunk_from_dict(value)
        except (UnicodeDecodeError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
            raise ValueError("malformed_chunk") from exc
        cls._validate(chunk)
        return chunk

    @classmethod
    def _validate(cls, chunk: BleFrameChunk) -> None:
        if chunk.frame_version != 1:
            raise ValueError("unsupported_frame_version")
        if not chunk.transfer_id:
            raise ValueError("missing_transfer_id")
        if not chunk.sender_fingerprint:
            raise ValueError("missing_sender_fingerprint")
        if not 1 <= chunk.chunk_count <= 512:
            raise ValueError("invalid_chunk_count")
        if not 0 <= chunk.chunk_index < chunk.chunk_count:
            raise ValueError("invalid_chunk_index")
        if not 1 <= chunk.payload_size <= cls.max_packet_bytes:
            raise ValueError("invalid_payload_size")
        try:
            base64.b64decode(chunk.payload_base64)
        except ValueError as exc:
            raise ValueError("malformed_chunk") from exc


class BleFrameReassembler:
    def __init__(self) -> None:
        self._pending: dict[str, dict[str, Any]] = {}

    def accept(self, chunk: BleFrameChunk) -> LanFrameEnvelope | None:
        pending = self._pending.setdefault(
            chunk.transfer_id,
            {
                "sender_peer_id": chunk.sender_peer_id,
                "sender_fingerprint": chunk.sender_fingerprint,
                "sender_display_name": chunk.sender_display_name,
                "chunk_count": chunk.chunk_count,
                "payload_size": chunk.payload_size,
                "payload_crc32": chunk.payload_crc32,
                "chunks": {},
            },
        )
        if (
            pending["sender_peer_id"] != chunk.sender_peer_id
            or pending["sender_fingerprint"] != chunk.sender_fingerprint
            or pending["chunk_count"] != chunk.chunk_count
            or pending["payload_size"] != chunk.payload_size
            or pending["payload_crc32"] != chunk.payload_crc32
        ):
            raise ValueError("transfer_metadata_mismatch")

        pending["chunks"][chunk.chunk_index] = base64.b64decode(chunk.payload_base64)
        if len(pending["chunks"]) != pending["chunk_count"]:
            return None

        del self._pending[chunk.transfer_id]
        payload = b"".join(pending["chunks"][index] for index in range(pending["chunk_count"]))
        if len(payload) != pending["payload_size"]:
            raise ValueError("payload_size_mismatch")
        if (zlib.crc32(payload) & 0xFFFFFFFF) != pending["payload_crc32"]:
            raise ValueError("payload_checksum_mismatch")
        packet = packet_from_dict(json.loads(payload.decode("utf-8")))
        if packet.sender_fingerprint != pending["sender_fingerprint"]:
            raise ValueError("sender_fingerprint_mismatch")
        return LanFrameEnvelope(
            sender_peer_id=pending["sender_peer_id"],
            sender_fingerprint=pending["sender_fingerprint"],
            sender_display_name=pending["sender_display_name"],
            packet=packet,
        )


def _chunk_to_dict(chunk: BleFrameChunk) -> dict[str, Any]:
    return {
        "frame_version": chunk.frame_version,
        "transfer_id": chunk.transfer_id,
        "sender_peer_id": chunk.sender_peer_id,
        "sender_fingerprint": chunk.sender_fingerprint,
        "sender_display_name": chunk.sender_display_name,
        "chunk_index": chunk.chunk_index,
        "chunk_count": chunk.chunk_count,
        "payload_size": chunk.payload_size,
        "payload_crc32": chunk.payload_crc32,
        "payload_base64": chunk.payload_base64,
    }


def _chunk_from_dict(value: dict[str, Any]) -> BleFrameChunk:
    raw = asdict(
        BleFrameChunk(
            frame_version=int(value.get("frame_version", 1)),
            transfer_id=str(value["transfer_id"]),
            sender_peer_id=str(value["sender_peer_id"]),
            sender_fingerprint=str(value["sender_fingerprint"]),
            sender_display_name=value.get("sender_display_name"),
            chunk_index=int(value["chunk_index"]),
            chunk_count=int(value["chunk_count"]),
            payload_size=int(value["payload_size"]),
            payload_crc32=int(value["payload_crc32"]),
            payload_base64=str(value["payload_base64"]),
        )
    )
    return BleFrameChunk(**raw)
