# Route-Aware Capability Model

Status: future design note.
Scope: Kraken Android mesh messenger UX and transport scheduling.
Implementation plan: [`route-aware-implementation-plan.md`](route-aware-implementation-plan.md).

## Problem

The app must not show a generic "available through mesh" state when the current
path is actually a direct Bluetooth link, a direct LAN/Wi-Fi link, or no route at
all. A mesh messenger has different costs and guarantees depending on the route.
If the app constantly probes contacts through other users' phones, it can flood
low-bandwidth channels and waste battery.

The UI should be route-aware:

- show how the contact is reachable now;
- avoid fake online/read/call affordances;
- enable rich features only when the current route can support them;
- keep multi-hop mesh traffic small, throttled, and useful.

## Route States

Use these user-facing route classes instead of one generic mesh label.

| Route kind | Meaning | Suggested UI label | Notes |
| --- | --- | --- | --- |
| `NONE` | No current route to the contact. | `No route` / localized `net marshruta` | Messages may be stored locally and sent later. |
| `DIRECT_BLE` | The peer is directly discovered over BLE/GATT. | `Bluetooth direct` | Good for text and small payloads. |
| `DIRECT_LAN` | The peer is directly reachable over LAN/Wi-Fi/Wi-Fi Direct class transport. | `Wi-Fi direct` or `Fast direct link` | Good for media and calls if the session is stable. |
| `ROUTED_MESH` | The peer is not direct, but a relay/multi-hop path is known. | `Through mesh` | Expensive. Use for text/control only by default. |

Direct BLE can still be a one-hop mesh link internally, but the product UI should
not call it just "mesh". For a user, direct Bluetooth and routed mesh imply very
different feature availability.

## Route Metadata

The route model should carry enough data to make product decisions without
probing too aggressively.

```kotlin
enum class PeerRouteKind {
    NONE,
    DIRECT_BLE,
    DIRECT_LAN,
    ROUTED_MESH,
}

data class PeerRouteSnapshot(
    val relationshipId: String,
    val kind: PeerRouteKind,
    val transportId: String?,
    val hopCount: Int?,
    val bandwidthClass: BandwidthClass,
    val lastSeenAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val routeCost: Int?,
)

enum class BandwidthClass {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}
```

Implementation detail: derive `DIRECT_BLE` from active `ble-gatt` peers,
`DIRECT_LAN` from `lan-nsd-tcp`/future Wi-Fi Direct peers, and `ROUTED_MESH`
only when the protocol actually has relay/path evidence. Do not infer routed mesh
from stale direct discovery. A non-relationship peer can contribute to
`ROUTED_MESH` only as a realm relay peer with validated realm membership/relay
policy; it must not replace the direct-message relationship target.

## Capability Policy

The composer and message actions should be enabled from route capabilities, not
from a static feature list.

| Feature | `NONE` | `DIRECT_BLE` | `DIRECT_LAN` | `ROUTED_MESH` |
| --- | --- | --- | --- | --- |
| Text message | queue locally | yes | yes | yes, budgeted |
| Delivery receipt | if later delivered | yes, if supported | yes, if supported | yes, if supported |
| Reactions | no | yes, direct only | yes | no by default |
| Built-in stickers | no | yes, ID-only | yes | no by default |
| Custom stickers | no | no by default | yes | no |
| GIF | no | no | yes | no |
| Voice note <= 15s | queue only if policy allows | yes | yes | no by default |
| Full voice note | no | no by default | yes | no |
| Photo | no | no by default | yes | no |
| Video file | no | no | yes | no |
| Voice call | no | no | direct high-speed only | no |
| Video call | no | no | direct high-speed only | no |
| Live location | explicit opt-in only | small updates only | yes, explicit opt-in | heavily throttled or no |

Key rule: multi-hop mesh should carry cheap events only. Reactions, custom
stickers, GIFs, media and call signaling can become spam if they are routed
through other users' phones.

## Presence Policy

Do not implement Telegram-style online status.

Allowed states:

- direct route is active now;
- last direct contact time;
- route expired;
- waiting for route;
- no route;
- routed mesh path known, with hop count if available.

Avoid:

- pinging all contacts through mesh;
- showing "online" without direct evidence;
- turning route discovery into continuous multi-hop presence traffic;
- using other users' phones to maintain cosmetic availability indicators.

Recommended behavior:

- direct discovery can refresh often while the foreground service is enabled;
- routed mesh reachability should be passive, TTL-based, or triggered by actual
  outgoing traffic;
- route entries must expire;
- UI should show "last contact" instead of fake availability when evidence is
  stale.

## UI Implications

Conversation header:

- replace generic "available through mesh" with route-specific copy;
- show direct Bluetooth, direct Wi-Fi/LAN, through mesh, no route, or last
  contact time;
- do not show call/menu features until capability policy allows them.

Composer:

- text input is always available unless relationship policy blocks it;
- rich actions appear only when the route supports them;
- disabled rich actions should explain the missing route, not blame the user.

Message status:

- keep mesh-aware delivery states;
- do not add read receipts unless the protocol has a real read event;
- routed-mesh delivery should not imply peer is currently online.

Home / Mesh Status:

- show transport health separately from per-contact route state;
- diagnostics can expose `transportId`, hop count, route cost and TTL;
- user-facing screens should use concise route labels.

## Implementation Plan

1. Add `PeerRouteKind`, `BandwidthClass`, `PeerRouteSnapshot` and a route
   aggregator that maps current transport discovery into per-relationship routes.
2. Replace chat header subtitle logic with route-aware labels.
3. Add a capability policy layer for composer actions.
4. Add tests for route selection priority:
   `DIRECT_LAN` > `DIRECT_BLE` > `ROUTED_MESH` > `NONE`.
5. Add route TTL and last-seen handling.
6. Only after that, consider richer features:
   short voice notes on direct BLE, media/calls on direct LAN/Wi-Fi Direct, and
   built-in sticker IDs on direct links.

## Acceptance Criteria

- The app never labels direct Bluetooth as generic mesh availability.
- The app distinguishes direct high-speed routes from low-bandwidth BLE.
- Multi-hop mesh does not run continuous presence probes for every contact.
- Composer actions are enabled by route capabilities.
- Heavy features are unavailable unless a direct high-speed route exists.
- Route state expires and falls back to last-contact/no-route copy.
