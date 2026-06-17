from __future__ import annotations

import io
import struct

import pytest

from disser_messenger.mesh.lan_frame_codec import (
    MAX_FRAME_BYTES,
    LanFrameEnvelope,
    decode_frame,
    encode_envelope_frame,
    encode_packet_frame,
)


def packet(payload_json: str = "{}") -> dict[str, object]:
    return {
        "packet_id": "packet-lan",
        "protocol_version": 1,
        "packet_type": "PING",
        "sender_fingerprint": "ALICE-FP",
        "recipient_fingerprint": "BOB-FP",
        "relationship_id": "relationship-1",
        "conversation_id": "conversation-1",
        "message_id": None,
        "created_at_epoch_millis": 1_700_000_000_000,
        "expires_at_epoch_millis": 1_700_000_060_000,
        "ttl_hops": 4,
        "payload_type": "PING_JSON",
        "payload_json": payload_json,
    }


def test_android_lan_envelope_round_trips_reply_hint() -> None:
    envelope = LanFrameEnvelope(
        sender_peer_id="local-alice",
        sender_fingerprint="ALICE-FP",
        sender_display_name="Alice",
        sender_reply_port=42001,
        packet=packet(),
    )

    decoded = decode_frame(io.BytesIO(encode_envelope_frame(envelope)))

    assert decoded == envelope


def test_legacy_packet_frame_decodes_as_envelope() -> None:
    decoded = decode_frame(io.BytesIO(encode_packet_frame(packet())))

    assert decoded.sender_peer_id == "lan-ALICE-FP"
    assert decoded.sender_fingerprint == "ALICE-FP"
    assert decoded.sender_reply_port is None
    assert decoded.packet == packet()


def test_envelope_sender_must_match_packet_sender() -> None:
    frame = encode_envelope_frame(
        LanFrameEnvelope(
            sender_peer_id="spoofed",
            sender_fingerprint="MALLORY-FP",
            packet=packet(),
        ),
    )

    with pytest.raises(ValueError, match="sender fingerprint"):
        decode_frame(io.BytesIO(frame))


def test_oversized_packet_frame_is_rejected_before_send() -> None:
    with pytest.raises(ValueError, match="exceeds"):
        encode_packet_frame(packet(payload_json="x" * (MAX_FRAME_BYTES + 1)))


def test_invalid_length_prefix_is_rejected() -> None:
    with pytest.raises(ValueError, match="Invalid Kraken packet frame length"):
        decode_frame(io.BytesIO(struct.pack(">I", MAX_FRAME_BYTES + 1)))
