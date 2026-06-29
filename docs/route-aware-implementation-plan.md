# Route-Aware UX Implementation Plan

Status: implementation plan.
Source design: [`route-aware-capability-model.md`](route-aware-capability-model.md).
Backlog section: `Route-Aware UX` in [`kraken-engineering-backlog.md`](kraken-engineering-backlog.md).

## Goal

Replace generic chat availability copy such as `dostupen cherez mesh` with a
route-aware model that distinguishes:

- no current route;
- direct BLE/GATT;
- direct LAN/Wi-Fi-class route;
- future routed/multi-hop mesh.

Then use that route state to decide which composer actions and message features
are allowed. This is a UX and routing-state foundation; it does not implement
media attachments, stickers, calls, or true routed mesh by itself.

## Non-Goals

- Do not implement Wi-Fi Direct transport in this pass.
- Do not implement audio/photo/video/GIF payload transfer in this pass.
- Do not add Telegram-style online/read status.
- Do not add continuous multi-hop presence probing.
- Do not change wire protocol unless route evidence requires a later explicit
  migration.

## Current Code Anchors

Use these current files as the first implementation targets:

- `app-android/app/src/main/java/com/disser/kraken/mesh/PeerTransport.kt`
  - `DiscoveredPeer`
  - `MeshRouteAttempt`
  - `MeshTransportDiagnostics`
- `app-android/app/src/main/java/com/disser/kraken/mesh/MeshService.kt`
  - `MeshServiceSnapshot`
  - `MeshState`
- `app-android/app/src/main/java/com/disser/kraken/mesh/CompositePeerTransport.kt`
  - `observePeers()`
  - `diagnostics().recentRouteAttempts`
- `app-android/app/src/main/java/com/disser/kraken/mesh/TransportCapability.kt`
  - `LAN_NSD_TCP`
  - `BLE_GATT`
- `app-android/app/src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt`
  - `meshAwareContactSubtitle(...)`
  - composer action surface
- `app-android/app/src/main/java/com/disser/kraken/ui/screens/HomeScreen.kt`
- `app-android/app/src/main/java/com/disser/kraken/ui/screens/MeshStatusScreen.kt`
- `app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt`
  - owns `meshSnapshot` for UI.

## Phase 1 - Route Model and Aggregation

Priority: P1.
Estimated size: medium.

Add route model types:

```kotlin
enum class PeerRouteKind {
    NONE,
    DIRECT_BLE,
    DIRECT_LAN,
    ROUTED_MESH,
}

enum class BandwidthClass {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

data class PeerRouteSnapshot(
    val relationshipId: String,
    val peerFingerprint: String,
    val kind: PeerRouteKind,
    val transportId: String?,
    val bandwidthClass: BandwidthClass,
    val hopCount: Int?,
    val lastSeenAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val evidence: PeerRouteEvidence,
)
```

Suggested file:

- `app-android/app/src/main/java/com/disser/kraken/mesh/PeerRouteModels.kt`

Add an aggregator:

- `PeerRouteAggregator.snapshotFor(...)`
- Inputs:
  - `relationships: List<Relationship>`
  - `meshSnapshot: MeshServiceSnapshot`
  - current clock
- Output:
  - `List<PeerRouteSnapshot>`

Initial route derivation:

| Evidence | Route |
| --- | --- |
| peer fingerprint appears through `ble-gatt` diagnostics | `DIRECT_BLE` |
| peer fingerprint appears through `lan-nsd-tcp` diagnostics | `DIRECT_LAN` |
| future relay/path evidence with `hopCount > 1` | `ROUTED_MESH` |
| no fresh evidence | `NONE` |

Important implementation detail: current `DiscoveredPeer` does not store the
transport that discovered it, and `CompositePeerTransport.observePeers()` merges
peers by fingerprint. Phase 1 therefore needs one of these changes:

1. Extend `DiscoveredPeer` with `transportId: String?`, or
2. Add a separate `DiscoveredPeerRouteEvidence` list to diagnostics, preserving
   `fingerprint + transportId + observedAt`.

Prefer option 2 if changing serialized `DiscoveredPeer` is risky.

Acceptance criteria:

- Unit test maps BLE-only evidence to `DIRECT_BLE`.
- Unit test maps LAN-only evidence to `DIRECT_LAN`.
- Unit test prefers `DIRECT_LAN` over `DIRECT_BLE` when both are fresh.
- Unit test maps stale direct evidence to `NONE`.
- No UI still needs to infer route from only `discoveredPeers.any { ... }`.

## Phase 2 - Route TTL and Last-Seen State

Priority: P1.
Estimated size: small/medium.

Add route freshness:

- direct route evidence TTL: short, for example 10-30 seconds;
- last direct contact timestamp: longer-lived display value;
- no multi-hop probing just to refresh UI labels.

Suggested data:

```kotlin
data class PeerRouteCacheEntry(
    val peerFingerprint: String,
    val routeKind: PeerRouteKind,
    val transportId: String?,
    val lastSeenAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)
```

Suggested owner:

- in-memory first inside mesh runtime/service;
- persistent storage only if needed for last-contact UX after app restart.

Acceptance criteria:

- A direct route disappears after TTL when discovery stops.
- Header falls back to `last contact: HH:mm` or `no route`, not fake online.
- No periodic routed-mesh contact probes are introduced.

## Phase 3 - UI Copy Replacement

Priority: P1.
Estimated size: small.

Replace `meshAwareContactSubtitle(...)` with a route-aware formatter.

