# Kraken Engineering Backlog

Statuses: `todo`, `in-progress`, `blocked`, `done`.

Priorities:

- `P0`: blocks credible demo/build correctness.
- `P1`: needed for next usable prototype milestone.
- `P2`: important hardening or UX quality.
- `P3`: later polish or research expansion.

## Immediate Build/UI Fixes

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Keep Android build green | P0 | in-progress | Gradle wrapper, SDK 35, NDK/CMake | `./gradlew test` and `./gradlew assembleDebug` pass on a clean checkout. |
| Reduce remaining AGP warnings | P2 | todo | AGP/Kotlin compatibility decision | Deprecated compatibility flags are either removed safely or documented with reason. |
| Screenshot review pass | P1 | in-progress | Demo data helper | Home, Contacts, Realms, Chat, Mesh Status and Research are readable on Pixel-class portrait screens. |

## Android Identity Hardening

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Android Keystore migration | P0 | todo | Safe crypto abstraction | Private key material is non-exportable; existing placeholder storage remains explicitly marked as non-production. |
| Identity backup policy | P1 | todo | Threat model | App states no account recovery and no transparent key rotation; user flow explains new key means new user. |
| Fingerprint verification UX | P1 | todo | Identity details screen | Fingerprint display is copyable and explainable without trusting display name. |

## Real QR Rendering

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| QR generation | P1 | done | QR implementation plan | My QR renders payload QR locally without network dependency. |
| QR scanning | P1 | done | Camera permission review | Import Invite can scan a QR and still lands in pending state only. |
| QR-only product UX | P1 | in progress | QR generation/scanning | Primary invite UX uses QR only; text payload exchange is not shown as normal user flow. |
| Payload canonicalization | P2 | todo | Invite signature design | Signed payload has stable canonical form and size budget. |

## Android Keystore Migration

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Key provider implementation | P0 | todo | Crypto interface review | Production provider does not use device identifiers and uses platform secure APIs. |
| Migration guard | P1 | todo | Storage schema version | Old placeholder identities are handled explicitly, not silently upgraded as trusted production identities. |

## Relationship UX

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Handshake detail screen | P1 | todo | QR import | Pending handshake shows peer fingerprint and explicit accept/reject actions. |
| Unlink notice import UI | P2 | todo | Control message model | Peer unlink moves relationship to blocked/rejoin-required without enabling messages. |
| Complaint copy review | P2 | todo | Moderation MVP | Negative unlink copy is neutral, precise and not punitive by default. |

## Realm Governance

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Approval workflow UX | P1 | in-progress | Pending approval model | Admin/moderator can approve/reject local requests; certificate issued only after approval. |
| Capacity token handling | P2 | todo | Safe crypto signatures | Capacity increase requires signed token placeholder with root/maintainer limits. |
| Governance escalation scope | P2 | todo | Threat model | Root governance cannot decrypt messages and does not handle ordinary disputes by default. |

## Delivery Simulator

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Scenario metrics | P1 | todo | Simulator experiment plan | Simulator reports delivery rate, latency, copy overhead and storage pressure. |
| Encounter model variants | P2 | todo | Experiment plan | Low/medium/high encounter probability can be compared reproducibly. |
| Tombstone cleanup metric | P2 | todo | Tombstone model | Honest relay cleanup rate is measured separately from malicious ignored tombstones. |

## Transit Buffer

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Android buffer UI | P1 | todo | Packet model | Mesh Status shows local buffer count, expired packets and copy budget summary. |
| Forwarding policy tuning | P2 | todo | Battery policy, courier score | Reserve copy rule and low-score relay behavior are tested. |
| Metadata hint hardening | P2 | todo | Threat model | Recipient/sender hint leakage is documented with future mitigation path. |

## Safe Crypto Implementation

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Select reviewed libraries | P0 | todo | Threat model, Android Keystore plan | Candidate primitives and libraries are documented before implementation. |
| Real AEAD provider | P0 | blocked | Key agreement and KDF provider | No custom primitives; tests cover tamper and wrong-recipient failure. |
| Signature provider | P1 | todo | Invite/unlink signing design | Invite and unlink notices can be signed and verified through abstraction. |

## LAN Transport

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Manual LAN debug session | P1 | todo | Transport abstraction | Manual IP/port local session exchanges summaries without external server. |
| Internet permission review | P0 | todo | LAN debug design | Any `INTERNET` permission is documented as LAN/debug-only with guard tests updated. |
| No public discovery guarantee | P0 | in-progress | Policy guards | No nearby/global/public peer or realm search appears in UI or models. |

