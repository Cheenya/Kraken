# Kraken Transport Architecture Suspicion Plan

Дата: 2026-06-12.
Scope: аудит транспорта Kraken без телефонов, с опорой на текущий Android repo,
сохранённые artifacts, официальные Android docs и исходники Briar/Berty/Meshtastic.

## Short Answer

Главная гипотеза регресса не в одном локальном баге Wi-Fi Direct, а в модели:
Kraken сейчас местами обращается с Android transports как с синхронным списком
`peer -> send`, хотя Wi-Fi Direct/BLE/LAN на Android являются асинхронными,
состояниями-зависимыми и оболочко-зависимыми runtime-сессиями.

Текущее ощущение "концептуально не там" похоже на три смешанных слоя:

1. transport discovery / endpoint resolution;
2. Android lifecycle / foreground-background policy;
3. доказательство доставки и Adamova admission, которое должно жить выше
   transport и не подменяться transport ACK.

Важное ограничение: на этом этапе телефонов нет. `adb devices` сейчас пустой,
поэтому Xiaomi crash/kill нельзя подтвердить live. В сохранённых artifacts
доказан не `FATAL EXCEPTION`, а отказ background foreground-service start:
`startForegroundService() not allowed due to mAllowStartForeground false`.

## Sources Reviewed

### Current Kraken Evidence

- `handoff.md`: reverse Wi-Fi Direct Samsung `R5CY22X6MSB` -> Xiaomi
  `d948ffd0` не закрыт.
- `artifacts/debug-route-evidence/20260611-143353-wifi-direct-receiver-foreground-2026-06-11/d948ffd0/debug_evidence_command_result.json`.
- `artifacts/debug-route-evidence/20260611-153635-wifi-direct-target-xiaomi-foreground-service-start-2026-06-11/d948ffd0/debug_evidence_command_result.json`.
- `reports/out/kraken_10_10_current_blocker_audit_2026-06-11.md`.
- `reports/out/production_secure_messenger_scope_2026-06-12.md`.
- `docs/kraken-attack-scenarios-evidence.md`.
- Current code in:
  - `WifiDirectTransport.kt`
  - `MeshForegroundService.kt`
  - `DebugEvidenceReceiver.kt`
  - `MeshService.kt`
  - `MeshDeliveryPipeline.kt`
  - `CompositePeerTransport.kt`
  - `TransportCapability.kt`

### Android Primary Sources

- Android Wi-Fi Direct service discovery:
  https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct
- Android `WifiP2pManager` API reference:
  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- Android foreground service background start restrictions:
  https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Android Doze and App Standby:
  https://developer.android.com/training/monitoring-device-state/doze-standby

### External Messenger / Mesh Sources

- Briar project: https://briarproject.org/
- Briar source: `/tmp/kraken-audit-briar`
- Berty protocol docs: https://berty.tech/ar/docs/protocol
- Berty source: `/tmp/kraken-audit-berty`
- Meshtastic Android source: https://github.com/meshtastic/Meshtastic-Android
- Meshtastic Android install docs:
  https://meshtastic.org/docs/software/android/installation/

## What Other Systems Suggest

### Briar

Briar treats transports as plugins with explicit states. `Plugin.State` has
`STARTING_STOPPING`, `DISABLED`, `ENABLING`, `ACTIVE`, `INACTIVE`; transport
connections are produced by plugin APIs and then handed to higher layers. This
is a useful reference because transport is not the place where identity, sync
and crypto policy are collapsed.

Practical lesson for Kraken: BLE/LAN/Wi-Fi Direct should expose route evidence,
session state and packets. Trust, Adamova admission, sync, message ordering and
delivery claims should remain above the transport adapters.

### Berty

Berty separates network modes behind feature flags/drivers: BLE, Android Nearby,
mDNS, relays, DHT and local discovery are configured as protocol/network
capabilities. Its own docs describe the higher-level SDK as responsible for
encryption, identities, routing, groups and application lifecycle.

Practical lesson for Kraken: Android Nearby should be considered as an optional
new `PeerTransport` experiment for Android-to-Android proximity. It must not be
mixed into QR trust or Adamova logic. It is a transport adapter, not a product
identity layer.

### Meshtastic

Meshtastic is not a phone-to-phone messenger, but it has a valuable Android
lifecycle pattern. It catches `ForegroundServiceStartNotAllowedException` when
`startForegroundService()` is blocked and schedules an expedited WorkManager
fallback. It also checks connection state before sending and keeps message send
work queued/retryable.

