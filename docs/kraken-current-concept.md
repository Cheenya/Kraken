# Kraken Current Concept

This document captures the current working concept for Kraken after the latest design discussion.
It is intentionally a living document and should be updated when product, governance or dissertation framing changes.

## 1. Dissertation Boundary

The dissertation must not assume that the rational elliptic curve algorithm already improves cryptography.

Correct research framing:

```text
We investigate whether the algorithm for analyzing rational elliptic curves provides measurable benefit in a cryptographic research workflow.
```

Potential outcomes are all valid research outcomes:

- the algorithm improves a preliminary diagnostic stage;
- the algorithm reduces the set of curves requiring heavier verification;
- the algorithm is useful for classification and reporting, but not runtime crypto;
- the algorithm works only in laboratory conditions;
- the algorithm shows limited practical value, which is still an important result.

Kraken is the applied demonstration prototype, not the proof of the hypothesis.

## 2. Product Boundary

Kraken is an Android-first pre-production P2P mesh messenger prototype. This
concept document describes target product boundaries; current implementation
status is governed by the dated readiness/evidence reports under `reports/out`.

It demonstrates:

- local prototype identity; production Keystore-backed cryptographic identity
  is still roadmap work;
- invite-only trust establishment;
- QR/key-based onboarding;
- opportunistic P2P delivery;
- transit forwarding;
- delivery receipts;
- battery-aware relay policies;
- local mesh realms;
- a research panel for elliptic-curve diagnostics.

It is not:

- a production security claim;
- a public anonymous network;
- a phone/email account messenger;
- a global social network;
- a system that can guarantee deletion from malicious devices;
- proof that the dissertation algorithm accelerates production cryptography.

## 3. Core Product Principles

```text
No public discovery.
Invite-only entry.
One-time QR starts a mandatory handshake.
Membership requires a signed certificate.
Inviter-invitee accountability is recorded.
Direct messages are primary.
Channels are preferred over large group chats.
Small group chats are strictly limited.
Relay participation is separate from community sanctions.
Root governance cannot read messages.
```

## 4. Platform Strategy

First target:

```text
Android-first
```

Suggested stack:

```text
Android app: Kotlin + Jetpack Compose
Optimized deterministic core: C++ through NDK/JNI
```

Kotlin responsibilities:

- UI;
- Android lifecycle;
- permissions;
- QR scan/generation;
- local settings;
- transport adapters;
- WorkManager/foreground-service orchestration;
- battery-aware scheduling.

C++ responsibilities:

- packet model;
- route/forwarding decisions;
- transit buffer policy;
- metrics;
- deterministic protocol logic;
- rational elliptic curve diagnostics;
- performance-critical research code.

Important rule:

```text
Kotlin orchestrates the Android platform.
C++ implements deterministic protocol/research core.
```

## 5. Identity Model

There is no phone, email, login, username registration or central account.

A user has:

```text
display_name + identity_keypair + fingerprint
```

The display name is only a label. The real identity is the public key/fingerprint.

Kraken is better described as a pseudonymous trust network, not a fully anonymous network.

## 6. No Discovery Policy

There is no search for:

- mesh realms;
- city networks;
- groups;
- channels;
- nearby public communities.

Entry is only possible through:

- one-time QR invite;
- direct key exchange;
- external unique contact channel;
- personal meeting.

This reduces uncontrolled growth, spam and moderation pressure.

## 7. One-Time QR And Mandatory Handshake

A QR invite does not grant membership by itself.

It starts a one-time handshake.

Flow:

1. Inviter creates one-time invite.
2. Invitee scans QR.
3. Join request is created.
4. Inviter and invitee perform handshake.
5. Inviter confirms the actual person/device.
6. If the inviter is not authorized to approve directly, the request enters pending moderation.
7. Membership certificate is issued after approval.
8. Invite is marked consumed.

One QR means one invite attempt, not unlimited access.

## 8. Inviter-Invitee Edge

Each accepted invite creates an inviter-invitee edge.

Purpose:

- trust chain;
- invite accountability;
- invite statistics;
- moderation context;
- anti-abuse;
- limiting invite privileges for users who repeatedly invite abusive participants.

A bad invitee should not automatically punish the inviter. Repeated patterns should affect invite trust.

## 9. Membership Certificates

A realm membership is represented by a signed certificate.

Draft concept:

