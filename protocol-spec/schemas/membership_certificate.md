# Membership Certificate

## Purpose

Represents signed membership in an invite-only Kraken realm or scoped space.

## Scope

Membership certificates are required for realm access. A new local identity key
does not grant access to existing realms.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `membership_certificate`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `realm_id` | string | yes | Realm that issued membership. |
| `membership_id` | string | yes | Realm-scoped membership id. |
| `member_key` | string | yes | Public identity key of member. |
| `issued_by` | string | yes | Admin/moderator/root-authorized key. |
| `issued_at` | string | yes | ISO-8601 UTC timestamp. |
| `expires_at` | string | yes | Expiry timestamp. |
| `capabilities` | string array | yes | Realm-scoped capabilities. |
| `approval_ref` | string | optional | Approval request id, if applicable. |
| `invite_edge_id` | string | optional | Accepted invite edge reference. |
| `signature` | string | yes | Signature by `issued_by`. |

## State Transitions

```text
PENDING_APPROVAL -> ACTIVE -> EXPIRED
PENDING_APPROVAL -> REJECTED
ACTIVE -> PAUSED
ACTIVE -> REVOKED
PAUSED -> ACTIVE
```

## Validation Rules

- `member_key` must match the identity that completed handshake and approval.
- A realm must not accept duplicate active memberships for the same `member_key`.
- `issued_by` must have certificate-issuing capability under the realm policy.
- `expires_at` must be enforced.
- Capabilities must be a subset allowed by realm policy and approval result.
- Certificate issuance must happen only after handshake and required approval.

## Privacy And Security Notes

Membership ids count realm memberships/identities, not unique real humans.
Certificates must not contain phone, email, username login or device identifier
fields.

Root governance may issue capacity or governance tokens, but root governance
cannot decrypt member messages by possessing or observing this certificate.

## JSON Example

```json
{
  "type": "membership_certificate",
  "version": 1,
  "realm_id": "realm_demo_dissertation",
  "membership_id": "mem_01HY9XD3P9VE7Z6KQ02T6J83HA",
  "member_key": "ed25519-pub:INVITEE_BASE64URL...",
  "issued_by": "ed25519-pub:ADMIN_BASE64URL...",
  "issued_at": "2026-05-17T09:30:00Z",
  "expires_at": "2026-08-17T09:30:00Z",
  "capabilities": ["send_direct", "relay_basic", "read_channel"],
  "approval_ref": "approval_01HY9XBQNA6A0X8N8N3F8VYJZD",
  "invite_edge_id": "edge_01HY9XCKG3N6GZPF3N8SS2F0H4",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should membership certificates be short-lived by default for demo realms?
- Should `membership_id` be random or derived from a certificate hash?
