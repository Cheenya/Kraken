# Approval Policy

## Purpose

Defines how pending membership or space access requests are approved.

## Scope

Approval policy applies when an inviter cannot directly approve membership or
when realm policy requires additional review.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `approval_policy`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `approval_policy_id` | string | yes | Policy id. |
| `realm_id` | string | yes | Realm scope. |
| `mode` | string | yes | `single_admin` or `threshold_approval`. |
| `required_approvals` | integer | yes | Number of approvals needed. |
| `eligible_approver_roles` | string array | yes | Roles allowed to approve. |
| `pending_user_capabilities` | object | yes | Explicit pending restrictions. |
| `single_admin_warning_enabled` | boolean | yes | Warn if realm grows with one admin. |
| `issued_by` | string | yes | Policy issuer key. |
| `updated_at` | string | yes | ISO-8601 UTC timestamp. |
| `signature` | string | yes | Signature by `issued_by`. |

## State Transitions

Pending request lifecycle:

```text
PENDING_APPROVAL -> APPROVED -> CERTIFICATE_ISSUED
PENDING_APPROVAL -> REJECTED
PENDING_APPROVAL -> EXPIRED
```

## Validation Rules

- Pending users cannot write first.
- Pending users cannot post in the target space.
- Pending users cannot invite others.
- Pending users cannot receive backlog.
- Pending users can only reply to admin/moderator approval messages.
- `threshold_approval` requires `required_approvals > 1`.
- `single_admin` requires exactly one eligible admin approval.
- Approval must precede membership certificate issuance.

## Privacy And Security Notes

Approval views may show invite trust summary and complaint/restriction summary.
They should not expose private message contents by default.

## JSON Example

```json
{
  "type": "approval_policy",
  "version": 1,
  "approval_policy_id": "apol_01HY9Y8K1W782T6RNGDYSM34M9",
  "realm_id": "realm_demo_dissertation",
  "mode": "threshold_approval",
  "required_approvals": 2,
  "eligible_approver_roles": ["admin", "moderator"],
  "pending_user_capabilities": {
    "can_write_first": false,
    "can_post_target_space": false,
    "can_invite": false,
    "can_receive_backlog": false,
    "can_reply_to_approval_messages": true
  },
  "single_admin_warning_enabled": true,
  "issued_by": "ed25519-pub:REALM_ADMIN_BASE64URL...",
  "updated_at": "2026-05-17T09:46:00Z",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should pending approval expiration be realm-policy controlled or fixed in MVP?
- Should moderators approve all scopes, or should channels/small groups have
  separate approver role lists?
