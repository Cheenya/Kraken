# Unlink Notice

## Purpose

Signed control message that terminates a user-to-user relationship for honest
clients.

## Scope

Unlink notices apply to one relationship/key context. They are not global bans
and do not automatically remove relay participation outside the scoped
relationship or realm policy.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `unlink_notice`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `unlink_notice_id` | string | yes | Notice id. |
| `relationship_id` | string | yes | Relationship being terminated. |
| `realm_id` | string | optional | Linked realm, if any. |
| `sender_key` | string | yes | Remover public identity key. |
| `recipient_key` | string | yes | Removed peer public identity key. |
| `reason` | string | yes | Allowed unlink reason. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `creates_complaint` | boolean | yes | True for negative reasons in a linked realm. |
| `signature` | string | yes | Signature by `sender_key`. |

Allowed reasons:

```text
ENDED_INTERACTION
UNWANTED_MESSAGES
SPAM
THREAT_PRESSURE_OR_ETHICS_ABUSE
OTHER
```

Neutral reasons:

```text
ENDED_INTERACTION
OTHER
```

Negative reasons:

```text
UNWANTED_MESSAGES
SPAM
THREAT_PRESSURE_OR_ETHICS_ABUSE
```

## State Transitions

Sender:

```text
ACTIVE -> UNLINK_REQUESTED -> UNLINKED
```

Recipient:

```text
ACTIVE -> BLOCKED_BY_PEER -> REJOIN_REQUIRED
```

## Validation Rules

- `sender_key` must match one party of `relationship_id`.
- `recipient_key` must match the other party of `relationship_id`.
- Signature must verify under `sender_key`.
- After receiving a valid notice, recipient must not send through old
  relationship.
- Sender rejects future packets from the old relationship/key.
- Reactivation requires new invite/QR plus handshake.
- Negative reason in a linked realm must create a complaint event.

## Privacy And Security Notes

Unlink is bilateral for honest clients, not a cryptographic way to force
malicious devices to stop transmitting bytes. Local rejection by the remover is
mandatory.

Unlink reason content should be minimized. Detailed evidence belongs in a
separate optional complaint payload.

## JSON Example

```json
{
  "type": "unlink_notice",
  "version": 1,
  "unlink_notice_id": "unlink_01HY9XN4M66JW58MEFJYMPJ6AV",
  "relationship_id": "rel_01HY9XHGMF9DN6XS1M1SYF9NKM",
  "realm_id": "realm_demo_dissertation",
  "sender_key": "ed25519-pub:ALICE_BASE64URL...",
  "recipient_key": "ed25519-pub:BOB_BASE64URL...",
  "reason": "UNWANTED_MESSAGES",
  "created_at": "2026-05-17T10:00:00Z",
  "creates_complaint": true,
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should unlink notices be encrypted to the peer, signed plaintext control
  metadata, or both?
