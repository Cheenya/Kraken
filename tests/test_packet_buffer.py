from disser_messenger.mesh.packet_buffer import (
    ForwardingPolicy,
    Packet,
    PacketKind,
    PacketScope,
    ReserveCopyPolicy,
    TransitBuffer,
)


def test_add_packet_and_reject_duplicate() -> None:
    buffer = TransitBuffer()
    packet = make_packet()

    assert buffer.add(packet, now=1)
    assert not buffer.add(packet, now=1)
    assert len(buffer) == 1


def test_expire_packet() -> None:
    buffer = TransitBuffer()
    packet = make_packet(expires_at=5)
    buffer.add(packet, now=1)

    assert buffer.expire(now=5) == [packet.packet_id]
    assert len(buffer) == 0


def test_forwarding_reduces_copy_budget_and_ttl() -> None:
    buffer = TransitBuffer()
    packet = make_packet(copy_budget=4, ttl_hops=3)
    buffer.add(packet, now=1)

    forwarded = buffer.forward_copy(packet.packet_id, relay_score=0.9, policy=ForwardingPolicy())

    assert forwarded is not None
    assert forwarded.copy_budget == 3
    assert forwarded.ttl_hops == 2
    stored = buffer.get(packet.packet_id)
    assert stored is not None
    assert stored.copy_budget == 3


def test_reserve_copy_rule_blocks_low_score_relay() -> None:
    buffer = TransitBuffer()
    packet = make_packet(copy_budget=2, min_reserve=1, social_reserve=2)
    buffer.add(packet, now=1)

    forwarded = buffer.forward_copy(
        packet.packet_id,
        relay_score=0.2,
        policy=ForwardingPolicy(reserve=ReserveCopyPolicy(high_score_threshold=0.7)),
    )

    assert forwarded is None
    assert buffer.get(packet.packet_id) == packet


def test_reserve_copy_rule_allows_high_score_relay() -> None:
    buffer = TransitBuffer()
    packet = make_packet(copy_budget=2, min_reserve=1, social_reserve=2)
    buffer.add(packet, now=1)

    forwarded = buffer.forward_copy(
        packet.packet_id,
        relay_score=0.95,
        policy=ForwardingPolicy(reserve=ReserveCopyPolicy(high_score_threshold=0.7)),
    )

    assert forwarded is not None


def test_packet_model_has_no_public_discovery_fields() -> None:
    forbidden = {"public_discovery", "nearby_discovery", "global_search", "discovery"}
    packet_fields = set(Packet.__dataclass_fields__.keys())
    scope_fields = set(PacketScope.__dataclass_fields__.keys())

    assert packet_fields.isdisjoint(forbidden)
    assert scope_fields.isdisjoint(forbidden)


def make_packet(
    packet_id: str = "packet-1",
    expires_at: int = 100,
    copy_budget: int = 4,
    ttl_hops: int = 4,
    min_reserve: int = 1,
    social_reserve: int = 1,
) -> Packet:
    return Packet(
        packet_id=packet_id,
        message_id="message-1",
        realm_id="realm-1",
        scope=PacketScope(realm_id="realm-1", kind=PacketKind.DIRECT),
        ttl_hops=ttl_hops,
        expires_at=expires_at,
        copy_budget=copy_budget,
        min_reserve=min_reserve,
        social_reserve=social_reserve,
        encrypted_payload=b"ciphertext",
    )
