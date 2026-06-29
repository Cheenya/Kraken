# Kraken Protocol Specification

This directory contains early protocol specifications for Kraken core models.
The specs are implementation-oriented but still draft-level: field names,
states, validation rules and JSON shapes may change as the Android prototype,
simulator and native core are implemented.

Kraken is an Android-first accountless P2P mesh messenger prototype for
dissertation research. It is not a production security claim and does not prove
that the dissertation elliptic-curve research improves production cryptography.

## Core Rules

- Kraken is invite-only.
- There is no public discovery for users, realms, subnets, channels, groups,
  city networks or nearby communities.
- A QR invite does not grant membership directly.
- A one-time QR starts a mandatory handshake.
- Membership requires a signed membership certificate.
- A new identity key means a new user.
- Unlink is bilateral for honest clients.
- Device identifiers must not be used as key material.
- Root governance cannot decrypt messages.
- Tombstone deletion is best-effort and cannot be guaranteed on malicious
  devices.
- Direct messages are primary.
- Channels are preferred over large groups.
- Group chats are small and limited.

## Model Index

- [Identity](schemas/identity.md)
- [Invite](schemas/invite.md)
- [Membership Certificate](schemas/membership_certificate.md)
- [Invite Edge](schemas/invite_edge.md)
- [Relationship](schemas/relationship.md)
- [Unlink Notice](schemas/unlink_notice.md)
- [Packet](schemas/packet.md)
- [Receipt](schemas/receipt.md)
- [Tombstone](schemas/tombstone.md)
- [Complaint](schemas/complaint.md)
- [Complaint Aggregate](schemas/complaint_aggregate.md)
- [Realm Policy](schemas/realm_policy.md)
- [Approval Policy](schemas/approval_policy.md)
- [Capacity Token](schemas/capacity_token.md)

## Common Naming

Use these names consistently across Kotlin, C++ and simulator code:

| Name | Meaning |
| --- | --- |
| `identity_key` | Public key that defines a Kraken user identity. |
| `fingerprint` | Stable display form or hash of `identity_key`. |
| `realm_id` | Invite-only mesh realm identifier. |
| `membership_id` | Realm-scoped identifier from a signed certificate. |
| `relationship_id` | User-to-user relationship instance. |
| `invite_id` | One-time invite attempt identifier. |
| `invite_edge_id` | Accepted inviter-invitee accountability edge. |
| `space_id` | Optional direct/small-group/channel space identifier. |
| `subnet_id` | Optional realm subnet identifier. |

## Identity Boundary

Kraken identity is:

```text
display_name + identity_keypair + fingerprint
```

The display name is only a label. The public identity key and fingerprint are
the cryptographic identity.

Kraken must not add phone registration, email registration, username login,
password login, social login, account server registration or public profile
directory fields.

Identity keys must be generated from platform CSPRNG / Android Keystore
abstractions. The following values must never be key material:

- IMEI
- Android ID
- phone number
- SIM identifiers
- MAC address
- serial number
- hardware fingerprint
- display name
- realm name

## New Key Means New User

A new identity key creates a new user. Do not define transparent key rotation in
these MVP specs.

A new key does not inherit:

- memberships;
- contacts;
- trust state;
- invite edges;
- courier score;
- relay reliability;
- moderation state;
- complaint state.

If a key is compromised, the expected flow is: leave old realms where possible,
delete the old local identity, create a new identity, and rejoin through new
invites.

## Invite And Membership Flow

A one-time QR is an invite attempt, not membership.

Required flow:

1. Inviter creates a one-time invite.
2. Invitee scans or imports the invite.
3. Join request is created.
4. Inviter and invitee perform a handshake.
5. Inviter confirms the concrete person/device.
6. If approval is required, the request enters pending approval.
7. Membership certificate is issued only after approval.
8. Invite is marked consumed.
9. Accepted invite creates an invite edge.

Pending users cannot write first, post in the target space, invite others or
receive backlog. They can only reply to admin/moderator approval messages.

## Governance Boundary

Root/maintainer governance is not a message-reading authority. It may handle
capacity increases, governance abuse, mass complaints against realm/channel/group
administration and large-scale capability restrictions.

Ordinary user disputes are local realm/channel/group moderation issues by
default. Root governance must not decrypt messages.

## Delivery Boundary

No static route is assumed. Kraken uses opportunistic store-carry-forward
delivery inspired by bounded gossip / Spray-and-Wait.

Packets carry encrypted payloads. Relay nodes temporarily store encrypted
packets and should enforce TTL, expiration, copy budget and tombstones.

Recipient hints may leak metadata. They are accepted as an MVP simplification
and should be improved in future privacy work.

### Current Android Prototype Exception

The Android research prototype currently uses a local LAN NSD/TCP transport with
prototype JSON packet payloads and placeholder proof fields. The Android code now
contains an Adamova-bound crypto envelope layer: accepted crypto-profile metadata
is validated against local admission policy and bound into AEAD associated data.
The protected path serializes message ciphertext as `ENCRYPTED_MESSAGE_JSON`,
while production payload policy rejects plaintext messages when the Adamova-bound
protector is required. Legacy `LOCAL_MESSAGE_JSON` remains available only for
compatibility and older transport fixtures. Runtime message keys are derived from
the QR-handshake invite secret and relationship/session/profile context with
HKDF-SHA256. Production public-key key agreement, Keystore-backed private keys,
signatures, replay hardening and external review remain open.

LAN discovery is not trust. Contacts become message-eligible only after the
offline QR handshake reaches `ACTIVE`.

## Open Questions

- Which exact public-key and signature encodings should be used in v1 payloads:
  multibase, raw base64url, COSE_Key, JWK or another format?
- Should JSON examples become machine-validated JSON Schema documents before
  Kotlin/C++ model implementation?
- Should read receipts be disabled by default in the first Android UI?
