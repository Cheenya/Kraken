# Kraken Identity And Unlink Policy

This document fixes two important governance and identity decisions:

1. unlinking a user-to-user relationship is bilateral for honest clients;
2. a new identity key means a new user.

It complements:

- `kraken-current-concept.md`;
- `mobile-p2p-messenger-plan.md`;
- `mobile-p2p-messenger-plan-addendum.md`.

## 1. Bilateral Unlink Policy

A user-to-user relationship requires an active relationship state on both honest clients.

When user A removes user B, this is not only a local UI deletion. It is a protocol-level relationship termination.

Expected flow:

1. A chooses to remove B.
2. A selects an unlink reason.
3. A locally blocks incoming messages from B for that relationship/key.
4. A sends B a signed `unlink_notice`.
5. B receives the `unlink_notice`.
6. B's honest client marks the relationship as no longer active.
7. B cannot send messages to A through that relationship anymore.
8. Re-activation requires a new QR/invite exchange and a new handshake.

Important practical rule:

```text
After unlink, honest clients must not allow sending messages to the remover until a new active relationship is created.
```

## 2. Relationship States

Suggested relationship states:

```text
ACTIVE
UNLINK_REQUESTED
UNLINKED
BLOCKED_BY_PEER
REJOIN_REQUIRED
```

Meaning:

- `ACTIVE` — normal relationship;
- `UNLINK_REQUESTED` — unlink notice has been issued but not yet confirmed/observed;
- `UNLINKED` — relationship ended;
- `BLOCKED_BY_PEER` — peer has ended the relationship;
- `REJOIN_REQUIRED` — communication can resume only after new QR/invite + handshake.

## 3. Honest Client Behavior

If B receives a valid unlink notice from A, B's client must:

- disable the message composer for that relationship;
- stop generating new direct messages to A through the old relationship;
- show a clear UI notice;
- require a new QR/invite + handshake before returning to `ACTIVE`.

Suggested UI text:

```text
This user ended the relationship with you.
You can no longer send messages in this chat.
To restore communication, exchange a new QR/invite and complete a new handshake.
```

## 4. Malicious Client Caveat

Kraken cannot physically force a malicious or modified client to obey an unlink notice.

Therefore the remover must also block locally:

```text
A unlinks B -> A rejects future packets/messages from B's old relationship/key.
```

Correct security wording:

```text
For honest clients, unlink is bilateral. A malicious client may attempt to send packets after unlink, but the remover rejects messages from the old relationship/key.
```

## 5. Unlink Reasons

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

Negative unlink reasons must create a complaint event in the linked mesh realm if the relationship belongs to a realm.

Neutral unlink reasons do not automatically create complaints.

## 6. Re-Activation After Unlink

A removed relationship must not be silently restored.

To restore communication:

1. both users must intentionally exchange a new QR/invite or key payload;
2. a new handshake must be completed;
3. a new relationship id should be created;
4. the new relationship becomes `ACTIVE` only after consent is re-established.

This prevents unwanted continued contact after unlink.

## 7. New Key Means New User

Kraken does not treat identity-key changes as transparent account recovery.

Rule:

```text
A new identity key means a new user.
```

A new identity key does not automatically inherit:

- old realm memberships;
- old contacts;
- old invite edges;
- old courier score;
- old relay reliability;
- old trust state;
- old moderation state.

## 8. Key Compromise Flow

If a user believes their key is compromised, the recommended flow is:

1. mark the current identity as compromised;
2. leave old mesh realms where possible;
3. send leave/key-compromise notices where possible;
4. delete the old local identity;
5. create a new identity key;
6. rejoin realms through new invites;
7. rebuild contacts through new QR/invite exchanges.

If the old key is unavailable, some notices may be impossible to send. This is acceptable and must be documented.

## 9. No Transparent Key Rotation In MVP

The old idea of deleting a contact because of `KEY_ROTATION_OR_NEW_PROFILE` is not part of the current model.

Key rotation should not be an unlink reason.

For MVP:

```text
new key = new user = new invites required
```

Transparent key rotation, if ever added, must be treated as a separate advanced feature and must not silently transfer trust or membership.

## 10. Identity Recreation Limits

The application should discourage identity recreation abuse.

Possible local limits:

```text
identity recreation cooldown
max identity resets per time window
extra confirmation for repeated resets
local risk warning after frequent recreation
```

Example policy:

```text
No more than 2 identity recreations per 7 days without explicit warning and cooldown.
```

However, local limits are not a cryptographic guarantee. A user may clear app data, reinstall the app or use a modified client.

The real anti-abuse boundary is:

```text
new identity does not have realm membership;
new identity needs a new invite;
inviter-invitee accountability applies;
new members may have cooldown restrictions.
```

## 11. New Member Cooldown

A newly created identity that joins a realm should be treated as a new member.

Possible realm-level limitations:

- cannot invite others for a period;
- limited group posting;
- limited channel publishing;
- lower relay quota;
- lower complaint threshold;
- pending approval where required.

This helps reduce abuse from identity recreation.

## 12. Courier Score And Trust Transfer

Courier Score and technical trust metrics must not automatically transfer to a new identity.

A user may locally keep old statistics as an archive, but the network should treat the new identity as new.

## 13. Complaint And Moderation Transfer

Complaint history should not be automatically transferred to a new key because Kraken cannot prove that a new key belongs to the same human.

Instead, the system should watch patterns in invite accountability:

```text
One inviter repeatedly brings new identities that quickly receive complaints.
```

The appropriate action is to limit the inviter's invite privileges, not to pretend the system can globally identify the same person.

## 14. Core Rules

Current rules to preserve:

```text
1. A relationship must be ACTIVE on both honest clients for messaging.
2. Unlink is bilateral for honest clients.
3. After receiving unlink notice, the removed peer cannot send through the old relationship.
4. The remover locally rejects future packets from the old relationship/key.
5. Relationship restoration requires a new QR/invite + handshake.
6. New identity key means new user.
7. Key compromise means leaving/rejoining, not transparent key rotation.
8. Local identity recreation limits help UX, but invite-only membership is the real boundary.
```

## 15. Codex Task Seed

### Task: Implement Relationship Unlink And Identity Recreation Policy Models

Acceptance criteria:

- relationship state enum includes ACTIVE, UNLINK_REQUESTED, UNLINKED, BLOCKED_BY_PEER, REJOIN_REQUIRED;
- unlink notice model exists and is signed;
- receiving valid unlink notice disables sending to the remover;
- remover locally rejects future packets from old relationship/key;
- reactivation requires new QR/invite + handshake;
- unlink reason codes match current policy;
- negative unlink creates complaint event when linked to a realm;
- identity recreation policy states that new key means new user;
- key compromise flow does not preserve memberships automatically;
- courier score and trust are not automatically transferred.
