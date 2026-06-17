# Capacity Token

## Purpose

Authorizes a realm capacity increase without giving root governance message
decryption authority.

## Scope

Capacity tokens are signed by root/maintainer governance and consumed by realm
policy. They are for large-scale capability control, not ordinary user disputes.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `capacity_token`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `capacity_token_id` | string | yes | Token id. |
| `realm_id` | string | yes | Realm receiving capacity. |
| `capacity` | integer | yes | New capacity limit. |
| `issued_to` | string | yes | Realm owner/admin key. |
| `issued_by` | string | yes | Root/maintainer key. |
| `issued_at` | string | yes | ISO-8601 UTC timestamp. |
| `expires_at` | string | yes | Token expiry timestamp. |
| `reason` | string | yes | Capacity increase reason/category. |
| `signature` | string | yes | Signature by `issued_by`. |

## State Transitions

```text
ISSUED -> APPLIED
ISSUED -> EXPIRED
APPLIED -> SUPERSEDED
```

## Validation Rules

- Signature must verify under trusted root/maintainer governance key.
- `capacity` must be greater than current realm capacity.
- Token must match `realm_id` and `issued_to`.
- Token must not be expired when applied.
- Applying a token updates realm policy capacity but does not grant access to
  message plaintext.

## Privacy And Security Notes

Root governance cannot decrypt messages. A capacity token is only a governance
capability for member-count limits.

Capacity increases should not introduce public discovery. Entry remains
invite-only.

## JSON Example

```json
{
  "type": "capacity_token",
  "version": 1,
  "capacity_token_id": "ctok_01HY9YAYHJQPXZRSQK9T35VP66",
  "realm_id": "realm_demo_dissertation",
  "capacity": 2000,
  "issued_to": "ed25519-pub:REALM_OWNER_BASE64URL...",
  "issued_by": "ed25519-pub:KRAKEN_ROOT_BASE64URL...",
  "issued_at": "2026-05-17T09:50:00Z",
  "expires_at": "2026-06-17T09:50:00Z",
  "reason": "approved_realm_growth",
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Should root capacity token verification be pinned in app builds or distributed
  through an updateable trust bundle?
