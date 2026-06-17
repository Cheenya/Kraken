# Packet

## Purpose

Represents an encrypted transit packet for opportunistic store-carry-forward
delivery.

## Current Prototype Status

This schema documents the target transport contract. The current Android
research prototype can send local LAN NSD/TCP packets with prototype JSON payload
fields and `prototype-placeholder` proof mode. That prototype path is useful for
delivery testing, but it is not the final encrypted packet format and must not be
presented as production-secure messaging.

## Scope

Packets are relayed by nodes that cannot decrypt the payload. No static route is
assumed. Routing is bounded gossip / Spray-and-Wait inspired.

## Fields

| Field | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | yes | Must be `packet`. |
| `version` | integer | yes | Spec version, currently `1`. |
| `packet_id` | string | yes | Unique packet id. |
| `message_id` | string | yes | Message or control-message id. |
| `realm_id` | string | yes | Realm scope. |
| `subnet_id` | string or null | yes | Optional subnet scope. |
| `space_id` | string or null | yes | Optional direct/group/channel space. |
| `scope` | string | yes | `direct`, `small_group` or `channel`. |
| `sender_hint` | string | yes | Sender routing hint. |
| `recipient_hint` | string | yes | Recipient routing hint. |
| `ttl_hops` | integer | yes | Remaining hop count. |
| `expires_at` | string | yes | ISO-8601 UTC expiration. |
| `copy_budget` | integer | yes | Remaining copy budget. |
| `min_reserve` | integer | yes | Copies reserved from low-value relays. |
| `social_reserve` | integer | yes | Copies reserved for trusted/high-score relays. |
| `priority` | string | yes | `low`, `normal` or `high`. |
| `encrypted_payload` | string | yes | Opaque encrypted payload. |

## State Transitions

Transit buffer lifecycle:

```text
CREATED -> STORED -> FORWARDED -> DELIVERED
CREATED -> STORED -> EXPIRED
CREATED -> STORED -> TOMBSTONED
CREATED -> STORED -> DROPPED_BY_POLICY
```

Forwarding decrements `ttl_hops` and consumes/splits `copy_budget` according to
policy.

## Validation Rules

- `ttl_hops` must be greater than zero when forwarded.
- `expires_at` must be enforced even if no tombstone is received.
- `copy_budget` must be greater than zero when creating or forwarding.
- Relay nodes must ignore duplicate `packet_id` values already seen/stored.
- Nodes should relay only traffic matching membership, subscription and relay
  policy.
- `scope` must be one of `direct`, `small_group`, `channel`.
- Large group chats are not MVP; `small_group` must be limited by policy.
- The final recipient is the only intended plaintext reader.

## Privacy And Security Notes

`sender_hint` and `recipient_hint` may leak metadata. They are MVP routing hints
subject to future improvement through blinded tags or other privacy-preserving
mechanisms.

Relay nodes store encrypted payloads temporarily. Root governance and relays
cannot decrypt message contents.

In the current Android prototype, relay/privacy guarantees are not complete
because payload encryption and signed envelopes are still Phase Crypto work.

## JSON Example

```json
{
  "type": "packet",
  "version": 1,
  "packet_id": "pkt_01HY9XR4CN9C7FHY8SZT8RK7GC",
  "message_id": "msg_01HY9XR05T6HZV8QDBR41R9S2R",
  "realm_id": "realm_demo_dissertation",
  "subnet_id": null,
  "space_id": "direct_alice_bob",
  "scope": "direct",
  "sender_hint": "hint:sender:5a4f",
  "recipient_hint": "hint:recipient:92bc",
  "ttl_hops": 8,
  "expires_at": "2026-05-17T12:00:00Z",
  "copy_budget": 8,
  "min_reserve": 1,
  "social_reserve": 1,
  "priority": "normal",
  "encrypted_payload": "aead:BASE64URL..."
}
```

## Open Questions

- What exact hint format gives enough MVP routing utility with acceptable
  metadata leakage?
- Should `realm_id` be optional for pure direct contacts outside a realm, or
  should MVP direct messaging always be scoped to a realm/demo context?
