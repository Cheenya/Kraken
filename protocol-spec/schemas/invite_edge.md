# Invite Edge

## Purpose

Records accepted inviter-invitee accountability after membership or relationship
activation.

## Scope

Invite edges support trust chains, invite statistics, moderation context and
limiting invite rights for repeated abuse patterns. One bad invitee should not
automatically punish the inviter.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `invite_edge`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `invite_edge_id` | string | yes | Edge identifier. |
| `invite_id` | string | yes | Consumed invite id. |
| `realm_id` | string | optional | Realm context, if any. |
| `subnet_id` | string | optional | Subnet context, if any. |
| `space_id` | string | optional | Space context, if any. |
| `scope` | string | yes | `direct`, `small_group` or `channel`. |
| `inviter_key` | string | yes | Public identity key of inviter. |
| `invitee_key` | string | yes | Public identity key of invitee. |
| `membership_id` | string | optional | Issued membership id, if realm-scoped. |
| `accepted_at` | string | yes | ISO-8601 UTC timestamp. |
| `status` | string | yes | `ACTIVE`, `LIMITED`, `REVOKED` or `ARCHIVED`. |
| `signature` | string | yes | Signature by realm issuer or edge recorder. |

## State Transitions

```text
ACTIVE -> LIMITED
ACTIVE -> REVOKED
ACTIVE -> ARCHIVED
LIMITED -> ACTIVE
LIMITED -> REVOKED
LIMITED -> ARCHIVED
```

## Validation Rules

- Create an edge only after invite acceptance and required certificate issuance.
- `inviter_key` and `invitee_key` must be different identities.
- `invite_id` must refer to a consumed one-time invite.
- Repeated negative outcomes may limit inviter capabilities under policy.
- A single complaint or single bad invitee must not automatically punish the
  inviter.

## Privacy And Security Notes

Invite edges are moderation metadata and may expose social graph information.
Do not share them outside the relevant realm/moderation context unless a future
privacy design explicitly allows it.

Invite edge state must not transfer to a new identity key.

## JSON Example

```json
{
  "type": "invite_edge",
  "version": 1,
  "invite_edge_id": "edge_01HY9XCKG3N6GZPF3N8SS2F0H4",
  "invite_id": "inv_01HY9X7VK5E9B9E1S8W3JX4A0P",
  "realm_id": "realm_demo_dissertation",
  "subnet_id": null,
  "space_id": null,
  "scope": "direct",
  "inviter_key": "ed25519-pub:INVITER_BASE64URL...",
  "invitee_key": "ed25519-pub:INVITEE_BASE64URL...",
  "membership_id": "mem_01HY9XD3P9VE7Z6KQ02T6J83HA",
  "accepted_at": "2026-05-17T09:31:00Z",
  "status": "ACTIVE",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should invite edge visibility be limited to admins/moderators only in MVP?
- Which repeated-pattern thresholds should reduce inviter rights?
