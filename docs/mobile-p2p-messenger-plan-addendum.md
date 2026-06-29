# Mobile P2P Messenger Plan Addendum

This addendum synchronizes the older `mobile-p2p-messenger-plan.md` with the current Kraken concept.

For the complete snapshot, see `kraken-current-concept.md`.

## 1. Dissertation Wording

Do not claim that the rational elliptic curve algorithm already improves practical cryptography.

Use research wording:

```text
Investigate whether the rational elliptic curve diagnostic algorithm provides measurable benefit in a cryptographic research workflow.
```

The messenger is a demonstration context and pre-production prototype, not proof of the dissertation hypothesis.

## 2. No Discovery

Kraken has no public discovery for:

- realms;
- city networks;
- groups;
- channels;
- nearby communities;
- subnets.

Entry is invite-only.

## 3. One-Time QR And Handshake

One QR means one invite attempt.

A QR invite does not grant membership directly. It starts a mandatory handshake.

Membership requires approval and a signed membership certificate.

## 4. Inviter-Invitee Accountability

Every accepted invite creates an inviter-invitee edge.

This supports:

- trust chain;
- invite statistics;
- moderation context;
- limiting invite rights when repeated problematic invites appear.

One bad invitee should not automatically punish the inviter. Repeated patterns matter.

## 5. Pending Approval

If a non-admin invites someone into a realm, chat, channel or subnet, the result is a pending request.

A pending user:

- cannot write first;
- cannot post in the target space;
- cannot invite others;
- cannot receive backlog;
- can only reply to admin/moderator approval messages.

Approval can be single-admin or threshold-based.

If a realm grows with only one admin, UI should recommend adding at least two moderators.

## 6. Contact Unlink Reasons

Use only these unlink reasons:

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

Negative unlink reasons must create a complaint event in the linked mesh realm, if the relationship belongs to a realm.

Remove these older reasons from the plan:

```text
MISTAKEN_CONTACT
KEY_ROTATION_OR_NEW_PROFILE
```

Key rotation must be a separate flow.

## 7. Complaint Routing

Ordinary complaints go to local moderators/admins of the relevant realm, chat, channel or subnet.

Root/maintainer review is only for governance-level cases, such as:

- mass complaints against a realm administration;
- mass complaints against channel/group administration;
- repeated ignored local complaints;
- suspicious mass invite behavior;
- large interaction spaces that need to be paused for review.

## 8. Governance Review

When many independent complaints target a channel, chat, realm or its administrators, that unit may enter:

```text
PAUSED_UNDER_REVIEW
```

Possible temporary effects:

- pause new posts;
- pause new invites;
- limit admins/moderators from creating new group interaction spaces;
- preserve appeal/review channel;
- do not decrypt user messages.

Thresholds must consider unique complainants, invite-branch independence, membership age and complaint category.

## 9. Realm Capacity

Realms should have capacity limits to prevent uncontrolled growth.

Example default:

```text
ordinary realm capacity = 500 members
```

When the signed known member count reaches capacity, invite generation is blocked.

Options:

- create another ordinary realm;
- request capacity increase;
- remove inactive members if policy allows.

Capacity increase requires a signed root/maintainer capacity token.

Root governance cannot decrypt messages and should only control capacity and governance review.

## 10. Member Counting

Do not trust arbitrary peer-reported counts.

MVP recommendation:

```text
signed member_count statement by realm administration
```

Future option:

```text
certificate-based count using realm-specific membership_id
```

Membership ids count memberships/identities, not unique real humans.

## 11. Relay And Membership Are Separate

Relay participation is a technical app mode.

Community membership is social/governance state.

Bad behavior in one group or channel should not automatically produce a global relay ban.

## 12. Groups, Channels And Scope

Direct messages are primary.

Large group chats are risky in opportunistic mesh delivery.

Recommended policy:

- groups are small and limited;
- channels are preferred for wider communication;
- every packet has scope fields such as realm, subnet, space and direct/group/channel type;
- nodes should not relay unrelated traffic outside their memberships/subscriptions/policies.

## 13. Roadmap Adjustments

Add or update roadmap stages:

1. Android-first skeleton.
2. Local identity and one-time invite flow.
3. Membership certificates.
4. Inviter-invitee accountability.
5. Pending approval and threshold approval policy.
6. Contact unlink reason codes.
7. Complaint routing and local moderation.
8. Realm capacity and root capacity tokens.
9. Scoped direct/group/channel packet model.
10. Opportunistic delivery, receipts and tombstones.
11. Courier score and battery-aware relay modes.
12. Research panel for elliptic-curve diagnostics.

## 14. Codex Task Seeds

### Task: Sync Docs With Current Kraken Governance

Acceptance criteria:

- no public discovery is documented;
- one-time QR plus handshake is documented;
- inviter-invitee edge is documented;
- pending approval rights are documented;
- approval threshold policy is documented;
- unlink reason codes match this addendum;
- negative unlink creates local complaint event;
- ordinary complaints and root review are separated;
- capacity limits and root capacity tokens are documented;
- relay participation and community sanctions are separated.
