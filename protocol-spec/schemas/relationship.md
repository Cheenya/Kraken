# Relationship

## Purpose

Models a user-to-user relationship lifecycle.

## Scope

Relationships are required for direct messaging and for some scoped moderation
events. Sending is allowed only when the relationship is `ACTIVE`.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `relationship`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `relationship_id` | string | yes | Relationship instance id. |
| `local_key` | string | yes | Local public identity key. |
| `peer_key` | string | yes | Peer public identity key. |
| `peer_fingerprint` | string | yes | Peer fingerprint shown to user. |
| `realm_id` | string | optional | Realm context, if relationship belongs to one. |
| `invite_id` | string | optional | Invite that started relationship. |
| `invite_edge_id` | string | optional | Accepted invite edge, if any. |
| `state` | string | yes | Relationship state. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `updated_at` | string | yes | ISO-8601 UTC timestamp. |
| `last_unlink_notice_id` | string | optional | Last unlink notice reference. |

Relationship states:

```text
PENDING_IMPORT
PENDING_HANDSHAKE
ACTIVE
UNLINK_REQUESTED
UNLINKED
BLOCKED_BY_PEER
REJOIN_REQUIRED
```

## State Transitions

```text
PENDING_IMPORT -> PENDING_HANDSHAKE
PENDING_HANDSHAKE -> ACTIVE
PENDING_HANDSHAKE -> REJOIN_REQUIRED
ACTIVE -> UNLINK_REQUESTED
UNLINK_REQUESTED -> UNLINKED
ACTIVE -> BLOCKED_BY_PEER
BLOCKED_BY_PEER -> REJOIN_REQUIRED
UNLINKED -> REJOIN_REQUIRED
REJOIN_REQUIRED -> PENDING_IMPORT
```

Reactivation requires a new invite/QR plus handshake and should create a new
`relationship_id`.

## Validation Rules

- Sending is allowed only in `ACTIVE`.
- Imported invite/contact data starts at `PENDING_IMPORT`, not `ACTIVE`.
- Handshake completion is required before `ACTIVE`.
- Receiving a valid unlink notice from peer sets `BLOCKED_BY_PEER`.
- Local unlink moves to `UNLINK_REQUESTED` and then `UNLINKED`.
- After unlink, packets from the old relationship/key must be rejected locally.
- A new identity key cannot reuse old relationship state.

## Privacy And Security Notes

For honest clients, unlink is bilateral. A malicious client may attempt to send
packets after unlink, but the remover rejects packets from the old
relationship/key.

Relationship state is local and social metadata. It must not imply public
discoverability of the peer.

## JSON Example

```json
{
  "type": "relationship",
  "version": 1,
  "relationship_id": "rel_01HY9XHGMF9DN6XS1M1SYF9NKM",
  "local_key": "ed25519-pub:ALICE_BASE64URL...",
  "peer_key": "ed25519-pub:BOB_BASE64URL...",
  "peer_fingerprint": "kraken:K6HP-PV8N-7J4C-Z2QB",
  "realm_id": "realm_demo_dissertation",
  "invite_id": "inv_01HY9X7VK5E9B9E1S8W3JX4A0P",
  "invite_edge_id": "edge_01HY9XCKG3N6GZPF3N8SS2F0H4",
  "state": "ACTIVE",
  "created_at": "2026-05-17T09:32:00Z",
  "updated_at": "2026-05-17T09:35:00Z",
  "last_unlink_notice_id": null
}
```

## Open Questions

- Should rejoin create a new relationship id in every case, or may local UI
  thread history link old and new relationship ids for display only?
