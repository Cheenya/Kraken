# Receipt

## Purpose

Confirms first-hop acceptance, final delivery or optional read state in a
serverless P2P delivery model.

## Scope

Receipts are encrypted control messages routed through the same opportunistic
network. There is no server acknowledgment.

## Current Prototype Status

This schema describes the target signed/encrypted receipt contract. The current
Android LAN prototype creates local receipt packets with prototype JSON payloads
and placeholder crypto. Treat those receipts as delivery-test evidence only, not
as production cryptographic acknowledgments.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `receipt`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `receipt_id` | string | yes | Receipt id. |
| `receipt_kind` | string | yes | `HopAck`, `DeliveryReceipt` or `ReadReceipt`. |
| `packet_id` | string | yes | Packet being acknowledged. |
| `message_id` | string | yes | Message being acknowledged. |
| `realm_id` | string | yes | Realm scope. |
| `from_key` | string | yes | Issuer public identity key. |
| `to_key` | string | yes | Intended recipient of receipt. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `status_after_apply` | string | yes | Resulting local message status. |
| `signature` | string | yes | Signature by `from_key`. |

Message statuses:

```text
PENDING
SENT_TO_NETWORK
DELIVERED
READ
NOT_CONFIRMED
EXPIRED
FAILED
```

## State Transitions

```text
PENDING -> SENT_TO_NETWORK
SENT_TO_NETWORK -> DELIVERED
DELIVERED -> READ
PENDING -> NOT_CONFIRMED
SENT_TO_NETWORK -> NOT_CONFIRMED
PENDING -> EXPIRED
SENT_TO_NETWORK -> EXPIRED
PENDING -> FAILED
```

`HopAck` maps to `SENT_TO_NETWORK`. `DeliveryReceipt` maps to `DELIVERED`.
`ReadReceipt` maps to `READ` and must be optional.

## Validation Rules

- `receipt_kind` must match the allowed values.
- `ReadReceipt` must not be emitted if read receipts are disabled.
- Receipt signatures must verify under `from_key`.
- A receipt must reference a known `packet_id`/`message_id` or be stored as
  unmatched control metadata until expiration.
- Receipt processing must not reveal plaintext to relay nodes.
- In the Android prototype, signature/encryption checks remain Phase Crypto work
  and must be reported as incomplete.

## Privacy And Security Notes

Read receipts leak user behavior and must be optional. Timing and receipt paths
can reveal metadata even when content is encrypted.

## JSON Example

```json
{
  "type": "receipt",
  "version": 1,
  "receipt_id": "rcpt_01HY9XVP0CFNZ28XDHGZCJ6R0M",
  "receipt_kind": "DeliveryReceipt",
  "packet_id": "pkt_01HY9XR4CN9C7FHY8SZT8RK7GC",
  "message_id": "msg_01HY9XR05T6HZV8QDBR41R9S2R",
  "realm_id": "realm_demo_dissertation",
  "from_key": "ed25519-pub:BOB_BASE64URL...",
  "to_key": "ed25519-pub:ALICE_BASE64URL...",
  "created_at": "2026-05-17T10:12:00Z",
  "status_after_apply": "DELIVERED",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should hop acknowledgments be signed by every relay or use a cheaper local
  session-authenticated format?