```json
{
  "type": "membership_certificate",
  "realm_id": "...",
  "membership_id": "...",
  "member_key": "...",
  "issued_by": "...",
  "issued_at": "...",
  "expires_at": "...",
  "capabilities": ["send_direct", "relay_basic"],
  "signature": "..."
}
```

A new local identity key does not automatically grant realm access.

## 10. Pending Approval

If a non-admin invites someone into a realm, chat, channel or subnet where they cannot approve membership, the invite creates a pending request.

Pending user rights:

- cannot write first;
- cannot post to the group/channel;
- cannot invite others;
- cannot receive backlog;
- can only reply to admin/moderator approval messages.

The approval UI should show:

- who invited the user;
- where the user wants to join;
- invite trust summary;
- complaint/restriction summary if available.

## 11. Approval Policy

Approval is policy-based.

Examples:

```text
single_admin
threshold_approval
```

If there is one admin, the decision is theirs.

If there is an admin and four moderators, policy may require two approvals.

If a realm grows while having only one admin, the UI should warn:

```text
This realm has only one administrator. Add at least two moderators for safer governance.
```

## 12. Contact Unlink Reasons

When a user removes a user-to-user link, ask for a reason.

Allowed reasons:

```text
ENDED_INTERACTION
UNWANTED_MESSAGES
SPAM
THREAT_PRESSURE_OR_ETHICS_ABUSE
OTHER
```

Neutral reasons:

```text
ENDED_INTERACTION
OTHER
```

Negative reasons:

```text
UNWANTED_MESSAGES
SPAM
THREAT_PRESSURE_OR_ETHICS_ABUSE
```

Negative reasons must create a complaint event in the linked mesh realm if the relationship belongs to a realm.

Removed reasons:

```text
MISTAKEN_CONTACT
KEY_ROTATION_OR_NEW_PROFILE
```

These are not appropriate for invite-only onboarding. Key rotation should be a separate flow, not an unlink reason.

## 13. Complaint Routing

Ordinary complaints go to the local moderators/admins of the relevant realm, chat, channel or subnet.

Root/maintainer governance should not receive ordinary user disputes.

Root escalation only applies to governance-level abuse, such as:

- mass complaints against a realm administration;
- mass complaints against a channel/group administration;
- moderators ignoring abuse;
- channels/realms becoming abuse hubs;
- suspicious mass invite behavior by administrators.

## 14. Complaint Effects

A single complaint should not normally ban a user.

Complaint effects should be staged:

```text
single complaint -> local protection + counter
multiple independent complaints -> temporary limitation
mass complaints -> moderation review
governance abuse complaints -> root review
```

Possible temporary restrictions:

- suspend group posting;
- suspend channel publishing;
- suspend invite creation;
- suspend relay rights inside a specific realm;
- restrict a channel/chat/realm under review.

## 15. Governance Abuse Review

If mass independent complaints target a channel/chat/realm or its administration, the unit may be marked:

```text
PAUSED_UNDER_REVIEW
```

Possible automatic effects:

- pause new posts in the channel/chat;
- pause new invite creation;
- limit admins/moderators from creating new group interaction spaces;
- preserve appeal/review channel;
- do not decrypt user messages.

This must use thresholds to resist complaint attacks.

Thresholds should consider:

- number of unique complainants;
- whether complainants come from independent invite branches;
- account/membership age;
- prior false complaint behavior;
- complaint category.

## 16. Moderation Without Content Access

Moderators cannot read private messages by default.

Moderation can rely on:

- voluntary evidence submitted by complainants;
- attached message plaintext from the reporter;
- signed message envelopes;
- complaint aggregates;
- observable protocol abuse;
- rate-limit and flood signals.

The user may choose whether to attach message content to a complaint.

## 17. Realm Capacity

Realms should have capacity limits to prevent uncontrolled growth.

Example default:

```text
ordinary realm capacity = 500 members
```

When the signed known member count reaches capacity:

```text
invite generation is blocked
```

Options:

- request capacity increase;
- create another ordinary realm;
- remove inactive members if policy allows.

## 18. Member Counting

Do not trust arbitrary peer-reported counts.

MVP recommendation:

```text
signed member_count statement by realm administration
```

Future option:

```text
certificate-based count using realm-specific membership_id
```

A membership id can help count unique memberships inside a realm, but it does not prove unique real humans.

## 19. Capacity Increase

Capacity increase should require a signed root/maintainer capacity token.

Draft concept:

```json
{
  "type": "capacity_token",
  "realm_id": "...",
  "capacity": 2000,
  "issued_to": "realm_owner_key",
  "issued_by": "kraken_root_key",
  "expires_at": "...",
  "signature": "..."
}
```