Practical lesson for Kraken: Android lifecycle constraints should be first-class
runtime state, not incidental exceptions from debug broadcasts.

## Current Kraken Findings

### P0: Background Foreground-Service Start Is A Real Failure

Current code path:

- `DebugEvidenceReceiver.kt` calls
  `MeshForegroundService.startDebugWifiDirectOnly(appContext)` from a debug
  broadcast thread.
- `MeshForegroundService.startDebugWifiDirectOnly()` calls
  `context.startForegroundService(intent)` on Android O+.
- Saved Xiaomi artifacts record:
  `startForegroundService() not allowed due to mAllowStartForeground false`.

This is a proved Android lifecycle policy failure. It is not yet proof of a
MIUI-specific crash. On Android 12+ foreground services cannot be started from
background except allowed cases; Xiaomi/HyperOS may make this feel harsher, but
the saved evidence itself is platform policy.

Impact:

- ADB/debug evidence can fail before transport is even started.
- Xiaomi may look like "Kraken crashes" when the actual symptom is OS-denied
  background FGS start or later process kill.
- Current code does not capture `ApplicationExitInfo`, so killed-by-system,
  ANR, user force-stop and OEM battery policy are not distinguishable from the
  saved artifacts.

### P0: Wi-Fi Direct Endpoint Is Ephemeral, But Code Treats Peer Mapping As Stable

Current `WifiDirectTransport` keeps a synchronized `peers` map until `stop()`.
`upsertPeer()` merges by fingerprint or device address and preserves an
existing `host` if the new observation has no host.

Wi-Fi Direct endpoint truth is not stable:

- discovery is asynchronous;
- group owner/client roles are asymmetric;
- group-owner address is meaningful mostly to clients;
- group-owner side often needs ARP/client-list style inference;
- OEM behavior can change timing and visible device/address data.

The current ARP fallback is useful, but it is hidden inside endpoint resolution
and can produce evidence that looks like "peer-bound send" while it is actually
"current group endpoint fallback".

Impact:

- stale host/port can survive group changes;
- relationship fingerprint can be used to synthesize a debug peer even when no
  current transport peer was observed;
- sender-side `debug_send_success=true` may prove only socket/ACK success, not
  a DNS-SD-bound peer route and not final target delivery.

### P0: Transport ACK Is Not Delivery

Current Wi-Fi Direct receiver ACKs after packet decode/enqueue. Sender success
then maps to `SENT_TO_TRANSPORT`. Inbound trust/admission processing happens
later through `MeshInboxProcessor` during sync.

Therefore:

- `debug_send_success=true` is a transport ACK;
- delivery evidence requires target-side counters and/or message/receipt state;
- Adamova admission evidence requires inbound/outbound admission decisions, not
  only a socket ACK.

Report wording should use:

- `transport_ack_success`
- `target_accepted_connections`
- `target_inbound_packets`
- `target_message_delivered`
- `receipt_applied`
- `admission_accepted/rejected`

Avoid treating a sender ACK as a complete route proof.

### P1: `PeerTransport` Contract Is Too Flat For Android Reality

Current `PeerTransport` works for simple direct routes, but the architecture now
needs a richer session model:

- permission/capability state;
- foreground/background allowance;
- advertising/registration state;
- discovery state;
- group/session state;
- endpoint resolution source and freshness;
- connection state;
- send attempt state;
- target delivery evidence.

Briar, Berty and Meshtastic all point in the same direction: transport adapters
should be controlled by lifecycle/state managers and emit state transitions,
not be treated only as `observePeers()` plus `send(peer, packet)`.

### P1: Composite Routing Can Hide Which Transport Actually Matters

`CompositePeerTransport.send()` filters transports by `observePeers()`, but if
no candidate sees the peer it falls back to trying all transports. This is
useful for debug/fallback, but risky for evidence.

Impact:

- a relationship peer can be sent through a transport that did not currently
  discover it;
- route attempts need to record fallback reason and endpoint source;
- "selected route" must be tied to message ID and attempt ID, not only latest
  snapshot state.

### P1: Xiaomi/MIUI Compatibility Must Be A Runtime Policy Layer

Current manifest has relevant permissions:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `POST_NOTIFICATIONS`
- `NEARBY_WIFI_DEVICES`
- legacy location capped to SDK 32

