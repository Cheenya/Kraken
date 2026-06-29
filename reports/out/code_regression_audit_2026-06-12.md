# Kraken Code Regression Audit, 2026-06-12

Audit timezone: Europe/Moscow.

Repository: `/Users/cheenya/Projects/kraken-android-research-panel`

Branch: `codex/android-research-panel-report-viewer`

Observed HEAD: `1587cd3` (`Document production secure messenger scope`)

Working tree state at audit time: dirty. Existing uncommitted files:

- `app-android/app/src/debug/java/com/disser/kraken/debug/DebugEvidenceReceiver.kt`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt`
- `app-android/app/src/test/java/com/disser/kraken/mesh/MeshServiceTest.kt`
- `scripts/capture_debug_route_evidence.sh`
- `handoff.md` (untracked)

## Executive Verdict

The suspected regression is not a broad build or unit-test break. Local checks
pass. The current risk is semantic: recent debug/evidence changes moved
Wi-Fi Direct direct-send behavior from "send only to an observed transport
peer" toward "construct a relationship peer and let the transport try fallback
resolution." That change is useful for exercising Wi-Fi Direct fallback logic,
but it also makes debug evidence easier to overread unless target-side counters
and bidirectional delivery remain mandatory gates.

The most likely active blocker is reverse Wi-Fi Direct endpoint resolution in
`WifiDirectTransport`: Samsung can be group owner and see a P2P client through
ARP, but the sender path can still fail to create a usable endpoint for the
relationship peer. This matches the latest untracked handoff diagnosis and the
latest blocker audit: Wi-Fi Direct is still not closed for bidirectional
delivery, negative tests, or reliability benchmark.

## Findings

### P1. Reverse Wi-Fi Direct fallback is incomplete for known peer without host

Code path:

- `MeshService.sendDebugDirectMessage()` now creates a synthetic
  `DiscoveredPeer` when `transport.observePeers()` does not expose the active
  relationship peer.
- `CompositePeerTransport.send()` then falls back from "transports observing
  this peer" to all transports when no candidate observes it.
- `WifiDirectTransport.send()` can call `fallbackPeerForRelationshipPeer()`
  only when no internal `WifiDirectPeer` is found.
- If an internal Wi-Fi Direct peer exists but has `host == null`, the code goes
  through `connectionHostFor(peer)`. For a group owner this tries
  `peer.host ?: arpHostFor(peer.deviceAddress)`, but does not explicitly use
  the single-client ARP fallback for that known-peer-without-host case.

Observed symptom from `handoff.md`: reverse Samsung -> Xiaomi attempts reached
Wi-Fi Direct but alternated between `wifi-direct-connecting` and
`wifi-direct-peer-not-found`, while Android P2P group state and Xiaomi listener
evidence existed.

Impact: debug-send can reach the transport but still fail endpoint resolution;
the route remains open even though the surrounding evidence tooling looks much
stronger than before.

### P1. Debug evidence can now be overread as delivery evidence

The debug receiver now records attempts, retry delay and per-attempt direct-send
results. This is good observability, but `debug_send_success=true` is not a
completion gate by itself. The current readiness gate still requires:

- selected route `wifi-direct`;
- ACK-backed sender result;
- target-side `accepted_connections` and `inbound_packets`;
- reverse direction proof;
- repeated route benchmark samples;
- negative route-bound rejection evidence.

Impact: a sender-side success can look like a completed transport proof unless
the report and scripts continue to require target-side counters.

### P2. UI status wording can feel like a delivery regression

`visibleMeshDeliveryLabel()` maps `SENT_TO_TRANSPORT` to `отправлено`, while
real peer delivery is represented separately by `DELIVERED_TO_PEER` and shown
as `доставлено`. This is technically defensible, but easy to misread during
manual tests: "отправлено" means the local sender handed the packet to a
transport, not that the peer received it.

Impact: when target counters do not move, a user can reasonably perceive the UI
as claiming success too early.

### P2. The new fallback test is too narrow

`MeshServiceTest.debugDirectSendAllowsTransportFallbackWhenPeerNotObservedYet`
locks the new synthetic-peer behavior with a fake transport. It does not test
the active Wi-Fi Direct blocker:

- peer exists but has no host;
- sender is group owner;
- exactly one P2P client is visible in ARP;
- fallback should resolve the client IP and use the fixed Wi-Fi Direct port.

Impact: the test protects the debug-send entry point but not the real endpoint
resolution regression.

### P3. Coverage gaps remain around real Android transport lifecycle

Unit tests cover route models, route labels, message ordering, receipt/status
transitions, evidence export and several Wi-Fi Direct helpers. They do not
cover Android Wi-Fi Direct callbacks, runtime permissions, socket lifecycle,
foreground-service timing, or Compose rendering of the route/status state.

Impact: local green tests do not prove phone-level route readiness.

## Refactoring And Fix Plan

### Phase 1: Preserve evidence semantics before changing transport behavior

1. Keep `debug_send_success` as sender-side only in reports and manifests.
2. Add or keep explicit report fields for target-side proof:
   `accepted_connections`, `inbound_packets`, selected route, and route attempt
   success/failure.
3. Do not let any completion/report verifier treat sender-side success alone as
   route delivery.

### Phase 2: Fix Wi-Fi Direct endpoint resolution narrowly

1. In `WifiDirectTransport`, introduce a small helper such as
   `resolveHostForPeer(peer, info)` so host resolution is not split between
   `connectionHostFor()` and `fallbackPeerForRelationshipPeer()`.
2. For group owner mode:
   - first use `peer.host`;
   - then use `arpHostFor(peer.deviceAddress)`;
   - if the peer still has no host and exactly one P2P client exists, use the
     single-client ARP fallback;
   - keep the existing multi-client no-guess guard.
3. For client mode:
   - keep using `info.groupOwnerAddress`.
4. Preserve diagnostics: when DNS-SD/TXT is missing and ARP fallback is used,
   set a visible diagnostic such as `wifi-direct-dns-sd-fallback`.

### Phase 3: Add tests for the actual regression

1. Add a focused unit test around host-resolution logic, preferably after
   extracting it to a pure helper.
2. Test the group-owner known-peer-without-host case with one P2P ARP client.
3. Test the multi-client case remains unresolved.
4. Keep the existing `MeshService` synthetic-peer test, but treat it as entry
   point coverage, not Wi-Fi Direct proof.

### Phase 4: Tighten UI wording if manual testing keeps confusing states

Option A: keep current labels and document them in the manual smoke checklist.

Option B: rename `SENT_TO_TRANSPORT` display from `отправлено` to a stricter
label such as `передано в транспорт`. This is more precise, but may be too
technical for the chat UI.

Do not rename status enums unless broader state-machine refactoring is planned.

### Phase 5: Verification gates before commit

Local gates:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
git diff --check
bash -n scripts/capture_debug_route_evidence.sh
pytest tests/test_android_policy_guards.py -q -p no:cacheprovider
cd app-android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Phone gates, if devices are available:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
scripts/capture_debug_route_evidence.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --label reverse-wifi-direct-fallback-after-fix-2026-06-12 \
  --transport-profile wifi-direct-only \
  --start-foreground-wifi-direct \
  --force-stop-mesh-before-start \
  --debug-send-body reverse-wifi-direct-fallback-after-fix \
  --debug-send-attempts 3 \
  --debug-send-retry-delay-ms 1000 \
  --sync-after-debug-send \
  --sync-attempts 1
```

