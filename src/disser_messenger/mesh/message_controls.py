from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class MessageStatus(StrEnum):
    PENDING = "PENDING"
    SENT_TO_NETWORK = "SENT_TO_NETWORK"
    DELIVERED = "DELIVERED"
    READ = "READ"
    NOT_CONFIRMED = "NOT_CONFIRMED"
    EXPIRED = "EXPIRED"
    FAILED = "FAILED"


@dataclass(frozen=True, slots=True)
class HopAck:
    packet_id: str
    message_id: str
    from_node_id: str
    at_time: int


@dataclass(frozen=True, slots=True)
class DeliveryReceipt:
    packet_id: str
    message_id: str
    recipient_id: str
    at_time: int


@dataclass(frozen=True, slots=True)
class ReadReceipt:
    packet_id: str
    message_id: str
    reader_id: str
    at_time: int


@dataclass(frozen=True, slots=True)
class Tombstone:
    packet_id: str
    message_id: str
    created_by: str
    at_time: int
    signature: str | None = None
    best_effort_only: bool = True

    @property
    def guarantees_deletion(self) -> bool:
        return False


class MessageStatusTracker:
    def __init__(self, read_receipts_enabled: bool = False) -> None:
        self.read_receipts_enabled = read_receipts_enabled
        self._statuses: dict[str, MessageStatus] = {}

    def create_pending(self, message_id: str) -> None:
        self._statuses[message_id] = MessageStatus.PENDING

    def apply_hop_ack(self, ack: HopAck) -> None:
        self._statuses[ack.message_id] = MessageStatus.SENT_TO_NETWORK

    def apply_delivery_receipt(self, receipt: DeliveryReceipt) -> None:
        self._statuses[receipt.message_id] = MessageStatus.DELIVERED

    def apply_read_receipt(self, receipt: ReadReceipt) -> None:
        if self.read_receipts_enabled:
            self._statuses[receipt.message_id] = MessageStatus.READ

    def mark_not_confirmed(self, message_id: str) -> None:
        if self._statuses.get(message_id) not in {MessageStatus.DELIVERED, MessageStatus.READ}:
            self._statuses[message_id] = MessageStatus.NOT_CONFIRMED

    def mark_expired(self, message_id: str) -> None:
        if self._statuses.get(message_id) != MessageStatus.READ:
            self._statuses[message_id] = MessageStatus.EXPIRED

    def mark_failed(self, message_id: str) -> None:
        self._statuses[message_id] = MessageStatus.FAILED

    def status(self, message_id: str) -> MessageStatus:
        return self._statuses.get(message_id, MessageStatus.PENDING)
