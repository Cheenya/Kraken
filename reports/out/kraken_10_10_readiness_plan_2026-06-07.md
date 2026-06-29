# Kraken 10/10 Readiness Plan

Date: 2026-06-07.

Scope: Android research prototype on
`codex/android-research-panel-report-viewer`, plus linked math/evidence branch
for the Adamova diagnostic and admission-gate work.

## Definition Of 10/10

`10/10` here means a dissertation-grade research prototype and evidence packet,
not a production-secure messenger. Production security remains a separate track.

Research-prototype `10/10` means:

- the app demonstrates QR-established trust, local identity, chats and nearby
  delivery over implemented serverless transports;
- LAN/Wi-Fi and BLE routes are evidenced with fresh two-phone captures;
- message delivery has repeatable route-specific counters, statuses and
  screenshots;
- Adamova C++ admission gate has clear product-path integration, tests and
  dissertation metrics;
- claim boundaries are explicit in UI/docs;
- the supervisor/commission packet can be opened without reading the whole repo;
- known gaps are framed as future production/security work, not hidden failures.
- fresh two-phone captures are taken only when `adb devices -l` shows the two
  phones as ADB-visible devices; physical connection alone is not enough for the
  capture helper.
- as of the 2026-06-07 phone pass, both phones are ADB-visible and an
  open-chat Bluetooth-direct UI bundle exists, but it is not an orchestrated
  fresh-send/counter run.

## Current Scores

| Area | Current score | 10/10 gate |
| --- | ---: | --- |
| Math/Sage evidence | 9/10 | One final dissertation table tying Sage, random risk, Adamova effectiveness and Android evidence together. |
| Adamova C++ admission gate | 8/10 | Route/profile UI evidence plus one end-to-end demo where rejected experimental profile blocks message capability. |
| Android messenger UX | 7/10 | Final Russian UI pass, no debug/demo labels in primary flows, screenshot pack after cleanup. |
| QR trust and relationship state | 8/10 | Simplified nearby/BLE-assisted handshake design decision and updated threat model. |
| LAN/Wi-Fi delivery | 8/10 | Repeatable send/receipt harness with in-app counters and latency/loss output. |
| BLE direct route | 7/10 | Fresh route-specific send/receipt smoke with exported `ble-gatt` counters and rejection cases. |
| Routed mesh / relay | 5/10 | Simulated relay is covered; real relay mode needs explicit prototype gate, path evidence and traffic budgets. |
| Security boundary | 7/10 | Keystore/real crypto roadmap is documented; release/prototype build separation and signed envelope remain open. |
| Dissertation packet | 8/10 | One consolidated "what to insert into thesis" packet with current screenshots and final numbers. |
| Branch/repo hygiene | 6/10 | Dirty tree must be sliced into reviewable commits; raw artifacts must stay ignored or curated. |

Overall research-prototype readiness: **7.5/10**.

The current project is strong enough for a serious dissertation prototype demo,
but not yet clean enough to call the evidence packet `10/10`.

## What Is Already Proven

### Mathematical / Adamova Evidence

- Sage pack: 15 curves, 11 direct matches, 4 unsupported local, 0 mismatches.
- Large-coefficient corpus: 20 curves, 20 matches, 0 mismatches.
- Random risk simulation: 90 curves; `kraken_precheck` false accepts `0/50`,
  `needs_reference_validation` `10`.
- Native C++ path is materially faster than the Kotlin diagnostic path in the
  measured corpus.
- Adamova admission policy exists for experimental crypto profiles and rejects
  weak/rejected/mismatched profiles in tests.

Primary artifacts:

- `reports/out/adamova_effectiveness_experiment.md`
- `reports/out/adamova_effectiveness_dissertation_table.md`
- `reports/out/adamova_effectiveness_completion_audit.md`
- `docs/adamova-admission-gate-architecture.md`

### Android / Transport Evidence

- QR-established relationship and local chat flow exist.
- LAN/Wi-Fi two-phone prototype delivery evidence exists.
- BLE direct-route two-phone evidence exists:
  `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`.