That is necessary but insufficient. Android shells differ in:

- background-start allowance;
- notification permission behavior;
- battery optimization;
- autostart behavior;
- Wi-Fi Direct/BLE timing;
- process-kill aggressiveness.

The app needs a shell compatibility layer that records what happened and adapts
without changing crypto or transport semantics.

### P1: Current Negative Evidence Does Not Fully Exercise Adamova Admission

`MeshDeliveryPipeline` has outbound and inbound admission checks:

- outbound trust validation before send;
- `validatePacketAdmission()` before transport send;
- inbound trust validation before payload processing;
- inbound admission mismatch checks.

No direct admission bypass was found in the current pipeline. The weakness is
evidence coverage: debug hostile probes currently focus on unknown/wrong/
duplicate style packets and often use valid/default admission metadata.

Adamova effectiveness needs explicit negative probes for:

- mismatched `cryptoProfileId`;
- mismatched `sessionProfileId`;
- wrong `admissionDecisionHash`;
- wrong `profilePolicyVersion`;
- unknown/weak profile over real transport frame;
- downgrade attempt from approved profile to default/weak profile.

## Refactoring Plan

### Phase 1: Evidence Semantics Cleanup

Do this before more phone experiments.

1. Rename/report sender-side debug success as `transport_ack_success`.
2. Add route-attempt IDs and bind them to message IDs.
3. Export route attempt records as:
   - `messageId`
   - `transportId`
   - `attemptIndex`
   - `endpointResolutionSource`
   - `endpointObservedAt`
   - `groupFormed`
   - `isGroupOwner`
   - `host`
   - `port`
   - `transportAck`
   - `targetAcceptedConnections`
   - `targetInboundPackets`
   - `receiptApplied`
   - `admissionDecision`
4. Keep `ROUTED_MESH` conservative until relay/path evidence exists.

### Phase 2: AndroidShellRuntimeController

Create a small runtime controller around service start/stop and debug starts.

Responsibilities:

1. Start foreground service only from allowed user-visible paths when possible.
2. Catch `ForegroundServiceStartNotAllowedException`.
3. Persist the failure into diagnostics/export JSON.
4. Provide a WorkManager fallback for allowed background recovery, similar in
   spirit to Meshtastic's expedited worker pattern.
5. Expose states:
   - `foreground-ready`
   - `background-start-blocked`
   - `notification-permission-missing`
   - `battery-optimization-active`
   - `autostart-unknown`
   - `process-exit-reason-unavailable`
6. Add `ApplicationExitInfo` capture on Android 11+.

Do not solve this by requesting broad battery exemption first. Android docs and
Play policy make `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` sensitive; for
the research prototype, first detect/report and give manual test instructions.

### Phase 3: Transport Session State

Introduce a transport-level state model beside existing `PeerTransport`.

Suggested model:

```kotlin
data class TransportSessionState(
    val transportId: String,
    val capabilityState: CapabilityState,
    val permissionState: PermissionState,
    val lifecycleState: LifecycleState,
    val discoveryState: DiscoveryState,
    val connectionState: ConnectionState,
    val endpointState: EndpointState,
    val lastError: String?,
)
```

Do not rewrite all transports at once. Start with Wi-Fi Direct because it is the
current blocker, then adapt BLE/LAN.

### Phase 4: Wi-Fi Direct Endpoint Resolver

Extract endpoint resolution into a pure/tested helper.

Inputs:

- current `WifiP2pInfo`;
- DNS-SD TXT peer records;
- visible P2P devices;
- ARP table;
- prior peer cache;
- relationship fingerprint;
- current time.

Outputs:

- `host`;
- `port`;
- `source`: `dns-sd`, `group-owner-address`, `arp-device-match`,
  `arp-single-client`, `manual-debug`, `none`;
- `confidence`: `bound-peer`, `group-fallback`, `debug-only`;
- `observedAt`;
- `expiresAt`;
- `staleReason`.

Rules:

1. Never reuse host/port after TTL unless refreshed.
2. Clear or downgrade endpoint cache on connection lost, adapter off, group
   changed, role changed.
3. Mark ARP single-client fallback as fallback evidence, not bound-peer proof.
4. Record endpoint source in every route attempt export.

### Phase 5: Store-And-Forward Core Above Transport

