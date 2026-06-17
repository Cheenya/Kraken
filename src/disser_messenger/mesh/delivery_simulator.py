from __future__ import annotations

from dataclasses import dataclass, replace


@dataclass(frozen=True, slots=True)
class SimPacket:
    packet_id: str
    message_id: str
    source_id: str
    recipient_id: str
    encrypted_payload: bytes
    ttl_hops: int
    expires_at: int
    copy_budget: int


@dataclass(frozen=True, slots=True)
class SimDeliveryReceipt:
    packet_id: str
    message_id: str
    source_id: str
    recipient_id: str


@dataclass(frozen=True, slots=True)
class SimTombstone:
    packet_id: str
    source_id: str


@dataclass(frozen=True, slots=True)
class SimEncounter:
    left_id: str
    right_id: str
    at_time: int


class SimTransitBuffer:
    def __init__(self) -> None:
        self.packets: dict[str, SimPacket] = {}
        self.receipts: dict[str, SimDeliveryReceipt] = {}
        self.tombstones: dict[str, SimTombstone] = {}

    def add_packet(self, packet: SimPacket, now: int) -> bool:
        if packet.packet_id in self.tombstones:
            return False
        if packet.packet_id in self.packets:
            return False
        if is_expired(packet, now):
            return False
        self.packets[packet.packet_id] = packet
        return True

    def add_receipt(self, receipt: SimDeliveryReceipt) -> bool:
        if receipt.packet_id in self.receipts:
            return False
        self.receipts[receipt.packet_id] = receipt
        return True

    def add_tombstone(self, tombstone: SimTombstone) -> None:
        self.tombstones[tombstone.packet_id] = tombstone
        self.packets.pop(tombstone.packet_id, None)

    def purge_expired(self, now: int) -> None:
        expired_ids = [
            packet_id for packet_id, packet in self.packets.items() if is_expired(packet, now)
        ]
        for packet_id in expired_ids:
            del self.packets[packet_id]

    def packet_count(self) -> int:
        return len(self.packets)


class SimNode:
    def __init__(self, node_id: str) -> None:
        self.node_id = node_id
        self.buffer = SimTransitBuffer()
        self.inbox: dict[str, SimPacket] = {}
        self.delivered_receipts: dict[str, SimDeliveryReceipt] = {}

    def create_packet(
        self,
        packet_id: str,
        message_id: str,
        recipient_id: str,
        encrypted_payload: bytes,
        ttl_hops: int,
        expires_at: int,
        copy_budget: int,
        now: int,
    ) -> SimPacket:
        packet = SimPacket(
            packet_id=packet_id,
            message_id=message_id,
            source_id=self.node_id,
            recipient_id=recipient_id,
            encrypted_payload=encrypted_payload,
            ttl_hops=ttl_hops,
            expires_at=expires_at,
            copy_budget=copy_budget,
        )
        self.buffer.add_packet(packet, now)
        return packet

    def receive_packet(self, packet: SimPacket) -> None:
        if packet.recipient_id != self.node_id:
            return
        if packet.packet_id in self.inbox:
            return
        self.inbox[packet.packet_id] = packet
        self.buffer.add_receipt(
            SimDeliveryReceipt(
                packet_id=packet.packet_id,
                message_id=packet.message_id,
                source_id=packet.source_id,
                recipient_id=self.node_id,
            )
        )

    def receive_receipt(self, receipt: SimDeliveryReceipt) -> None:
        if receipt.source_id == self.node_id:
            self.delivered_receipts[receipt.packet_id] = receipt
        else:
            self.buffer.add_receipt(receipt)

    def create_tombstone(self, packet_id: str) -> SimTombstone:
        tombstone = SimTombstone(packet_id=packet_id, source_id=self.node_id)
        self.buffer.add_tombstone(tombstone)
        return tombstone

    def read_payload(self, packet_id: str) -> bytes:
        packet = self.inbox.get(packet_id) or self.buffer.packets.get(packet_id)
        if packet is None:
            raise KeyError(packet_id)
        if packet.recipient_id != self.node_id:
            raise PermissionError("Relay nodes cannot decrypt encrypted_payload placeholder")
        return packet.encrypted_payload


def meet(left: SimNode, right: SimNode, now: int) -> SimEncounter:
    left.buffer.purge_expired(now)
    right.buffer.purge_expired(now)

    exchange_tombstones(left, right)
    exchange_packets(left, right, now)
    exchange_packets(right, left, now)
    exchange_receipts(left, right)
    exchange_receipts(right, left)
    exchange_tombstones(left, right)

    return SimEncounter(left_id=left.node_id, right_id=right.node_id, at_time=now)


def exchange_packets(sender: SimNode, receiver: SimNode, now: int) -> None:
    for packet in list(sender.buffer.packets.values()):
        if packet.ttl_hops <= 0 or packet.copy_budget <= 1 or is_expired(packet, now):
            continue
        forwarded = replace(packet, ttl_hops=packet.ttl_hops - 1, copy_budget=packet.copy_budget - 1)
        if receiver.buffer.add_packet(forwarded, now):
            sender.buffer.packets[packet.packet_id] = replace(packet, copy_budget=packet.copy_budget - 1)
            receiver.receive_packet(forwarded)


def exchange_receipts(sender: SimNode, receiver: SimNode) -> None:
    for receipt in list(sender.buffer.receipts.values()):
        receiver.receive_receipt(receipt)


def exchange_tombstones(left: SimNode, right: SimNode) -> None:
    for tombstone in list(left.buffer.tombstones.values()):
        right.buffer.add_tombstone(tombstone)
    for tombstone in list(right.buffer.tombstones.values()):
        left.buffer.add_tombstone(tombstone)


def is_expired(packet: SimPacket, now: int) -> bool:
    return packet.expires_at <= now
