# Identity

## Purpose

Defines the local cryptographic identity of a Kraken user.

## Scope

Identity is local-first and accountless. It is used for fingerprints, invite
payloads, relationship handshakes, membership certificates and signed control
messages. It is not a phone/email/account registration model.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `identity`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `display_name` | string | yes | User-facing label only. Not trusted identity. |
| `identity_key` | string | yes | Public identity key encoding. |
| `fingerprint` | string | yes | Stable display/hash form derived from `identity_key`. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `capabilities` | string array | yes | Supported protocol capabilities. |

Private key material is never serialized in this public model.

## State Transitions

Identity lifecycle is local:

```text
NOT_CREATED -> ACTIVE -> COMPROMISED -> DELETED
NOT_CREATED -> ACTIVE -> DELETED
```

A new `identity_key` after deletion or compromise is a new user. Do not model
transparent key rotation.

## Validation Rules

- `display_name` must be present but must not be used as cryptographic identity.
- `identity_key` must be generated from platform CSPRNG / Android Keystore
  abstraction.
- `fingerprint` must be derived from `identity_key`, not user or device data.
- Duplicate active membership for the same `identity_key` in one realm must be
  rejected.
- A scanned/imported known `identity_key` should be shown as already known.

## Privacy And Security Notes

Never use IMEI, Android ID, phone number, SIM identifiers, MAC address, serial
number, hardware fingerprint, display name or realm name as key material.

The display name is mutable UI metadata. Changing it does not change identity.

## JSON Example

```json
{
  "type": "identity",
  "version": 1,
  "display_name": "Alice",
  "identity_key": "ed25519-pub:BASE64URL...",
  "fingerprint": "kraken:7N4Q-9Q2C-P8DM-43TA",
  "created_at": "2026-05-17T09:00:00Z",
  "capabilities": ["kraken.identity.v1", "kraken.invite.v1"]
}
```

## Open Questions

- Choose final key encoding and fingerprint formatting.
- Decide whether the first identity signature scheme is Ed25519, another
  reviewed signature scheme or a platform-backed abstraction.
