# Realm Policy

## Purpose

Defines realm-level limits, capabilities and governance boundaries.

## Scope

Realm policy controls invite-only membership, capacity, allowed scopes and local
moderation rules. It does not provide message decryption authority to root
governance.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `realm_policy`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `realm_id` | string | yes | Realm identifier. |
| `policy_id` | string | yes | Policy id/version. |
| `member_capacity` | integer | yes | Default example: `500`. |
| `signed_known_member_count` | integer | yes | Latest signed known count. |
| `invite_generation` | string | yes | `OPEN_UNDER_CAPACITY` or `BLOCKED_AT_CAPACITY`. |
| `allowed_scopes` | string array | yes | Usually `direct`, `small_group`, `channel`. |
| `small_group_max_members` | integer | yes | Strict small group limit. |
| `channel_backlog_policy` | object | yes | Latest-N/TTL limits. |
| `approval_policy_id` | string | yes | Approval policy reference. |
| `root_capacity_token_id` | string | optional | Capacity increase token, if any. |
| `issued_by` | string | yes | Realm admin/maintainer key. |
| `updated_at` | string | yes | ISO-8601 UTC timestamp. |
| `signature` | string | yes | Signature by `issued_by`. |

## State Transitions

```text
ACTIVE -> AT_CAPACITY
AT_CAPACITY -> ACTIVE
ACTIVE -> PAUSED_UNDER_REVIEW
PAUSED_UNDER_REVIEW -> ACTIVE
ACTIVE -> ARCHIVED
```

When `signed_known_member_count >= member_capacity`, invite generation is
blocked unless a valid capacity token increases capacity.

## Validation Rules

- No public discovery fields or public directory endpoints are allowed.
- `member_capacity` must be positive. Default ordinary realm example is `500`.
- Capacity increase requires a signed capacity token.
- Small groups must have strict limits. Large group chats are not MVP.
- Channels are preferred for wider communication.
- Root governance cannot decrypt messages and should not handle ordinary
  disputes by default.
- Nodes should relay only traffic matching membership, subscriptions and policy.

## Privacy And Security Notes

Realm policies are governance metadata. They must not include message keys or
plaintext access. Root/maintainer controls capacity and large-scale governance,
not message reading.

## JSON Example

```json
{
  "type": "realm_policy",
  "version": 1,
  "realm_id": "realm_demo_dissertation",
  "policy_id": "rpol_01HY9Y7R35EG6R5H9M2BP9JVRN",
  "member_capacity": 500,
  "signed_known_member_count": 214,
  "invite_generation": "OPEN_UNDER_CAPACITY",
  "allowed_scopes": ["direct", "small_group", "channel"],
  "small_group_max_members": 10,
  "channel_backlog_policy": {
    "mode": "latest_n_and_ttl",
    "latest_n": 50,
    "ttl_hours": 168
  },
  "approval_policy_id": "apol_01HY9Y8K1W782T6RNGDYSM34M9",
  "root_capacity_token_id": null,
  "issued_by": "ed25519-pub:REALM_ADMIN_BASE64URL...",
  "updated_at": "2026-05-17T09:45:00Z",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should ordinary realm capacity `500` be hard-coded for MVP or only a default
  policy template?
- What default small group member limit should the Android MVP enforce: 5, 8 or
  10?
