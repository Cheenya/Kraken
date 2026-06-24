from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

from .codec import OutboxBackoffPolicy
from .models import LocalMessage, MessageDirection, parse_datetime, state_to_jsonable, utc_now


@dataclass(slots=True)
class OutboxRetryRecord:
    message_id: str
    relationship_id: str
    body: str
    created_at: datetime
    expires_at: datetime
    attempts: int
    next_attempt_at: datetime


class DurableOutboxStore:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.records: dict[str, OutboxRetryRecord] = {}
        self._load()

    def enqueue(self, message: LocalMessage, ttl_seconds: int = 300) -> OutboxRetryRecord:
        if message.direction is not MessageDirection.OUTGOING:
            raise ValueError("outbox_accepts_only_outgoing_messages")
        record = self.records.get(message.message_id)
        if record is None:
            record = OutboxRetryRecord(
                message_id=message.message_id,
                relationship_id=message.relationship_id,
                body=message.body,
                created_at=message.created_at,
                expires_at=message.created_at + timedelta(seconds=ttl_seconds),
                attempts=0,
                next_attempt_at=message.created_at,
            )
            self.records[message.message_id] = record
            self.save()
        return record

    def mark_attempt(self, message_id: str, now: datetime | None = None) -> OutboxRetryRecord:
        current = now or utc_now()
        record = self.records[message_id]
        record.attempts += 1
        record.next_attempt_at = current + timedelta(seconds=OutboxBackoffPolicy.retry_delay(record.attempts))
        self.save()
        return record

    def mark_delivered(self, message_id: str) -> None:
        self.records.pop(message_id, None)
        self.save()

    def ready_records(self, now: datetime | None = None) -> list[OutboxRetryRecord]:
        current = now or utc_now()
        return [
            record
            for record in self.records.values()
            if record.next_attempt_at <= current and record.expires_at > current
        ]

    def expire(self, now: datetime | None = None) -> list[str]:
        current = now or utc_now()
        expired = [message_id for message_id, record in self.records.items() if record.expires_at <= current]
        for message_id in expired:
            self.records.pop(message_id, None)
        if expired:
            self.save()
        return expired

    def save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        payload = {message_id: state_to_jsonable(record) for message_id, record in self.records.items()}
        self.path.write_text(json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")

    def _load(self) -> None:
        if not self.path.exists():
            return
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        self.records = {
            message_id: OutboxRetryRecord(
                message_id=str(value["message_id"]),
                relationship_id=str(value["relationship_id"]),
                body=str(value["body"]),
                created_at=_required_datetime(value["created_at"]),
                expires_at=_required_datetime(value["expires_at"]),
                attempts=int(value.get("attempts", 0)),
                next_attempt_at=_required_datetime(value["next_attempt_at"]),
            )
            for message_id, value in payload.items()
        }


def _required_datetime(value: str | datetime) -> datetime:
    parsed = parse_datetime(value)
    if parsed is None:
        raise ValueError("datetime_required")
    return parsed