Suggested function:

```kotlin
fun peerRouteSubtitle(route: PeerRouteSnapshot, meshState: MeshState): String
```

Localized copy:

| Route | Russian UI copy |
| --- | --- |
| `DIRECT_BLE` | `Bluetooth напрямую` |
| `DIRECT_LAN` | `Wi-Fi/LAN напрямую` or `Быстрый прямой канал` |
| `ROUTED_MESH` | `Через mesh` |
| `NONE` with last seen | `последний контакт: HH:mm` |
| `NONE` without last seen | `нет маршрута` |
| mesh off | `локальный контакт` |

Screens to update:

- chat conversation header;
- chat list secondary status if present;
- Home nearby state block;
- Mesh Status diagnostics.

Acceptance criteria:

- Direct Bluetooth is never shown as generic `dostupen cherez mesh`.
- Direct LAN and BLE have distinct labels.
- No fake online status appears.
- Mesh Status can still expose diagnostics for developers.

## Phase 4 - Capability Policy

Priority: P1.
Estimated size: medium.

Add:

- `RouteCapabilityPolicy`
- `RouteFeature`
- `RouteFeatureDecision`

Suggested file:

- `app-android/app/src/main/java/com/disser/kraken/mesh/RouteCapabilityPolicy.kt`

Suggested feature enum:

```kotlin
enum class RouteFeature {
    TEXT,
    DELIVERY_RECEIPT,
    REACTION,
    BUILT_IN_STICKER,
    CUSTOM_STICKER,
    GIF,
    SHORT_VOICE_NOTE,
    FULL_VOICE_NOTE,
    PHOTO,
    VIDEO_FILE,
    VOICE_CALL,
    VIDEO_CALL,
    LIVE_LOCATION,
}
```

Initial policy:

| Feature | `NONE` | `DIRECT_BLE` | `DIRECT_LAN` | `ROUTED_MESH` |
| --- | --- | --- | --- | --- |
| `TEXT` | queue | allow | allow | allow budgeted |
| `REACTION` | deny | allow | allow | deny |
| `BUILT_IN_STICKER` | deny | allow ID-only | allow | deny |
| `SHORT_VOICE_NOTE` | deny/queue later | allow with limit | allow | deny |
| `PHOTO` | deny | deny | allow | deny |
| `VIDEO_FILE` | deny | deny | allow | deny |
| `VOICE_CALL` | deny | deny | allow direct high-speed | deny |
| `VIDEO_CALL` | deny | deny | allow direct high-speed | deny |

Acceptance criteria:

- Composer asks policy before showing/enabling future rich actions.
- Text send remains available according to relationship policy.
- Route policy does not mutate transport state.
- Unit tests cover each route kind and feature class.

## Phase 5 - Diagnostics and Developer Evidence

Priority: P1/P2.
Estimated size: small.

Expose route state in diagnostics without overloading user copy:

- per-contact route kind;
- transport id;
- TTL/expiry;
- last seen;
- hop count when future routed mesh exists;
- recent route attempts.

Screens/scripts:

- `MeshStatusScreen`
- `scripts/kraken_phone_preflight.py` optional route evidence capture later.

Acceptance criteria:

- A phone smoke artifact can prove whether a peer was BLE direct or LAN direct.
- Diagnostics do not claim `ROUTED_MESH` without relay/path evidence.

## Phase 6 - Future Rich Features

Priority: P2+.
Estimated size: separate projects.

Only start after route-aware UX is in place.

Feature order:

1. Built-in sticker IDs on direct routes.
2. Reactions on direct routes only.
3. Short voice notes on `DIRECT_BLE` and better.
4. Attachment framework with checksums/chunks.
5. Photos/GIF/video files on `DIRECT_LAN` only.
6. Voice/video calls on direct high-speed route only.

Each feature must declare:

- max payload size;
- allowed route kinds;
- retry/TTL behavior;
- UI disabled state;
- tests for route denial.

## Testing Plan

Local:

```bash
cd /Users/cheenya/Projects/kraken-android-research-panel/app-android
./gradlew test assembleDebug lintDebug
git diff --check
```

Unit tests:

- `PeerRouteAggregatorTest`
  - BLE-only -> `DIRECT_BLE`
  - LAN-only -> `DIRECT_LAN`
  - LAN+BLE -> `DIRECT_LAN`
  - stale evidence -> `NONE`
  - future relay evidence -> `ROUTED_MESH`
- `RouteCapabilityPolicyTest`
  - reactions denied on `ROUTED_MESH`
  - media denied on BLE/mesh
  - media allowed on direct LAN
  - text allowed/queued according to route and relationship policy
- UI label tests if existing test stack supports them.

Device smoke:

- Xiaomi + Samsung with Bluetooth only:
  - header says `Bluetooth напрямую`;
  - no media/call action is shown;
  - text sends.
- Same LAN/Wi-Fi:
  - header says direct LAN/Wi-Fi when LAN route is the winning route;
  - future rich actions may become visible only after implementation.
- No route:
  - header says `нет маршрута` or last-contact copy;
  - no direct availability claim.

## Rollout Notes

- Implement model and copy first; do not add rich feature UI until policy is in
  place.
- Keep direct-route and routed-mesh terminology separate in user-facing text.
- Treat `ROUTED_MESH` as unavailable until protocol evidence exists.
- Avoid introducing background probes that exist only to make the UI look alive.
- Update `route-aware-capability-model.md` if implementation changes the policy.
