from __future__ import annotations

from typing import Any

from disser_messenger.mesh.desktop_lan_relay import DesktopLanRelay, LanPeer, RelayAttackMode


def make_packet(
    *,
    packet_id: str = "packet-1",
    recipient_fingerprint: str = "BOB-FP",
    ttl_hops: int = 4,
    expires_at_epoch_millis: int = 2_000_000_000_000,
) -> dict[str, Any]:
    return {
        "packet_id": packet_id,
        "protocol_version": 1,
        "packet_type": "MESSAGE",
        "sender_fingerprint": "ALICE-FP",
        "recipient_fingerprint": recipient_fingerprint,
        "relationship_id": "relationship-1",
        "conversation_id": "conversation-1",
        "message_id": "message-1",
        "created_at_epoch_millis": 1_700_000_000_000,
        "expires_at_epoch_millis": expires_at_epoch_millis,
        "ttl_hops": ttl_hops,
        "payload_type": "LOCAL_MESSAGE_JSON",
        "payload_json": "{\"message_id\":\"message-1\",\"body\":\"hello\"}",
        "crypto_profile_id": "kraken-research-mesh-lc32-prime-offsets",
        "proof_mode": "verified-hmac-sha256-prototype",
        "auth_algorithm": "hmac-sha256-prototype-auth",
        "auth_tag": "prototype-tag",
    }


def relay_with_sent_packets(
    attack_mode: RelayAttackMode,
) -> tuple[DesktopLanRelay, list[tuple[str, int, dict[str, Any]]]]:
    sent: list[tuple[str, int, dict[str, Any]]] = []
    relay = DesktopLanRelay(
        fingerprint="DESKTOP-RELAY-FP",
        attack_mode=attack_mode,
        now_epoch_millis=lambda: 1_700_000_000_100,
        send_fn=lambda host, port, packet: sent.append((host, port, packet)) is None or True,
    )
    relay.remember_peer(LanPeer("BOB-FP", "192.168.1.50", 43191))
    return relay, sent


def test_normal_mode_forwards_once_and_decrements_ttl() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.NORMAL)

    decision = relay.handle_packet(make_packet(), source_host="192.168.1.10")

    assert decision.accepted is True
    assert decision.forwarded is True
    assert decision.dropped is False
    assert decision.reason == "attack-normal"
    assert len(sent) == 1
    assert sent[0][0] == "192.168.1.50"
    assert sent[0][1] == 43191
    assert sent[0][2]["ttl_hops"] == 3
    assert sent[0][2]["payload_json"] == "{\"message_id\":\"message-1\",\"body\":\"hello\"}"
    assert relay.stats.accepted == 1
    assert relay.stats.forwarded == 1


def test_drop_mode_accepts_but_sends_nothing() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.DROP)

    decision = relay.handle_packet(make_packet(), source_host="192.168.1.10")

    assert decision.accepted is True
    assert decision.forwarded is False
    assert decision.dropped is True
    assert decision.reason == "attack-drop"
    assert sent == []
    assert relay.stats.accepted == 1
    assert relay.stats.dropped == 1
    assert relay.stats.forwarded == 0


def test_duplicate_mode_sends_two_copies_with_decremented_ttl() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.DUPLICATE)

    decision = relay.handle_packet(make_packet(), source_host="192.168.1.10")

    assert decision.accepted is True
    assert decision.forwarded is True
    assert decision.reason == "attack-duplicate"
    assert len(sent) == 2
    assert [packet["ttl_hops"] for _host, _port, packet in sent] == [3, 3]
    assert [packet["packet_id"] for _host, _port, packet in sent] == ["packet-1", "packet-1"]
    assert relay.stats.accepted == 1
    assert relay.stats.forwarded == 1
    assert relay.stats.duplicated == 1


def test_tamper_mode_mutates_payload_before_forwarding() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.TAMPER)

    decision = relay.handle_packet(make_packet(), source_host="192.168.1.10")

    assert decision.accepted is True
    assert decision.forwarded is True
    assert decision.reason == "attack-tamper"
    assert len(sent) == 1
    assert sent[0][2]["ttl_hops"] == 3
    assert sent[0][2]["payload_json"] == "{\"tampered\":true}"
    assert relay.stats.accepted == 1
    assert relay.stats.forwarded == 1
    assert relay.stats.tampered == 1


def test_unknown_recipient_is_queued_without_forwarding() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.NORMAL)

    decision = relay.handle_packet(
        make_packet(recipient_fingerprint="UNKNOWN-FP"),
        source_host="192.168.1.10",
    )

    assert decision.accepted is True
    assert decision.forwarded is False
    assert decision.queued is True
    assert decision.reason == "recipient-not-seen-yet"
    assert sent == []
    assert relay.stats.queued == 1
    assert relay.stats.accepted == 0


def test_expired_packet_is_rejected_before_attack_mode() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.DUPLICATE)

    decision = relay.handle_packet(
        make_packet(expires_at_epoch_millis=1_700_000_000_100),
        source_host="192.168.1.10",
    )

    assert decision.accepted is False
    assert decision.forwarded is False
    assert decision.dropped is True
    assert decision.reason == "packet-expired"
    assert sent == []
    assert relay.stats.rejected == 1
    assert relay.stats.duplicated == 0


def test_ttl_exhausted_packet_is_rejected_before_attack_mode() -> None:
    relay, sent = relay_with_sent_packets(RelayAttackMode.TAMPER)

    decision = relay.handle_packet(make_packet(ttl_hops=0), source_host="192.168.1.10")

    assert decision.accepted is False
    assert decision.forwarded is False
    assert decision.dropped is True
    assert decision.reason == "ttl-exhausted"
    assert sent == []
    assert relay.stats.rejected == 1
    assert relay.stats.tampered == 0
