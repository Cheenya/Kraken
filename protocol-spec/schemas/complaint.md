# Complaint

## Purpose

Represents an ordinary complaint event created by a user action or by a negative
unlink reason in a linked realm.

## Scope

Complaints support local moderation. A single complaint should not automatically
ban a user. Root/maintainer governance is only for governance-level abuse, not
ordinary disputes by default.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `complaint`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `complaint_id` | string | yes | Complaint id. |
| `realm_id` | string | yes | Realm where moderation applies. |
| `subnet_id` | string | optional | Optional subnet context. |
| `space_id` | string | optional | Optional direct/group/channel space. |
| `scope` | string | yes | `direct`, `small_group` or `channel`. |
| `reporter_key` | string | yes | Reporter public identity key. |
| `subject_key` | string | yes | Reported public identity key. |
| `category` | string | yes | Complaint category. |
| `source` | string | yes | `unlink_notice`, `manual_report`, `moderator_note`. |
| `source_ref` | string | optional | Unlink notice or message reference. |
| `created_at` | string | yes | ISO-8601 UTC timestamp. |
| `evidence_mode` | string | yes | `none`, `metadata_only`, `attached_plaintext`. |
| `governance_escalation_requested` | boolean | yes | True only for governance-level claims. |
| `signature` | string | yes | Signature by `reporter_key`. |

Categories:

```text
UNWANTED_MESSAGES
SPAM
THREAT_PRESSURE_OR_ETHICS_ABUSE
GOVERNANCE_ABUSE
OTHER
```

## State Transitions

```text
SUBMITTED -> AGGREGATED -> REVIEWED
SUBMITTED -> DISMISSED
AGGREGATED -> ESCALATED_TO_LOCAL_MODERATION
AGGREGATED -> ESCALATED_TO_GOVERNANCE_REVIEW
```

Governance escalation is only for governance abuse or mass complaints against
administration, not ordinary user disputes by default.

## Validation Rules

- Negative unlink reasons in a linked realm create complaint events.
- Neutral unlink reasons must not automatically create complaints.
- Signature must verify under `reporter_key`.
- Reporter and subject must be valid members or known scoped participants for
  the relevant moderation context.
- One complaint must not automatically ban a user.
- Evidence attachment is voluntary.

## Privacy And Security Notes

Moderators cannot read private messages by default. They may rely on voluntary
evidence submitted by complainants, signed envelopes, aggregates and observable
protocol abuse.

Complaint data is sensitive social metadata and should be scoped to the relevant
realm/moderation context.

## JSON Example

```json
{
  "type": "complaint",
  "version": 1,
  "complaint_id": "cmp_01HY9Y19H3X6YSMZF878MW7MZC",
  "realm_id": "realm_demo_dissertation",
  "subnet_id": null,
  "space_id": "direct_alice_bob",
  "scope": "direct",
  "reporter_key": "ed25519-pub:ALICE_BASE64URL...",
  "subject_key": "ed25519-pub:BOB_BASE64URL...",
  "category": "UNWANTED_MESSAGES",
  "source": "unlink_notice",
  "source_ref": "unlink_01HY9XN4M66JW58MEFJYMPJ6AV",
  "created_at": "2026-05-17T10:01:00Z",
  "evidence_mode": "metadata_only",
  "governance_escalation_requested": false,
  "signature": "sig:BASE64URL..."
}
```

## Open Questions

- Which complaint categories should be visible in the first Android MVP UI?
- How long should local complaint metadata be retained?
