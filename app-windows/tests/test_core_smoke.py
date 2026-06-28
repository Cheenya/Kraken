from __future__ import annotations

import json
import tempfile
import time
import unittest
from dataclasses import replace
from datetime import UTC, datetime
from pathlib import Path

from kraken_windows.core import (
    BleFrameCodec,
    BleFrameReassembler,
    BleEventDirection,
    BleEventStatus,
    BleTimelineReducer,
    BleTransferEvent,
    DurableOutboxStore,
    HandshakeQrCodec,
    HandshakeImportService,
    KrakenDesktopSimulator,
    KrakenPacket,
    KrakenPacketPolicyError,
    KrakenPacketPolicyValidator,
    JsonStateStore,
    LanEndpoint,
    LanFrameCodec,
    LanFrameEnvelope,
    LanTimelineReducer,
    LanTransferEvent,
    LanEventDirection,
    LanEventStatus,
    WindowsLanTcpListener,
    WindowsLanTcpSender,
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
                "proof_mode": "local-admission-check-v1",
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

    def test_state_store_and_outbox_persist_desktop_state_and_retry_records(self) -> None:
        simulator = KrakenDesktopSimulator(now=lambda: datetime.fromtimestamp(1_800_000_000, tz=UTC))
        state = simulator.make_initial_state()
        state = simulator.create_identity(state, "Windows tester")
        state = simulator.send_message(state, "rel-xiaomi", "persist me")

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            state_store = JsonStateStore(root)
            outbox_store = DurableOutboxStore(root / "outbox.json")

            state_store.save_state(state)
            outbox_store.enqueue(state.messages[-1], ttl_seconds=300)
            outbox_store.mark_attempt(state.messages[-1].message_id, now=datetime.fromtimestamp(1_800_000_001, tz=UTC))

            loaded_state = state_store.load_state()
            loaded_outbox = DurableOutboxStore(root / "outbox.json")
            loaded_record = loaded_outbox.records[state.messages[-1].message_id]

        self.assertIsNotNone(loaded_state.local_identity)
        self.assertEqual(loaded_state.local_identity.display_name, "Windows tester")
        self.assertEqual(loaded_state.messages[-1].message_id, state.messages[-1].message_id)
        self.assertEqual(loaded_record.message_id, state.messages[-1].message_id)
        self.assertEqual(loaded_record.attempts, 1)
        self.assertGreaterEqual(loaded_record.next_attempt_at, datetime.fromtimestamp(1_800_000_003, tz=UTC))

    def test_lan_and_ble_timeline_reducers_update_messages_and_unknown_inbound_peers(self) -> None:
        simulator = KrakenDesktopSimulator(now=lambda: datetime.fromtimestamp(1_800_000_000, tz=UTC))
        state = simulator.make_initial_state()
        state = simulator.send_message(state, "rel-xiaomi", "ack me")
        outgoing_id = state.messages[-1].message_id

        ack_event = LanTransferEvent(
            direction=LanEventDirection.OUTBOUND,
            status=LanEventStatus.ACKED,
            at_epoch_millis=1_800_000_001_000,
            source="windows:43191",
            target="127.0.0.1:54035",
            packet_id="packet-ack",
            message_id=outgoing_id,
            sender_fingerprint="WINDOWSDESKTOP001",
            recipient_fingerprint="A17C9E2048F0DA11",
            relationship_id="rel-xiaomi",
        )
        state, selected = LanTimelineReducer.apply(ack_event, state)
        self.assertIsNone(selected)
        self.assertEqual(state.messages[-1].status, MessageStatus.DELIVERED_TO_PEER)

        inbound_event = LanTransferEvent(
            direction=LanEventDirection.INBOUND,
            status=LanEventStatus.ACCEPTED,
            at_epoch_millis=1_800_000_002_000,
            source="127.0.0.1:54035",
            target="0.0.0.0:43191",
            packet_id="packet-inbound",
            message_id="message-inbound",
            payload_json='{"message_id":"message-inbound","body":"hello from phone"}',
            sender_display_name="Android phone",
            sender_fingerprint="ANDROID-FP-2",
            recipient_fingerprint="WINDOWSDESKTOP001",
            relationship_id=None,
        )
        state, selected = LanTimelineReducer.apply(inbound_event, state)

        self.assertEqual(selected, "rel-lan-ANDROID-FP-2")
        self.assertTrue(any(message.body == "hello from phone" for message in state.messages))
        self.assertTrue(
            any(route.relationship_id == "rel-lan-ANDROID-FP-2" and route.kind == PeerRouteKind.DIRECT_LAN for route in state.routes)
        )

        ble_event = BleTransferEvent(
            direction=BleEventDirection.INBOUND,
            status=BleEventStatus.ACCEPTED,
            at_epoch_millis=1_800_000_003_000,
            peer_fingerprint="BLE-FP-1",
            packet_id="packet-ble-inbound",
            message_id="message-ble-inbound",
            payload_json='{"message_id":"message-ble-inbound","body":"hello from BLE"}',
            sender_display_name="BLE Android",
            sender_fingerprint="BLE-FP-1",
            recipient_fingerprint="WINDOWSDESKTOP001",
        )
        ble_state, _ = BleTimelineReducer.apply(ble_event, state)
        self.assertTrue(any(route.peer_fingerprint == "BLE-FP-1" and route.kind == PeerRouteKind.DIRECT_BLE for route in ble_state.routes))

    def test_windows_lan_transport_sends_frame_and_listener_acks_inbound_event(self) -> None:
        now_ms = 1_800_000_000_000
        packet = KrakenPacket(
            packet_id="packet-loopback",
            sender_fingerprint="WINDOWSDESKTOP001",
            recipient_fingerprint="ANDROID-FP",
            relationship_id="rel-loopback",
            conversation_id="conv-loopback",
            message_id="message-loopback",
            created_at_epoch_millis=now_ms,
            expires_at_epoch_millis=now_ms + 300_000,
            payload_json='{"body":"hello loopback","message_id":"message-loopback"}',
        )
        envelope = LanFrameEnvelope(
            sender_peer_id="windows-desktop",
            sender_fingerprint="WINDOWSDESKTOP001",
            sender_display_name="Windows Kraken",
            sender_reply_port=43191,
            packet=packet,
        )
        inbound_events = []
        listener = WindowsLanTcpListener(now=lambda: now_ms)
        try:
            listener.start("127.0.0.1", 0, inbound_events.append)
            sender = WindowsLanTcpSender(now=lambda: now_ms + 1)
            outbound_event = sender.send(
                envelope,
                LanEndpoint(host="127.0.0.1", port=listener.local_port, fingerprint="ANDROID-FP"),
                timeout_seconds=2,
            )
            deadline = time.time() + 2
            while not inbound_events and time.time() < deadline:
                time.sleep(0.01)
        finally:
            listener.stop()

        self.assertEqual(outbound_event.status, LanEventStatus.ACKED)
        self.assertEqual(inbound_events[0].status, LanEventStatus.ACCEPTED)
        self.assertEqual(inbound_events[0].message_id, "message-loopback")

    def test_handshake_import_service_imports_invite_and_rejects_expired_or_self_qr(self) -> None:
        simulator = KrakenDesktopSimulator(now=lambda: datetime.fromtimestamp(1_800_000_000, tz=UTC))
        state = simulator.make_initial_state()
        state = simulator.create_identity(state, "Windows tester")
        identity = state.local_identity
        payload = {
            "type": "one_time_invite",
            "version": 1,
            "invite_id": "invite-1",
            "inviter_display_name": "Android peer",
            "inviter_public_key_encoded": "android-public-key",
            "inviter_fingerprint": "ANDROID-FP-INVITE",
            "crypto_profile_id": "standard-reviewed-primitives-v1",
            "admission_decision_hash": "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
            "profile_policy_version": 1,
            "expires_at_epoch_millis": 1_800_000_300_000,
        }
        encoded = HandshakeQrCodec.encoded_qr_payload(json.dumps(payload))

        error = HandshakeImportService.import_payload(state, encoded, identity=identity, now_millis=1_800_000_000_000)

        self.assertIsNone(error)
        imported = next(relationship for relationship in state.relationships if relationship.peer_fingerprint == "ANDROID-FP-INVITE")
        self.assertEqual(imported.peer_display_name, "Android peer")
        self.assertEqual(imported.state.value, "ACTIVE")
        self.assertTrue(any(route.relationship_id == imported.relationship_id and route.kind == PeerRouteKind.DIRECT_LAN for route in state.routes))

        expired = {**payload, "invite_id": "invite-expired", "expires_at_epoch_millis": 1_799_999_999_000}
        error = HandshakeImportService.import_payload(state, json.dumps(expired), identity=identity, now_millis=1_800_000_000_000)
        self.assertIn("истекло", error)

        own = {
            **payload,
            "invite_id": "invite-self",
            "inviter_fingerprint": identity.fingerprint,
            "inviter_public_key_encoded": identity.public_key_encoded,
        }
        error = HandshakeImportService.import_payload(state, json.dumps(own), identity=identity, now_millis=1_800_000_000_000)
        self.assertIn("собственный", error)


if __name__ == "__main__":
    unittest.main()