Offline messengers should not rely on live socket presence as the only delivery
model. Keep outbox/retry as the durable truth:

- transport availability only moves a queued packet into an attempt;
- failed attempt returns to queued/retry state;
- transport ACK moves to `SENT_TO_TRANSPORT`;
- target receipt/admission moves to delivered/accepted;
- no route remains an honest queue state, not a UI failure.

### Phase 6: Optional Android Nearby Adapter

Add Android Nearby only as an experiment if Wi-Fi Direct raw socket work remains
fragile.

Constraints:

- separate `PeerTransport` adapter;
- no cloud/server claim;
- explicit user approval model;
- QR trust and Adamova admission remain above it;
- compare against Wi-Fi Direct with the same route-attempt evidence schema.

## Adamova / Custom Crypto Algorithm Plan

The transport refactor must preserve the key product principle: Adamova remains
your custom algorithm/admission layer and its effectiveness must be demonstrated
as a research claim.

Boundary:

- Adamova is not a raw Wi-Fi/BLE transport feature.
- Adamova is not proven by socket delivery.
- Adamova evidence belongs in packet/session/profile metadata and admission
  decisions.
- Current production-security wording must remain conservative unless formal
  crypto review happens.

Required evidence:

1. Positive path: approved Adamova profile creates packets with expected
   profile/session/admission metadata and passes outbound/inbound admission.
2. Negative path: weak/unknown/mismatched profile is rejected before send or
   before inbox storage.
3. Downgrade path: relationship approved for one profile cannot accept packets
   claiming another profile/session hash.
4. Route independence: same admission behavior over loopback, LAN, BLE and
   Wi-Fi Direct transport frames.
5. Benchmark path: record latency/overhead for admission/evaluation separately
   from radio delivery latency.

Good dissertation claim:

> Kraken demonstrates a transport-independent Adamova admission policy for
> experimental crypto-profile metadata, with positive/negative route-independent
> evidence.

Bad claim:

> Wi-Fi Direct delivery proves Adamova production message security.

## Tests Without Phones

Run before the next physical session:

1. Unit-test `WifiDirectEndpointResolver` with:
   - client using group-owner address;
   - group owner with DNS-SD host;
   - group owner with ARP device match;
   - group owner with ARP single-client fallback;
   - stale host TTL;
   - connection lost clears endpoint.
2. Unit-test foreground-service start wrapper:
   - normal start;
   - `ForegroundServiceStartNotAllowedException` captured;
   - fallback scheduled;
   - diagnostics exported.
3. Unit-test route-attempt semantics:
   - transport ACK does not equal delivered;
   - target counters/receipt are required for delivery claim.
4. Unit-test admission probes:
   - mismatched `cryptoProfileId`;
   - mismatched `sessionProfileId`;
   - wrong decision hash;
   - wrong policy version.
5. Add report/export tests so old fields do not overclaim delivery.

## Later Tests With One Phone

Use one Xiaomi first, not two phones.

Purpose: prove lifecycle/crash behavior.

Checklist:

1. Install debug APK.
2. Launch app visibly.
3. Start mesh from UI.
4. Export diagnostics while app is foreground.
5. Background app, screen off, wait 5/15/30 minutes.
6. Capture:
   - `adb logcat -b crash`;
   - `adb logcat -b main,system`;
   - `dumpsys activity processes`;
   - `dumpsys deviceidle`;
   - `ApplicationExitInfo` export;
   - notification permission state;
   - battery optimization state.
7. Repeat with debug broadcast start and with user-visible UI start.

Pass condition:

- app records whether it was background-start-blocked, killed, ANR'd, or still
  running.

## Later Tests With Two Phones

Use Samsung + Xiaomi after the one-phone lifecycle path is instrumented.

Purpose: prove route behavior without confusing lifecycle failure with transport
failure.

Matrix:

1. Samsung -> Xiaomi Wi-Fi Direct, app foreground on both.
2. Xiaomi -> Samsung Wi-Fi Direct, app foreground on both.
3. Samsung -> Xiaomi after Xiaomi backgrounded but foreground service active.
4. Xiaomi -> Samsung after Samsung backgrounded but foreground service active.
5. Wi-Fi Direct only, no common router path.
6. LAN/BLE comparison run with the same route-attempt schema.
7. Optional Android Nearby adapter comparison if implemented.

Required evidence:

