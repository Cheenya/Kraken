# Invite

## Purpose

Represents a one-time invite payload that starts a mandatory handshake.

## Scope

An invite may be encoded as QR, copied JSON or transferred through an external
unique contact channel. It does not grant membership directly.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `invite`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `invite_id` | string | yes | One-time invite attempt id. |
| `realm_id` | string | optional | Target realm, if realm-scoped. |
| `subnet_id` | string | optional | Target subnet, if any. |
| `space_id` | string | optional | Target direct/small-group/channel space, if any. |
| `scope` | string | yes | `direct`, `small_group` or `channel`. |
| `inviter_key` | string | yes | Public identity key of inviter. |
| `inviter_fingerprint` | string | yes | Fingerprint shown for verification. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `expires_at` | string | yes | Invite expiry timestamp. |
| `one_time` | boolean | yes | Must be `true` for MVP invites. |
| `requires_handshake` | boolean | yes | Must be `true`. |
| `requires_approval` | boolean | yes | Whether approval is expected after handshake. |
| `nonce` | string | yes | Invite random nonce, not device-derived. |
| `signature` | string | yes | Signature by `inviter_key`. |

## State Transitions

```text
CREATED -> IMPORTED -> HANDSHAKE_PENDING -> APPROVAL_PENDING -> CERTIFICATE_ISSUED -> CONSUMED
CREATED -> IMPORTED -> HANDSHAKE_PENDING -> REJECTED
CREATED -> EXPIRED
CREATED -> IMPORTED -> HANDSHAKE_PENDING -> EXPIRED
```

If no approval is required, `HANDSHAKE_PENDING` may transition directly to
`CERTIFICATE_ISSUED` after inviter confirmation.

## Validation Rules

- Invite import must not create active membership.
- Invite import must not create an active relationship before handshake.
- `one_time` and `requires_handshake` must be `true`.
- `expires_at` must be checked before handshake and before certificate issuance.
- `nonce` must come from secure randomness and must not be derived from device
  identifiers.
- If the inviter lacks approval rights, the result must be pending approval.
- Consumed invites cannot be reused.

## Privacy And Security Notes

QR codes can be copied. Treat them as bearer invite attempts, not proof of real
human identity. The handshake must confirm the concrete person/device.

The invite payload must not contain phone number, email, account id, public
directory id or public discovery endpoint.

## JSON Example

```json
{
  "type": "invite",
  "version": 1,
  "invite_id": "inv_01HY9X7VK5E9B9E1S8W3JX4A0P",
  "realm_id": "realm_demo_dissertation",
  "subnet_id": null,
  "space_id": null,
  "scope": "direct",
  "inviter_key": "ed25519-pub:BASE64URL...",
  "inviter_fingerprint": "kraken:7N4Q-9Q2C-P8DM-43TA",
  "created_at": "2026-05-17T09:05:00Z",
  "expires_at": "2026-05-17T09:20:00Z",
  "one_time": true,
  "requires_handshake": true,
  "requires_approval": true,
  "nonce": "base64url-random-32-bytes",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should invite payloads include a handshake prekey in v1 or reference a
  separate handshake payload?
- Should one-time invite consumption be proven by signed receipt or only local
  inviter state in MVP?
