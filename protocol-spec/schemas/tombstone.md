# Tombstone

## Purpose

Signed deletion signal telling honest relay nodes to delete a transit packet and
stop forwarding it.

## Scope

Tombstones apply to transit copies and local forwarding buffers. They do not
guarantee deletion from malicious or modified devices.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `tombstone`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `tombstone_id` | string | yes | Tombstone id. |
| `packet_id` | string | yes | Packet to delete. |
| `message_id` | string | yes | Message/control message reference. |
| `realm_id` | string | yes | Realm scope. |
| `issuer_key` | string | yes | Sender or recipient key authorized to tombstone. |
| `reason` | string | yes | `delivered`, `expired`, `sender_deleted`, `recipient_deleted`. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `expires_at` | string | yes | Tombstone propagation expiry. |
| `signature` | string | yes | Signature by `issuer_key`. |

## State Transitions

Transit buffer:

```text
STORED -> TOMBSTONED -> DELETED
STORED -> EXPIRED -> DELETED
```

## Validation Rules

- Signature must verify under `issuer_key`.
- `issuer_key` must be authorized as sender or final recipient context for the
  packet.
- Honest relay nodes must delete matching transit packet copies after a valid
  tombstone.
- Tombstones must not replace TTL/expiration. Expiration remains mandatory.
- Expired tombstones may be dropped.

## Privacy And Security Notes

Deletion is best-effort. Kraken can require honest clients to delete matching
transit packets, but cannot force malicious devices to delete stored bytes.

Tombstones may reveal that a packet/message existed. Future versions may need
privacy-improved deletion tags.

## JSON Example

```json
{
  "type": "tombstone",
  "version": 1,
  "tombstone_id": "tomb_01HY9XY6EGQSP9WRWTDB8DFBGH",
  "packet_id": "pkt_01HY9XR4CN9C7FHY8SZT8RK7GC",
  "message_id": "msg_01HY9XR05T6HZV8QDBR41R9S2R",
  "realm_id": "realm_demo_dissertation",
  "issuer_key": "ed25519-pub:ALICE_BASE64URL...",
  "reason": "delivered",
  "created_at": "2026-05-17T10:20:00Z",
  "expires_at": "2026-05-18T10:20:00Z",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should tombstones reference encrypted deletion tags instead of visible
  `packet_id` values in later privacy versions?