- sender `transport_ack_success`;
- target `accepted_connections`;
- target `inbound_packets`;
- message stored as incoming;
- receipt applied or explicit no-receipt reason;
- route attempt bound to message ID;
- endpoint source and freshness;
- Adamova admission result.

## Phone Audit Update: 2026-06-12

The phone phase was run after Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0` were
connected. Detailed report:
`reports/out/phone_transport_audit_2026-06-12.md`.

New facts:

1. Xiaomi API 35 repeatedly logged that `WIFI_P2P_PEERS_CHANGED_ACTION` for
   `com.disser.kraken` was skipped because the system requires
   `android.permission.ACCESS_FINE_LOCATION`.
2. Current `AndroidManifest.xml` limits `ACCESS_FINE_LOCATION` to
   `android:maxSdkVersion="32"`, and `WifiDirectPermissions` requests only
   `NEARBY_WIFI_DEVICES` on Android 13+.
3. Both phones have `NEARBY_WIFI_DEVICES` granted, but `FINE_LOCATION` appops is
   `ignore` because the permission is not actually requested.
4. Forced Wi-Fi Direct-only discovery works: both phones reached
   `selected_route=wifi-direct`, `transport_discovered_peer_count=1`, and
   `p2p_txt_bound_peer_count=1`.
5. Wi-Fi Direct delivery is still not proven. Directed Samsung -> Xiaomi fails
   on Samsung as group owner: after group formation the sender has
   `transport_discovered_peer_count=0`, all attempts end as `UNKNOWN_PEER`, and
   Xiaomi target counters stay `accepted_connections=0`, `inbound_packets=0`.
6. Directed Xiaomi -> Samsung produced sender-side success, but Samsung target
   counters did not increase, so this is not fresh target-delivery proof.
7. No fresh `FATAL EXCEPTION` for `com.disser.kraken` was found during this
   audit. The older `mAllowStartForeground=false` artifact remains valid, but
   it did not reproduce in the current already-running/allowed session.

Updated interpretation:

- the strongest concrete issue is now a Wi-Fi Direct permission mismatch on
  modern Android/OEM behavior, not only generic MIUI unpredictability;
- stale endpoint/session-state remains likely, but should be re-tested after
  fixing the permission model;
- multi-device debug capture must not be accepted as route proof unless it is
  replaced with a directed `target-before -> sender -> target-after` harness.

## Priority Order

1. Fix the Wi-Fi Direct permission model: requestable `ACCESS_FINE_LOCATION` on
   Android 13+ when Wi-Fi Direct is enabled, with clear UI wording that this is
   for peer discovery rather than background tracking.
2. Add permission diagnostics to route export: `nearbyWifiDevicesGranted`,
   `fineLocationDeclared`, `fineLocationGranted`, `fineLocationAppOp`,
   `wifiP2pBroadcastDeniedByPermission`.
3. Fix evidence wording/fields so transport ACK cannot masquerade as delivery.
4. Replace phone evidence with a directed harness:
   `target-before -> sender -> target-after`.
5. Add foreground-service start wrapper and diagnostics.
6. Add `ApplicationExitInfo` export for Xiaomi/MIUI diagnosis.
7. Extract and test Wi-Fi Direct endpoint resolver.
8. Add route attempt state machine and TTL endpoint cache.
9. Add Adamova negative admission probes over the transport-independent path.
10. Only then rerun full Wi-Fi Direct delivery evidence.

## Current Confidence

High confidence:

- background FGS start failure is real and already recorded;
- sender-side Wi-Fi Direct ACK is not enough for delivery proof;
- Wi-Fi Direct endpoint resolution needs explicit state/source/freshness;
- Adamova should remain transport-independent and above transport.
- the current Wi-Fi Direct permission model is incomplete for the tested
  Xiaomi/API 35 path: `NEARBY_WIFI_DEVICES` is granted, but the system still
  requires `ACCESS_FINE_LOCATION` for `PEERS_CHANGED`.

Medium confidence:

- stale endpoint/peer cache contributes to reverse Samsung -> Xiaomi flakiness;
- MIUI/HyperOS is amplifying lifecycle failures beyond stock Android behavior.

Low confidence after the current phone tests:

- Xiaomi is crashing via `FATAL EXCEPTION`;
- Xiaomi is being killed specifically by MIUI battery/autostart policy;
- raw Wi-Fi Direct is impossible to stabilize enough for the research prototype.
