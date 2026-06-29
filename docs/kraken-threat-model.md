# Kraken Threat Model Draft

This is an initial threat model for the Kraken Android-first dissertation prototype. It describes expected risks and boundaries; it does not claim that the current prototype solves them.

## Goals

- Support invite-only local identity and relationship workflows without phone/email/account registration.
- Keep public discovery out of the product model.
- Make identity key/fingerprint the cryptographic identity; display name remains only a label.
- Preserve the rule: new key means new user.
- Keep root/maintainer governance separate from message decryption authority.
- Model opportunistic delivery, receipts and tombstones honestly.
- Make privacy/security limitations visible before production crypto or transport work.

## Non-Goals

- Production-secure messenger claims.
- Central account recovery.
- Transparent key rotation.
- Public user, realm, channel or group directories.
- Server-side message history.
- Global moderation service for ordinary disputes.
- Custom cryptographic primitives.

## Assets

- Local identity key material or key references.
- Public key and fingerprint.
- Relationship state and peer fingerprints.
- Invite payloads and pending imports.
- Membership certificates and realm policies.
- Transit packets and encrypted payloads.
- Receipts, tombstones and unlink notices.
- Complaint events and aggregates.
- Courier Score summaries and relay reliability state.
- Research diagnostic inputs/outputs.

## Actors

- Honest local user.
- Honest peer.
- Malicious relay.
- Malicious invitee.
- Abusive inviter.
- Compromised device holder.
- Realm admin/moderator.
- Governance/root maintainer.
- Local observer with device access.
- Network observer for future transport phases.

## Trust Boundaries

- Device-local app storage.
- Android OS, Android Keystore and platform secure randomness.
- QR/manual invite transfer.
- Peer-to-peer relationship handshake.
- Realm membership certificate issuance.
- Opportunistic relay/transit buffer.
- Future LAN debug transport.
- Native C++/JNI boundary.

## Device Compromise

If a device is compromised, local keys, pending invites, relationship metadata and message state may be exposed or modified. The current MVP storage is SharedPreferences-backed and is not production key storage.

Required future work:

- Move private key handling to Android Keystore.
- Add explicit local data deletion flows.
- Document that compromised key recovery requires creating a new identity and rejoining by invite.

## Malicious Relay

A relay may:

- drop packets;
- refuse to forward;
- keep packets past tombstone/expiration;
- attempt traffic analysis;
- lie about local buffer state.

Relays should not decrypt payloads. Tombstones are best-effort deletion signals for honest relays only.

## Malicious Invitee

A malicious invitee may:

- share invite payload screenshots;
- attempt duplicate imports;
- join and send unwanted messages after approval;
- trigger complaints or moderation load.

Mitigations:

- one-time invite semantics;
- duplicate invite and duplicate key rejection;
- mandatory handshake before active relationship;
- membership certificate required for realm access;
- inviter-invitee edge for accountability without automatic punishment.

## Abusive Inviter

An inviter may repeatedly invite abusive peers or use invitation pressure socially.

Mitigations:

- invite edge history;
- aggregate complaint context;
- repeated-pattern moderation;
- no automatic punishment for one bad invitee.

## Metadata Leakage

Potential metadata includes:

- peer fingerprints;
- relationship graph;
- realm membership;
- recipient/sender hints;
- packet timing and copy budget;
- relay participation patterns;
- receipt timing;
- complaint aggregation.

Current model acknowledges recipient hints may leak metadata. Future work should harden hint design, batching, timing and summary exchange.

## Social Graph Leakage

Invite edges, relationship states and realm membership certificates can reveal social structure. They should stay local or scoped to the relevant realm unless protocol rules explicitly require sharing.

Review points:

- avoid global graph export;
- avoid public directories;
- avoid leaderboard-like relay/social metrics;
- keep Courier Score aggregated and delayed.

## QR Leak

A leaked QR/manual invite payload may allow an attacker to attempt import/handshake. It must not grant membership directly.

Required rules:

- QR starts handshake only.
- One QR is intended for one invite attempt.
- Membership requires approval/certificate.
- Future signed invite validation must detect tampering.

## Duplicate Key Or Imported Key

Duplicate public keys may indicate a re-imported known peer or attempted impersonation/confusion. The app must not silently create a second contact for the same key.

Expected behavior:

- reject self-invite;
- reject duplicate invite IDs;
- reject or route duplicate public key to existing relationship review;
- never trust display name as identity.

## Lost Or Compromised Key

New key means new user. Kraken must not silently transfer:

- memberships;
- relationships;
- trust;
- invite edges;
- complaint/moderation state;
- Courier Score;
- relay reliability.

Compromise response:

- leave old realms where possible;
- delete old local identity if safe;
- create new identity;
- rejoin through new invites.

## Complaint Abuse

Complaints can be used for harassment or moderation overload.

Rules:

- a single complaint does not normally ban a user;
- complaint aggregates should become moderator cards;
- negative unlink reasons may create complaint events;
- neutral unlink reasons should not create complaints;
- governance escalation is reserved for governance-level abuse.

## Moderation Abuse

Moderators/admins may overreach by restricting users or abusing approval flows.

Mitigations to model:

- approval policy transparency;
- decision record;
- role eligibility checks;
- root governance cannot decrypt messages;
- root does not handle ordinary disputes by default.

## Root Governance Limitations

Root/maintainer governance may handle:

- capacity increase tokens;
- mass complaints against realm/channel/group administration;
- governance abuse;
- large-scale capability restrictions.

It must not be a message-reading authority and must not decrypt user content.

## Tombstone Limitations

Tombstones are signed or placeholder-signed deletion signals for honest relay nodes.

They cannot guarantee deletion from:

- malicious peers;
- compromised devices;
- screenshots or local copies;
- offline storage outside app control.

TTL and expiration remain mandatory.

## Local Storage Risks

Current app state is local MVP persistence. Risks include:

- exported/shared preference access on compromised devices;
- lack of production non-exportable key storage;
- missing encrypted-at-rest policy;
- no migration strategy beyond schema placeholder.

Future work:

- Android Keystore private key handling;
- encrypted local data where appropriate;
- storage schema migration tests;
- explicit local wipe paths.

## Future Crypto Review Requirements

Before production crypto implementation:

- choose reviewed primitives and libraries;
- avoid custom cryptographic primitives;
- define canonical serialization for signed payloads;
- test wrong-recipient and tamper failure;
- review key agreement, KDF, AEAD and signature boundaries;
- document Android Keystore behavior and fallback policy.

Candidate primitive direction remains:

- X25519 or equivalent for key agreement;
- HKDF;
- ChaCha20-Poly1305 or AES-GCM;
- platform secure randomness.

## Review Checklist

- No phone/email/login/account registration was added.
- No public discovery or global search exists.
- Identity is public key/fingerprint, not display name.
- QR/import never grants membership directly.
- Sending is only allowed for active relationships.
- Tombstones are described as best-effort.
- Root governance cannot decrypt messages.
- Research panel is diagnostic-only.
- Fake/test crypto is never presented as production crypto.
