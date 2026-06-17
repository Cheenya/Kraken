from __future__ import annotations

from dataclasses import dataclass, replace
from enum import StrEnum


class PacketKind(StrEnum):
    DIRECT = "direct"
    SMALL_GROUP = "small_group"
    CHANNEL = "channel"


@dataclass(frozen=True, slots=True)
class PacketScope:
    realm_id: str
    kind: PacketKind
    subnet_id: str | None = None
    space_id: str | None = None


@dataclass(frozen=True, slots=True)
class Packet:
    packet_id: str
    message_id: str
    realm_id: str
    scope: PacketScope
    ttl_hops: int
    expires_at: int
    copy_budget: int
    min_reserve: int
    social_reserve: int
    encrypted_payload: bytes
    priority: int = 0


@dataclass(frozen=True, slots=True)
class BatteryPolicy:
    allow_forwarding: bool = True
    low_power_mode: bool = False


@dataclass(frozen=True, slots=True)
class CopyBudgetPolicy:
    min_copy_budget: int = 1


@dataclass(frozen=True, slots=True)
class ReserveCopyPolicy:
    high_score_threshold: float = 0.7


@dataclass(frozen=True, slots=True)
class ForwardingPolicy:
    battery: BatteryPolicy = BatteryPolicy()
    copy_budget: CopyBudgetPolicy = CopyBudgetPolicy()
    reserve: ReserveCopyPolicy = ReserveCopyPolicy()

    def can_forward(self, packet: Packet, relay_score: float) -> bool:
        if not self.battery.allow_forwarding or self.battery.low_power_mode:
            return False
        if packet.ttl_hops <= 0:
            return False
        if packet.copy_budget <= max(packet.min_reserve, self.copy_budget.min_copy_budget):
            return False
        if packet.copy_budget <= packet.social_reserve and relay_score < self.reserve.high_score_threshold:
            return False
        return True


class TransitBuffer:
    def __init__(self, max_packets: int = 100) -> None:
        self.max_packets = max_packets
        self._packets: dict[str, Packet] = {}

    @property
    def packets(self) -> list[Packet]:
        return list(self._packets.values())

    def add(self, packet: Packet, now: int) -> bool:
        if packet.packet_id in self._packets:
            return False
        if packet.expires_at <= now:
            return False
        if len(self._packets) >= self.max_packets:
            return False
        self._packets[packet.packet_id] = packet
        return True

    def get(self, packet_id: str) -> Packet | None:
        return self._packets.get(packet_id)

    def expire(self, now: int) -> list[str]:
        expired = [packet_id for packet_id, packet in self._packets.items() if packet.expires_at <= now]
        for packet_id in expired:
            del self._packets[packet_id]
        return expired

    def remove(self, packet_id: str) -> None:
        self._packets.pop(packet_id, None)

    def forward_copy(
        self,
        packet_id: str,
        relay_score: float,
        policy: ForwardingPolicy,
    ) -> Packet | None:
        packet = self._packets.get(packet_id)
        if packet is None or not policy.can_forward(packet, relay_score):
            return None
        updated = replace(packet, copy_budget=packet.copy_budget - 1)
        forwarded = replace(updated, ttl_hops=packet.ttl_hops - 1)
        self._packets[packet_id] = updated
        return forwarded

    def __len__(self) -> int:
        return len(self._packets)
