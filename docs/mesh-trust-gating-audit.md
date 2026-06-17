# Mesh Trust Gating Audit

Цель: зафиксировать, что P2P/mesh transport не создаёт доверие сам по себе.
Единственный source of truth для `ACTIVE` relationship — offline QR handshake.

## Entry Points

| Entry point | Разрешённое поведение | Запрещённое поведение |
| --- | --- | --- |
| QR invite import | создать pending relationship | создать `ACTIVE` напрямую |
| QR response/confirmation | завершить offline handshake | принимать неподписанный placeholder как production proof |
| LAN NSD discovery | показать peer в diagnostics | создать contact/chat/trust |
| Inbound `MESSAGE` packet | принять только от ACTIVE peer | создать relationship из unknown sender |
| Outbound message | отправить только ACTIVE peer | отправить pending/unlinked/blocked |
| Realm-bound chat/mesh | принять только при действующем membership certificate у обеих сторон | обходить restrict/remove member через старый ACTIVE relationship |
| Relay simulation | переслать только в explicit prototype relay mode | включать real relay по умолчанию |
| Realm-scoped relay peer | использовать как relay-кандидата только после проверки membership/relay policy того же реалма | превращать non-relationship peer в contact или direct-message target |

## Rejection Reasons

Runtime diagnostics должны использовать безопасные причины без raw payload:

- `UNKNOWN_PEER`;
- `PENDING_RELATIONSHIP`;
- `WRONG_RECIPIENT`;
- `BLOCKED_OR_UNLINKED`;
- `EXPIRED`;
- `DUPLICATE`;
- `MALFORMED`;
- `TTL_EXHAUSTED`;
- `REALM_MEMBERSHIP_BLOCKED`;
- `UNKNOWN_CRYPTO_PROFILE`;
- `CRYPTO_PROFILE_REJECTED`;
- `CRYPTO_PROFILE_MISMATCH`.

## Required Guards

1. Discovered LAN peer does not become contact.
2. Unknown sender packet cannot create relationship.
3. Unknown sender packet cannot create chat.
4. Pending relationship cannot receive accepted message.
5. Wrong recipient packet is rejected.
6. Blocked/unlinked peer packet is rejected.
7. Outbound message requires `ACTIVE`.
8. Invite QR import still never creates `ACTIVE`.
9. Realm-bound relationship also requires local and peer membership certificates with `send_direct`.
10. Removed/restricted/expired realm members are blocked in chat and mesh gates.
11. Packets без metadata допуска криптографического профиля отклоняются.
12. Packets, чей криптографический профиль не совпадает с relationship/session
    binding, отклоняются.
13. Экспериментальные криптографические профили, отклонённые контуром
    диагностики кручения рациональных кривых, не попадают в outbox/inbox
    delivery.
14. Non-relationship peer может быть только realm-scoped relay-кандидатом после
    проверки membership/relay policy; он не становится contact и не подменяет
    direct-message target.

## Current Implementation Hooks

- `MeshTrustGate.validateOutbound()`;
- `MeshTrustGate.validateInbound()`;
- `PacketValidator.validateForStorage()`;
- `MeshOutboxProcessor`;
- `MeshInboxProcessor`;
- `ProductCryptoAdmissionGate`;
- `RelationshipService.canSendMessage()`.
- `RealmCommunicationPolicy.canUseRelationship()`.

## UI/Diagnostics Boundary

Diagnostics may show counts:

- duplicates dropped;
- expired dropped;
- unknown peer rejected;
- last packet status.

Diagnostics must not show:

- raw packet body;
- private keys;
- secret material;
- proof placeholders as signatures;
- public discovery language.

Terminology for transport diagnostics:

- `relationship peer` - адресат личного сообщения;
- `realm relay peer` - не контакт, но возможный ретранслятор закрытого пакета
  при совпадении реалма и relay policy;
- `unknown/noise peer` - устройство без валидного Kraken/realm маяка.

## Manual Audit Checklist

Before two-device P2P demo:

1. Scan invite QR and verify state is pending, not `ACTIVE`.
2. Complete response/final QR and verify `ACTIVE`.
3. Turn on mesh diagnostics.
4. Confirm discovered LAN peers are diagnostics-only until QR trust exists.
5. Send message only after `ACTIVE`.
6. Replay same packet and verify duplicate is not duplicated as message.
7. Block/unlink peer and verify inbound message is rejected.
8. Restrict/remove a realm member and verify chat send + inbound mesh packet are rejected.