Required evidence before closing the Wi-Fi Direct gate:

- Samsung -> Xiaomi and Xiaomi -> Samsung both show `selected_route=wifi-direct`.
- Sender-side debug send succeeds with ACK-backed semantics.
- Target-side post-send capture shows incremented `accepted_connections` and
  `inbound_packets`.
- Repeated route benchmark reaches the configured minimum sample gate.
- Negative route-bound cases are captured separately.

## Checks Performed During This Audit

Passed:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel
git diff --check
bash -n scripts/capture_debug_route_evidence.sh
pytest tests/test_android_policy_guards.py -q -p no:cacheprovider
cd app-android
./gradlew :app:testDebugUnitTest --tests 'com.disser.kraken.mesh.MeshServiceTest' --tests 'com.disser.kraken.mesh.MeshEvidenceExportTest' --tests 'com.disser.kraken.mesh.WifiDirectPeerBindingTest' --tests 'com.disser.kraken.mesh.WifiDirectArpTableTest' --tests 'com.disser.kraken.mesh.LanFrameCodecTest'
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Note on Python: `python3 -m pytest` failed because the active Homebrew
`python3.14` does not expose a `pytest` module, but `/opt/homebrew/bin/pytest`
is installed and `pytest ...` passed.

Not performed:

- no code fix was applied in this audit pass;
- no ADB two-phone capture was run;
- no commit or push was made.
