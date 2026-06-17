from disser_messenger.mesh.message_controls import (
    DeliveryReceipt,
    HopAck,
    MessageStatus,
    MessageStatusTracker,
    ReadReceipt,
    Tombstone,
)


def test_hop_ack_moves_message_to_sent_to_network() -> None:
    tracker = MessageStatusTracker()
    tracker.create_pending("message-1")

    tracker.apply_hop_ack(HopAck("packet-1", "message-1", "relay-b", at_time=1))

    assert tracker.status("message-1") == MessageStatus.SENT_TO_NETWORK


def test_delivery_receipt_moves_message_to_delivered() -> None:
    tracker = MessageStatusTracker()

    tracker.apply_delivery_receipt(DeliveryReceipt("packet-1", "message-1", "peer-y", at_time=2))

    assert tracker.status("message-1") == MessageStatus.DELIVERED


def test_read_receipt_is_optional() -> None:
    tracker = MessageStatusTracker(read_receipts_enabled=False)
    tracker.apply_delivery_receipt(DeliveryReceipt("packet-1", "message-1", "peer-y", at_time=2))

    tracker.apply_read_receipt(ReadReceipt("packet-1", "message-1", "peer-y", at_time=3))

    assert tracker.status("message-1") == MessageStatus.DELIVERED


def test_read_receipt_enabled_moves_message_to_read() -> None:
    tracker = MessageStatusTracker(read_receipts_enabled=True)
    tracker.apply_delivery_receipt(DeliveryReceipt("packet-1", "message-1", "peer-y", at_time=2))

    tracker.apply_read_receipt(ReadReceipt("packet-1", "message-1", "peer-y", at_time=3))

    assert tracker.status("message-1") == MessageStatus.READ


def test_timeout_marks_not_confirmed_or_expired() -> None:
    tracker = MessageStatusTracker()

    tracker.mark_not_confirmed("message-1")
    tracker.mark_expired("message-2")

    assert tracker.status("message-1") == MessageStatus.NOT_CONFIRMED
    assert tracker.status("message-2") == MessageStatus.EXPIRED


def test_tombstone_targets_packet_id_and_is_best_effort() -> None:
    tombstone = Tombstone(
        packet_id="packet-1",
        message_id="message-1",
        created_by="peer-a",
        at_time=4,
        signature=None,
    )

    assert tombstone.packet_id == "packet-1"
    assert tombstone.best_effort_only
    assert not tombstone.guarantees_deletion