Target design: root governance cannot decrypt messages. Current prototype
status must not be read as production E2EE evidence; production encrypted
packet envelopes are still roadmap work.

## 20. Direct Messages, Groups And Channels

Primary mode:

```text
direct messages
```

Group chats:

- small only;
- strict member limits;
- TTL;
- max backlog;
- slow mode;
- not suitable for large active communities.

Channels:

- preferred for wider communication;
- publisher to subscribers;
- latest-N or TTL-based backlog;
- controlled publisher roles;
- complaints target channel administration if needed.

## 21. Scoped Traffic

Packets should not pollute the entire mesh.

Every packet should have scope fields:

```text
realm_id
subnet_id optional
space_id optional
scope: direct | small_group | channel
```

A node should relay only traffic matching its membership, subscriptions and relay policy.

## 22. Delivery Model

No static route is assumed.

Use opportunistic store-carry-forward delivery inspired by bounded gossip / Spray-and-Wait.

Packet fields:

```text
packet_id
message_id
realm_id
recipient_hint
ttl_hops
expires_at
copy_budget
min_reserve
social_reserve
encrypted_payload
```

Relay nodes only store encrypted packets temporarily.

## 23. Delivery Receipts And Message Status

Message statuses:

```text
PENDING
SENT_TO_NETWORK
DELIVERED
READ
NOT_CONFIRMED
EXPIRED
FAILED
```

Meaning:

```text
one gray check = first hop accepted the packet
two gray checks = final client confirmed delivery
two blue checks = final client confirmed read
```

Read receipts must be optional.

## 24. Tombstones

After delivery, sender or recipient may emit a signed tombstone.

Purpose:

```text
tell honest relay nodes to delete the transit packet and stop forwarding it
```

Deletion is best-effort and cannot be enforced on malicious devices.

TTL and expiration remain mandatory.

## 25. Relay Participation vs Community Sanctions

Relay participation is a technical app mode.

Community membership is social/governance state.

A user may be removed from a group or channel without being globally excluded as a relay.

Bad behavior in a chat does not automatically imply global relay ban.

## 26. Social-Aware Routing

Default should avoid leaking contact graphs.

Routing levels:

```text
Level 0: Spray-and-Wait + TTL + copy_budget
Level 1: local relay scoring without contact-list exchange
Level 2: optional trusted hints with consent
Level 3: research relay tokens / private route tags
```

Reserve policy:

```text
Do not forward the last reserve copy to a low-score relay.
```

## 27. Courier Score

Courier Score is a user-facing engagement metric.

Relay Reliability is a technical routing metric.

They must be separate.

Courier Score rules:

- local by default;
- no global leaderboard in MVP;
- daily delayed aggregation;
- no real-time point increments;
- no GPS;
- no recipient details;
- no route details;
- no exact timestamps.

## 28. Battery-Aware Modes

User relay modes:

```text
Only my messages
Help a little
Help on charging/Wi-Fi
Active courier
Research mode
```

App states:

```text
OFFLINE
IDLE
PASSIVE_SCAN
ACTIVE_HANDSHAKE
ONLINE_SESSION
TRANSIT_FORWARDING
LOW_POWER
```

The app must avoid constant background transmission.

## 29. Realm Pause / Archive / Travel

A user may belong to many realms.

Realm state per user:

```text
active
only direct
paused
archived
left
```

City realms are social realms, not strict geographic areas.

A user may move from one city to another while keeping old realm memberships.

## 30. Key Caveats

- A new local identity can always be created, but it cannot enter an invite-only realm without a new invite and approval.
- One-time QR requires handshake/consumed state; QR alone is not enough.
- Membership ids count memberships, not real human uniqueness.
- Root governance reduces pure decentralization, but helps control capacity and large-scale abuse in pre-production.
- Deletion on relays is best-effort.
- The messenger is not evidence that the dissertation algorithm is useful; it is a testbed and demonstration context.

## 31. Immediate Documentation TODO

Update `docs/mobile-p2p-messenger-plan.md` to reflect:

- dissertation hypothesis wording;
- no discovery policy;
- one-time QR + handshake;
- inviter-invitee accountability;
- new unlink reason codes;
- mandatory local complaint on negative unlink;
- root escalation only for governance abuse;
- capacity limits and root tokens;
- groups limited, channels preferred;
- relay participation separated from community sanctions;
- pseudonymous trust wording.
