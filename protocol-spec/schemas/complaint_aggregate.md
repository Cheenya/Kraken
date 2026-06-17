# Complaint Aggregate

## Purpose

Summarizes complaint events into moderator-friendly cards instead of raw endless
complaint streams.

## Scope

Aggregates support local moderation decisions and governance-abuse escalation
when thresholds indicate administration-level problems.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `complaint_aggregate`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `aggregate_id` | string | yes | Aggregate id. |
| `realm_id` | string | yes | Realm scope. |
| `subnet_id` | string | optional | Optional subnet context. |
| `space_id` | string | optional | Optional direct/group/channel space. |
| `scope` | string | yes | `direct`, `small_group` or `channel`. |
| `subject_key` | string | optional | User subject, if user-level aggregate. |
| `subject_space_id` | string | optional | Channel/group/admin unit subject, if applicable. |
| `category_counts` | object | yes | Count by category. |
| `unique_reporter_count` | integer | yes | Number of unique reporters. |
| `independent_invite_branch_count` | integer | optional | Estimate for complaint independence. |
| `first_seen_at` | string | yes | ISO-8601 UTC timestamp. |
| `last_seen_at` | string | yes | ISO-8601 UTC timestamp. |
| `recommended_action` | string | yes | Moderator card recommendation. |
| `governance_escalation_eligible` | boolean | yes | True only for governance-level cases. |

## State Transitions

```text
OPEN -> LOCAL_REVIEW
OPEN -> LOCAL_ACTION_APPLIED
OPEN -> DISMISSED
LOCAL_REVIEW -> GOVERNANCE_REVIEW
LOCAL_REVIEW -> LOCAL_ACTION_APPLIED
LOCAL_ACTION_APPLIED -> CLOSED
DISMISSED -> CLOSED
```

## Validation Rules

- Aggregate source complaints must belong to the same realm and moderation
  subject.
- One complaint alone should normally produce `MONITOR`, not a punitive action.
- Repeated independent complaints may recommend temporary limitation.
- Governance escalation should require governance abuse or mass complaints
  against administration/channel/group governance.
- Aggregate output must avoid exposing unnecessary private message content.

## Privacy And Security Notes

Aggregates should minimize raw data exposure. Use counts, categories and scoped
references unless a reporter voluntarily attaches evidence.

Invite-branch independence is moderation context, not proof of real-human
uniqueness.

## JSON Example

```json
{
  "type": "complaint_aggregate",
  "version": 1,
  "aggregate_id": "cagg_01HY9Y4NRGTDXMEXZ16G2BQ1VD",
  "realm_id": "realm_demo_dissertation",
  "subnet_id": null,
  "space_id": "direct_alice_bob",
  "scope": "direct",
  "subject_key": "ed25519-pub:BOB_BASE64URL...",
  "subject_space_id": null,
  "category_counts": {
    "UNWANTED_MESSAGES": 3,
    "SPAM": 1
  },
  "unique_reporter_count": 4,
  "independent_invite_branch_count": 3,
  "first_seen_at": "2026-05-17T10:01:00Z",
  "last_seen_at": "2026-05-17T11:20:00Z",
  "recommended_action": "TEMPORARILY_LIMIT_INVITES_AND_GROUP_POSTING",
  "governance_escalation_eligible": false
}
```

## Open Questions

- What exact independence threshold is enough for automatic moderator warning?
- Should aggregate cards include inviter pattern summaries in MVP?