## Route-Aware UX

Reference: [`route-aware-capability-model.md`](route-aware-capability-model.md).
Implementation plan: [`route-aware-implementation-plan.md`](route-aware-implementation-plan.md).
Comparative audit: [`mesh-messenger-best-practices-audit.md`](mesh-messenger-best-practices-audit.md).
Russian audit: [`mesh-messenger-best-practices-audit.ru.md`](mesh-messenger-best-practices-audit.ru.md).

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Per-contact route state | P1 | todo | BLE/LAN transport diagnostics | Chat header distinguishes no route, Bluetooth direct, Wi-Fi/LAN direct and routed mesh. |
| Capability-based composer | P1 | todo | Per-contact route state | Rich actions are enabled only when the current route supports them. |
| Mesh presence budget | P1 | todo | Route TTL/cache model | App does not ping all contacts through multi-hop mesh for cosmetic availability. |
| Rich media route policy | P2 | todo | Attachment framework, Wi-Fi Direct/LAN route | Voice notes, stickers, GIFs, photo/video and calls have route-specific limits. |

## Battery Policy

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Android battery state adapter | P1 | todo | Relay policy model | Forwarding evaluator receives battery/charging/Wi-Fi context without starting background scans. |
| Relay mode persistence | P1 | done | Settings UI | Selected relay mode persists locally. |
| Low-power warnings | P2 | todo | Mesh Status | UI explains why forwarding is disabled. |

## Courier Score

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Daily aggregation UI | P2 | todo | Courier model | Mesh Status shows delayed daily summary, no exact routes or timestamps. |
| Separate relay reliability | P1 | done | Courier model | Courier score and relay reliability are distinct model concepts. |
| Privacy guard tests | P1 | in-progress | Policy guards | No GPS, route, recipient or leaderboard fields. |

## Moderation

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Moderator card UI | P1 | todo | Complaint aggregate | Moderators see grouped aggregates, not endless raw complaint streams. |
| Restriction state UI | P2 | todo | Moderation action model | Restricted user state is visible without implying global relay ban. |
| Governance abuse escalation | P2 | todo | Threat model | Only governance-level abuse escalates beyond local moderation by default. |

## Channels

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Channel detail screen | P1 | todo | Realm model | Channel belongs to realm, has role badge, mute/leave actions and no public search. |
| Latest-N storage demo | P2 | todo | Channel policy | Stored placeholder messages respect latest-N policy. |
| Publisher permissions | P1 | done | Channel model | Only publisher role can publish placeholder messages. |

## Small Groups

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Small group UI section | P2 | todo | Realm model | UI says small groups only and enforces max-member model. |
| Invite approval integration | P1 | todo | Pending approval workflow | New group invitees require approval before membership. |
| Backlog/TTL enforcement | P2 | todo | Message model | Group placeholder messages respect policy limits. |

## Research Panel

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Connect existing research code path | P2 | todo | C++/JNI or Python bridge decision | Android panel uses deterministic implementation or clearly remains placeholder. |
| Export research report | P3 | todo | Result schema | JSON/CSV/Markdown export is local-only and marked diagnostic. |
| Wording guard | P0 | in-progress | Policy tests | UI never claims production encryption benefit. |

## C++ Core

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Native boundary tests | P1 | todo | JNI bridge | Android test confirms native status function loads on supported ABIs. |
| Packet serialization candidate | P2 | todo | Packet model | C++ API can serialize deterministic packet model without Android lifecycle logic. |
| 16 KB compatibility watch | P1 | in-progress | NDK linker flags | Debug APK remains compatible with 16 KB page-size devices. |

## Dissertation Reporting

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Simulator result tables | P2 | todo | Experiment plan | Runs produce tables suitable for dissertation appendix. |
| Demo screenshots | P1 | todo | UI screenshot readiness | Key screens captured and mapped to demo checklist. |
| Claim review | P0 | todo | Threat model | Dissertation text avoids production security and unproven algorithm-performance claims. |

## Security Review

| Item | Priority | Status | Dependencies | Acceptance Criteria |
| --- | --- | --- | --- | --- |
| Threat model review | P0 | in-progress | Threat model draft | Assets, actors, boundaries, metadata leakage and local storage risks are explicit. |
| Crypto design review | P0 | blocked | Safe crypto implementation plan | Reviewed primitives/libraries selected before production crypto code. |
| Android permissions review | P0 | in-progress | Policy guard tests | Manifest requests no forbidden permissions; any future LAN permission is justified. |
