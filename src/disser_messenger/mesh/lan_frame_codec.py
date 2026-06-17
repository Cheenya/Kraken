from __future__ import annotations

import json
import struct
from dataclasses import dataclass
from typing import Any, BinaryIO


MAX_FRAME_BYTES = 256 * 1024
LENGTH_PREFIX_BYTES = 4


@dataclass(frozen=True, slots=True)
class LanFrameEnvelope:
    sender_peer_id: str
    sender_fingerprint: str
    packet: dict[str, Any]
    sender_display_name: str | None = None
    sender_reply_port: int | None = None
    frame_version: int = 1


def encode_packet_frame(packet: dict[str, Any]) -> bytes:
    return _encode_payload(packet)


def encode_envelope_frame(envelope: LanFrameEnvelope) -> bytes:
    payload: dict[str, Any] = {
        "frame_version": envelope.frame_version,
        "sender_peer_id": envelope.sender_peer_id,
        "sender_fingerprint": envelope.sender_fingerprint,
        "packet": envelope.packet,
    }
    if envelope.sender_display_name is not None:
        payload["sender_display_name"] = envelope.sender_display_name
    if envelope.sender_reply_port is not None:
        payload["sender_reply_port"] = envelope.sender_reply_port
    return _encode_payload(payload)


def decode_frame(stream: BinaryIO) -> LanFrameEnvelope:
    payload = _read_payload(stream)
    decoded = json.loads(payload.decode("utf-8"))
    if isinstance(decoded, dict) and isinstance(decoded.get("packet"), dict):
        envelope = LanFrameEnvelope(
            frame_version=int(decoded.get("frame_version", 1)),
            sender_peer_id=str(decoded.get("sender_peer_id", "")),
            sender_fingerprint=str(decoded.get("sender_fingerprint", "")),
            sender_display_name=_optional_str(decoded.get("sender_display_name")),
            sender_reply_port=_optional_int(decoded.get("sender_reply_port")),
            packet=decoded["packet"],
        )
        if envelope.sender_fingerprint != str(envelope.packet.get("sender_fingerprint", "")):
            raise ValueError("LAN frame sender fingerprint does not match packet sender.")
        return envelope

    if not isinstance(decoded, dict):
        raise ValueError("Malformed Kraken packet frame.")
    sender_fingerprint = str(decoded.get("sender_fingerprint", ""))
    return LanFrameEnvelope(
        sender_peer_id=f"lan-{sender_fingerprint}",
        sender_fingerprint=sender_fingerprint,
        packet=decoded,
    )


def _encode_payload(payload: dict[str, Any]) -> bytes:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if len(raw) > MAX_FRAME_BYTES:
        raise ValueError(f"Kraken packet frame exceeds {MAX_FRAME_BYTES} bytes.")
    return struct.pack(">I", len(raw)) + raw


def _read_payload(stream: BinaryIO) -> bytes:
    length_prefix = _read_exact(stream, LENGTH_PREFIX_BYTES)
    length = struct.unpack(">I", length_prefix)[0]
    if length < 1 or length > MAX_FRAME_BYTES:
        raise ValueError("Invalid Kraken packet frame length.")
    return _read_exact(stream, length)


def _read_exact(stream: BinaryIO, length: int) -> bytes:
    chunks: list[bytes] = []
    remaining = length
    while remaining > 0:
        chunk = stream.read(remaining)
        if not chunk:
            raise ValueError("Unexpected end of Kraken packet frame.")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def _optional_str(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    return text if text else None


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)