- Samsung live capture shows chat with Xiaomi, route label
  `Bluetooth напрямую` and delivered messages.
- Android Bluetooth dumps show Kraken GATT service UUID
  `58a1257c-f4a8-48c8-99d5-917b9863d7c4`.
- Trust gate, duplicate/expired/wrong-recipient/unknown-peer checks are covered
  by tests.

Primary artifacts:

- `reports/out/two_device_delivery_evidence.md`
- `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`
- `reports/out/mesh_delivery_simulation.md`
- `docs/kraken-attack-scenarios-evidence.md`

## P0 To Reach 10/10 Research Prototype

1. **Commit slicing and tree cleanup.**
   Split the current dirty tree into reviewable commits:
   Adamova effectiveness, raw evidence manifest, two-phone capture helper,
   BLE evidence docs, QR/invite metadata code changes and policy guards.

2. **Route-specific evidence export.**
   Add an in-app/exported evidence snapshot that records:
   `recentRouteAttempts`, selected route, `packetsSent`, `packetsReceived`,
   `receiptsReceived`, `duplicatesDropped`, `unknownPeerRejected`,
   `lastDeliveryLatencyMs`.

3. **Fresh route-specific two-phone smoke.**
   Precondition: `adb devices -l` must show both phones. This precondition is
   currently met for Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0`; the June 7
   capture shows an open `Bluetooth напрямую` chat with delivered/error UI
   states, but still lacks orchestrated fresh sends and exported counters.

   Run and capture:
   - LAN send/receipt;
   - BLE send/receipt;
   - wrong recipient rejection;
   - unknown peer rejection;
   - duplicate packet drop.

4. **Message ordering and status audit.**
   Verify delayed messages sort by sender creation time, then `messageId`.
   Confirm no old `SENT_TO_TRANSPORT` or `READY_FOR_TRANSPORT` state remains
   stuck after queue processing.

5. **Final Russian UI pass.**
   Remove English/debug labels from primary screens, keep UI Lab isolated, and
   make route labels precise:
   `Bluetooth напрямую`, `Wi-Fi/LAN напрямую`, `через relay-прототип` only when
   path evidence exists.

6. **Adamova end-to-end demo path.**
   Show one product-visible scenario where an experimental profile accepted by
   baseline is rejected by Adamova admission policy and therefore cannot become
   message-capable.

7. **Dissertation insertion packet.**
   Produce one final report with:
   - exact numbers;
   - screenshots;
   - allowed wording;
   - limitation wording;
   - references to source artifacts.

## P1 After 10/10 Research Prototype

These improve credibility but are not required for a dissertation-grade research
prototype:

- automated two-phone orchestration;
- latency/loss repeated trials;
- route reliability comparison LAN vs BLE;
- BLE malicious advertiser fixture;
- Wi-Fi Direct prototype;
- real relay/store-carry-forward runtime behind explicit prototype mode;
- attachment/voice-note route policy.

## Production Track Is Separate

Production readiness remains far below 10/10. Required production gates:

1. Android Keystore identity provider.
2. Non-exportable private key handling and migration from placeholder identity.
3. Signed QR invite/response/confirmation payloads.
4. Signed packet envelope.
5. Encrypted message payloads using reviewed primitives/libraries.
6. Replay protection and key rotation/revocation model.
7. Release/prototype build separation.
8. Security review.

Allowed wording:

> Kraken is a dissertation-grade research prototype with QR-established trust,
> local chats, LAN/BLE nearby transport evidence and Adamova experimental-profile
> admission checks.

Forbidden wording:

- `Kraken is a production secure messenger`.
- `Adamova proves production message security`.
- `BLE/LAN route evidence proves production reliability`.
- `prototype-placeholder is a real signature or encryption layer`.

## Next Batch

Recommended immediate batch:

1. Freeze this `10/10` plan as source-of-truth.
2. Use the currently ADB-visible phones for route-specific LAN/BLE smoke before
   making any fresh delivery claim.
3. Slice and commit current evidence/docs changes.
4. Add route-specific evidence export.
5. Run fresh two-phone LAN and BLE smoke with counters.
6. Refresh dissertation insertion packet.
