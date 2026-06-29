from __future__ import annotations

import json
import unittest
from dataclasses import replace
from datetime import UTC, datetime

from kraken_windows.core import (
    BleFrameCodec,
    BleFrameReassembler,
    HandshakeQrCodec,
    KrakenDesktopSimulator,
    KrakenPacket,
    KrakenPacketPolicyError,
    KrakenPacketPolicyValidator,
    LanFrameCodec,
    LanFrameEnvelope,
    MessageStatus,
    PeerRouteKind,
)


class KrakenWindowsCoreSmokeTest(unittest.TestCase):
    def test_simulator_blocks_pending_relationship_and_cycles_route(self) -> None:
        ids = iter(["one", "two", "three", "four", "five"])
        simulator = KrakenDesktopSimulator(
            now=lambda: datetime.fromtimestamp(1_800_000_000, tz=UTC),
            make_id=lambda: next(ids),
        )
        state = simulator.make_initial_state()

        state = simulator.send_message(state, "rel-xiaomi", "hello desktop")
        self.assertEqual(state.messages[-1].status, MessageStatus.SENT_TO_TRANSPORT)

        message_count = len(state.messages)
        state = simulator.send_message(state, "rel-samsung", "blocked")
        self.assertEqual(len(state.messages), message_count)
        self.assertIn("не активна", state.last_event)

        state = simulator.cycle_route(state, "rel-xiaomi")
        route = next(route for route in state.routes if route.relationship_id == "rel-xiaomi")
        self.assertEqual(route.kind, PeerRouteKind.ROUTED_MESH)

    def test_lan_frame_codec_round_trips_android_compatible_envelope(self) -> None:
        now_ms = 1_700_000_000_000
        packet = KrakenPacket(
            packet_id="packet-smoke",
            sender_fingerprint="WINDOWSDESKTOP001",
            recipient_fingerprint="B42B3068934EF618",
            relationship_id="rel-xiaomi",
            conversation_id="desktop-rel-xiaomi",
            message_id="message-smoke",
            created_at_epoch_millis=now_ms,
            expires_at_epoch_millis=now_ms + 300_000,
            payload_json=json.dumps({"message_id": "message-smoke", "body": "hello"}, ensure_ascii=False),
        )
        envelope = LanFrameEnvelope(
            sender_peer_id="windows-desktop",
            sender_fingerprint="WINDOWSDESKTOP001",
            sender_display_name="Windows Kraken",
            sender_reply_port=43191,
            packet=packet,
        )

        frame = LanFrameCodec.encode_envelope(envelope)
        decoded = LanFrameCodec.decode_frame(frame)

        self.assertEqual(decoded, envelope)
        self.assertEqual(int.from_bytes(frame[:4], "big"), len(frame) - 4)

    def test_lan_frame_codec_decodes_android_fixture(self) -> None:
        payload = {
            "frame_version": 1,
            "sender_peer_id": "android-fixture-peer",
            "sender_fingerprint": "ANDROID-FP",
            "sender_display_name": "Xiaomi fixture",
            "sender_reply_port": 54035,
            "packet": {
                "packet_id": "packet-android-fixture",
                "protocol_version": 1,
                "packet_type": "MESSAGE",
                "sender_fingerprint": "ANDROID-FP",
                "recipient_fingerprint": "WINDOWSDESKTOP001",
                "relationship_id": "relationship-android-fixture",
                "conversation_id": "conversation-android-fixture",
                "message_id": "message-android-fixture",
                "created_at_epoch_millis": 1700000000000,
                "expires_at_epoch_millis": 1700000300000,
                "ttl_hops": 4,
                "payload_type": "LOCAL_MESSAGE_JSON",
                "payload_json": "{\"body\":\"hello from Android\",\"message_id\":\"message-android-fixture\"}",
                "crypto_profile_id": "standard-reviewed-primitives-v1",
                "session_profile_id": None,
                "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
                "profile_policy_version": 1,
                "proof_mode": "prototype-placeholder",
            },
        }
        payload_bytes = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
        frame = len(payload_bytes).to_bytes(4, "big") + payload_bytes

        decoded = LanFrameCodec.decode_frame(frame)

        self.assertEqual(decoded.sender_peer_id, "android-fixture-peer")
        self.assertEqual(decoded.sender_reply_port, 54035)
        self.assertEqual(decoded.packet.payload_type, "LOCAL_MESSAGE_JSON")
        self.assertIn("hello from Android", decoded.packet.payload_json)

    def test_qr_codec_encodes_normalizes_and_detects_payload_kind(self) -> None:
        raw = '{"type":"kraken.handshake.response.v1","peer":"windows"}'
        encoded = HandshakeQrCodec.encoded_qr_payload(raw)

        normalized = HandshakeQrCodec.normalized_scanned_payload(encoded)

        self.assertEqual(json.loads(normalized)["peer"], "windows")
        self.assertEqual(HandshakeQrCodec.detect_kind(encoded), "response")

    def test_packet_policy_rejects_duplicate_expired_and_zero_ttl_packets(self) -> None:
        packet = KrakenPacket(
            packet_id="packet-policy",
            sender_fingerprint="A",
            recipient_fingerprint="B",
            relationship_id="rel",
            conversation_id="conv",
            message_id="msg",
            created_at_epoch_millis=1000,
            expires_at_epoch_millis=2000,
            payload_json="{}",
        )
        validator = KrakenPacketPolicyValidator()

        validator.accept_inbound(packet, now_millis=1500)
        with self.assertRaisesRegex(KrakenPacketPolicyError, "duplicate"):
            validator.accept_inbound(packet, now_millis=1500)

        expired = replace(packet, packet_id="expired", expires_at_epoch_millis=1000)
        with self.assertRaisesRegex(KrakenPacketPolicyError, "expired"):
            validator.accept_inbound(expired, now_millis=1500)

        ttl_zero = replace(packet, packet_id="ttl-zero", ttl_hops=0)
        with self.assertRaisesRegex(KrakenPacketPolicyError, "ttl-exhausted"):
            validator.accept_inbound(ttl_zero, now_millis=1500)

    def test_ble_frame_codec_chunks_and_reassembles_packet(self) -> None:
        packet = KrakenPacket(
            packet_id="packet-ble",
            sender_fingerprint="WINDOWSDESKTOP001",
            recipient_fingerprint="ANDROID-FP",
            relationship_id="rel",
            conversation_id="conv",
            message_id="msg-ble",
            created_at_epoch_millis=1000,
            expires_at_epoch_millis=2000,
            payload_json='{"body":"hello over BLE"}',
        )

        chunks = BleFrameCodec.encode_chunks(
            packet,
            sender_peer_id="windows-desktop",
            sender_fingerprint="WINDOWSDESKTOP001",
            sender_display_name="Windows Kraken",
            chunk_payload_bytes=64,
        )
        reassembler = BleFrameReassembler()
        envelope = None
        for chunk_data in chunks:
            envelope = reassembler.accept(BleFrameCodec.decode_chunk(chunk_data)) or envelope

        self.assertIsNotNone(envelope)
        self.assertEqual(envelope.packet, packet)
        self.assertTrue(all(len(chunk) <= BleFrameCodec.max_gatt_write_bytes for chunk in chunks))


if __name__ == "__main__":
    unittest.main()
