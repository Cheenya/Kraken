import pytest

from disser_messenger.mesh.delivery_simulator import SimNode, meet


def test_a_to_b_to_c_to_y_delivery_receipt_and_tombstone_flow() -> None:
    a = SimNode("A")
    b = SimNode("B")
    c = SimNode("C")
    y = SimNode("Y")

    a.create_packet(
        packet_id="packet-1",
        message_id="message-1",
        recipient_id="Y",
        encrypted_payload=b"ciphertext",
        ttl_hops=5,
        expires_at=100,
        copy_budget=6,
        now=1,
    )

    meet(a, b, now=2)
    meet(b, c, now=3)
    meet(c, y, now=4)

    assert "packet-1" in y.inbox
    assert y.read_payload("packet-1") == b"ciphertext"

    meet(y, c, now=5)
    meet(c, b, now=6)
    meet(b, a, now=7)

    assert "packet-1" in a.delivered_receipts

    a.create_tombstone("packet-1")
    meet(a, b, now=8)
    meet(b, c, now=9)
    meet(c, y, now=10)

    assert "packet-1" not in a.buffer.packets
    assert "packet-1" not in b.buffer.packets
    assert "packet-1" not in c.buffer.packets
    assert "packet-1" not in y.buffer.packets


def test_expired_packets_are_deleted() -> None:
    a = SimNode("A")
    b = SimNode("B")
    a.create_packet(
        packet_id="expired",
        message_id="message-1",
        recipient_id="B",
        encrypted_payload=b"ciphertext",
        ttl_hops=1,
        expires_at=5,
        copy_budget=2,
        now=1,
    )

    meet(a, b, now=5)

    assert a.buffer.packet_count() == 0
    assert b.buffer.packet_count() == 0


def test_duplicate_packets_are_ignored() -> None:
    a = SimNode("A")
    b = SimNode("B")
    packet = a.create_packet(
        packet_id="packet-1",
        message_id="message-1",
        recipient_id="B",
        encrypted_payload=b"ciphertext",
        ttl_hops=3,
        expires_at=100,
        copy_budget=3,
        now=1,
    )

    assert b.buffer.add_packet(packet, now=2)
    assert not b.buffer.add_packet(packet, now=2)


def test_relays_cannot_read_encrypted_payload_placeholder() -> None:
    a = SimNode("A")
    b = SimNode("B")
    a.create_packet(
        packet_id="packet-1",
        message_id="message-1",
        recipient_id="Y",
        encrypted_payload=b"ciphertext",
        ttl_hops=3,
        expires_at=100,
        copy_budget=3,
        now=1,
    )
    meet(a, b, now=2)

    with pytest.raises(PermissionError):
        b.read_payload("packet-1")


def test_copy_budget_decreases_during_forwarding() -> None:
    a = SimNode("A")
    b = SimNode("B")
    a.create_packet(
        packet_id="packet-1",
        message_id="message-1",
        recipient_id="B",
        encrypted_payload=b"ciphertext",
        ttl_hops=3,
        expires_at=100,
        copy_budget=3,
        now=1,
    )

    meet(a, b, now=2)

    assert a.buffer.packets["packet-1"].copy_budget == 2
    assert b.buffer.packets["packet-1"].copy_budget == 2
