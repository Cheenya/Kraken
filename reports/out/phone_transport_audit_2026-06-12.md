# Kraken Phone Transport Audit

Дата: 2026-06-12.
Scope: Samsung + Xiaomi phone audit after devices were connected.
Initial audit phase: код не менялся. Later addendum below records local fixes
and rerun evidence. Commit/push не выполнялись.

## Devices

| Serial | Vendor / model | Android | API | Package |
| --- | --- | --- | --- | --- |
| `R5CY22X6MSB` | Samsung `SM-S938B` | 16 | 36 | `com.disser.kraken` `0.1.0` |
| `d948ffd0` | Xiaomi `2201122G` | 15 | 35 | `com.disser.kraken` `0.1.0` |

## Artifacts Captured

Lifecycle / logs:

- `artifacts/phone-audit/20260612-140309-xiaomi-lifecycle-baseline`
- `artifacts/phone-audit/20260612-140802-post-wifi-direct-send-logs`

Wi-Fi Direct setup / send:

- `artifacts/debug-route-evidence/20260612-140327-phone-audit-xiaomi-background-fgs-start-2026-06-12`
- `artifacts/debug-route-evidence/20260612-140359-phone-audit-both-foreground-wifi-direct-settled-2026-06-12`
- `artifacts/debug-route-evidence/20260612-140454-phone-audit-force-wifi-direct-only-2026-06-12`
- `artifacts/debug-route-evidence/20260612-140637-phone-audit-wifi-direct-bidirectional-debug-send-2026-06-12`

Directed Wi-Fi Direct:

- `artifacts/debug-route-evidence/20260612-140948-phone-audit-directed-target-xiaomi-hold-wifi-direct-2026-06-12`
- `artifacts/debug-route-evidence/20260612-141025-phone-audit-directed-sender-samsung-to-xiaomi-wifi-direct-2026-06-12`
- `artifacts/debug-route-evidence/20260612-141219-phone-audit-directed-target-xiaomi-post-samsung-send-2026-06-12`
- `artifacts/debug-route-evidence/20260612-141244-phone-audit-directed-target-samsung-hold-wifi-direct-2026-06-12`
- `artifacts/debug-route-evidence/20260612-141321-phone-audit-directed-sender-xiaomi-to-samsung-wifi-direct-2026-06-12`
- `artifacts/debug-route-evidence/20260612-141504-phone-audit-directed-target-samsung-post-xiaomi-send-2026-06-12`

LAN control:

- `artifacts/debug-route-evidence/20260612-140815-phone-audit-lan-only-control-debug-send-2026-06-12`

## Short Answer

Final status after local fixes and fresh phone rerun:

- UI boundary corrected: normal Settings no longer exposes a separate
  Wi-Fi Direct toggle. It shows `Маршруты связи` with state `Авто`; tapping the
  row switches to `Проверка` and reveals diagnostic rows (`Связь рядом`,
  `Исследовательский режим`). This keeps Wi-Fi Direct as debug/evidence
  infrastructure, not a user-facing transport mode.
- Runtime permissions are clean on both phones:
  `NEARBY_WIFI_DEVICES=true`, `ACCESS_FINE_LOCATION=true`, app-op allowed /
  foreground, and `wifi_direct_permission_warning=null` in fresh evidence.
- Directed Wi-Fi Direct now proves both layers in both directions:
  target transport counter delta and message-id-bound target delivery.

The strongest concrete blocker found in the initial audit was a Wi-Fi Direct
permission mismatch on modern Android / Xiaomi:

- `AndroidManifest.xml` declares `ACCESS_FINE_LOCATION` only with
  `android:maxSdkVersion="32"`.
- `WifiDirectPermissions.requiredRuntimePermissions()` requests only
  `NEARBY_WIFI_DEVICES` on Android 13+.
- On Xiaomi API 35, logcat repeatedly reports that Kraken's registered Wi-Fi
  Direct receiver is skipped because `WIFI_P2P_PEERS_CHANGED_ACTION` requires
  `android.permission.ACCESS_FINE_LOCATION`.

This makes the earlier MIUI suspicion more concrete: at least part of the
failure is not "Xiaomi randomly crashes", but "the app does not request a
permission that the device still enforces for a Wi-Fi Direct broadcast path".

The original warning below is preserved as baseline evidence. It is no longer
the current final result after the permission, endpoint retry, UX boundary and
target-id evidence fixes.

## Key Evidence

### P0: ACCESS_FINE_LOCATION Is Missing For Wi-Fi Direct Broadcasts

Code:

- `app-android/app/src/main/AndroidManifest.xml` declares:
  - `NEARBY_WIFI_DEVICES`
  - `ACCESS_COARSE_LOCATION` with `maxSdkVersion=32`
  - `ACCESS_FINE_LOCATION` with `maxSdkVersion=32`
- `app-android/app/src/main/java/com/disser/kraken/mesh/WifiDirectPermissions.kt`
  returns only `NEARBY_WIFI_DEVICES` for Android 13+.

Live package state on both phones:

- requested permissions do not include `ACCESS_FINE_LOCATION`;
- runtime permissions include `NEARBY_WIFI_DEVICES=true`;
- appops show `FINE_LOCATION: ignore`.

Xiaomi logcat:

```text
Permission Denial: receiving Intent { act=android.net.wifi.p2p.PEERS_CHANGED ... }
to ProcessRecord{... com.disser.kraken ...}
requires android.permission.ACCESS_FINE_LOCATION due to sender android (uid 1000)
```

This appeared repeatedly in:

- `artifacts/phone-audit/20260612-140802-post-wifi-direct-send-logs/d948ffd0/logcat_main_system_filtered.txt`

Impact:

- `WIFI_P2P_PEERS_CHANGED_ACTION` can be skipped on Xiaomi.
- Wi-Fi Direct peer state can become stale or incomplete.
- DNS-SD TXT callbacks may not be enough to keep endpoint state reliable.
- The app UI currently believes Wi-Fi Direct runtime permission is satisfied
  because `NEARBY_WIFI_DEVICES` is granted, but the OS still denies a broadcast
  path requiring fine location.

### P0: Forced Wi-Fi Direct-Only Discovery Works Before Send

Artifact:

- `20260612-140454-phone-audit-force-wifi-direct-only-2026-06-12`

Both devices:

- `enabled_transport_modes=["wifi-direct"]`
- `wifi_direct.active=true`
- `transport_discovered_peer_count=1`
- `p2p_visible_device_count=1`
- `p2p_service_found_count=1`
- `p2p_txt_record_count=1`
- `p2p_txt_bound_peer_count=1`
- `relationship_peer_seen_by_transport=true`

This proves the implementation can reach a useful pre-send discovery state.
It does not prove delivery.

### P0: Directed Samsung -> Xiaomi Still Fails

Artifacts:

- target hold:
  `20260612-140948-phone-audit-directed-target-xiaomi-hold-wifi-direct-2026-06-12`
- sender:
  `20260612-141025-phone-audit-directed-sender-samsung-to-xiaomi-wifi-direct-2026-06-12`
- target post:
  `20260612-141219-phone-audit-directed-target-xiaomi-post-samsung-send-2026-06-12`

Target Xiaomi before send:

- `selected_route=wifi-direct`
- `transport_discovered_peer_count=1`
- `relationship_peer_seen_by_transport=true`
- `wifi_direct_server_bind_address=192.168.49.59`
- `accepted_connections=0`
- `inbound_packets=0`

Sender Samsung:

- `wifi_direct_group_formed=true`
- `wifi_direct_is_group_owner=true`
- `wifi_direct_group_owner_address=192.168.49.1`
- `wifi_direct_server_bind_address=192.168.49.1`
- `transport_discovered_peer_count=0`
- `p2p_service_found_count=0`
- `p2p_txt_record_count=0`
- `debug_send_success=false`
- four attempts ended `UNKNOWN_PEER`
- `debug_transport_error=wifi-direct-send:wifi-direct-peer-not-found:...`

Target Xiaomi after send:

- `accepted_connections=0`
- `inbound_packets=0`

Interpretation:

Samsung as group owner forms a group, but loses the transport-bound peer/TXT
route and does not reach the Xiaomi listener. This matches the architectural
suspicion: endpoint/session state is not robust enough for the group-owner
side.

### P0: Directed Xiaomi -> Samsung Is Sender-Side Only, Not Delivery Proof

Artifacts:

- target hold:
  `20260612-141244-phone-audit-directed-target-samsung-hold-wifi-direct-2026-06-12`
- sender:
  `20260612-141321-phone-audit-directed-sender-xiaomi-to-samsung-wifi-direct-2026-06-12`
- target post:
  `20260612-141504-phone-audit-directed-target-samsung-post-xiaomi-send-2026-06-12`

Sender Xiaomi:

- `wifi_direct_group_formed=true`
- `wifi_direct_is_group_owner=false`
- `wifi_direct_group_owner_address=192.168.49.1`
- `wifi_direct_last_send_host=192.168.49.1`
- `wifi_direct_last_send_port=48381`
- `debug_send_success=true`

Target Samsung:

- before send: `accepted_connections=2`, `inbound_packets=2`
- after send: `accepted_connections=2`, `inbound_packets=2`

Interpretation:

The target counters did not increase during the directed run. Therefore this is
not fresh delivery proof. It should be treated as sender-side transport success
or stale/carry-over state until message-id-bound target evidence exists.

### P1: Multi-Device Debug Capture Can Overclaim

The multi-device script captures each device sequentially. That means one
device can export before the other performs a send. As a result, a single
manifest can mix:

- sender-side ACK;
- target counters from previous state;
- target export taken before current send.

This validates the previous plan recommendation: use a directional harness with
target-before, sender, target-after, and message-id-bound counters.

### Final Rerun: UX Boundary And Message-Id-Bound Directed Delivery

Code / APK state:

- Fresh debug APK built and installed on both phones on 2026-06-12 after the
  UI/evidence changes.
- No commit/push/deploy performed.

UI artifacts:

- `artifacts/ui-device-verification/20260612-final-route-mode-toggle/R5CY22X6MSB`
- `artifacts/ui-device-verification/20260612-final-route-mode-toggle/d948ffd0`

Observed Settings behavior:

- before tap: `Маршруты связи` row shows `Авто`;
- after tap: the same row shows `Проверка`;
- after tap diagnostic rows appear: `Связь рядом`, `Исследовательский режим`;
- no standalone `Wi-Fi Direct` Settings row and no `android.widget.Switch` for
  Wi-Fi Direct were observed in the UI dumps.

Permission state:

- Samsung `R5CY22X6MSB`: `ACCESS_FINE_LOCATION=true`,
  `NEARBY_WIFI_DEVICES=true`; appops include `FINE_LOCATION: allow/foreground`
  and `NEARBY_WIFI_DEVICES: allow`.
- Xiaomi `d948ffd0`: `ACCESS_FINE_LOCATION=true`,
  `NEARBY_WIFI_DEVICES=true`; appops include `FINE_LOCATION: allow/foreground`
  and `NEARBY_WIFI_DEVICES: allow`.

Fresh directed Wi-Fi Direct artifacts:

- Samsung -> Xiaomi:
  `artifacts/directed-wifi-direct/20260612-180503-samsung-to-xiaomi-final-newly-observed-message-id/manifest.json`
- Xiaomi -> Samsung:
  `artifacts/directed-wifi-direct/20260612-180625-xiaomi-to-samsung-final-newly-observed-message-id/manifest.json`

Samsung -> Xiaomi:

- sender debug send: `true`;
- target deltas: `accepted_connections=+1`, `inbound_packets=+1`,
  `malformed_frames_dropped=0`;
- sender `message_id`:
  `message-0b1ce50b-f168-4c46-a5f3-40af8670b64a`;
- sender `packet_id`:
  `packet-8200cab0-9f5d-4f59-b189-debc59327eed`;
- target `newly_observed_after.matching_message_ids` contains that message id;
- target `newly_observed_after.matching_packet_ids` contains that packet id;
- verdict: `target_message_id_delivery_proven`.

Xiaomi -> Samsung:

- sender debug send: `true`;
- target deltas: `accepted_connections=+1`, `inbound_packets=+1`,
  `malformed_frames_dropped=0`;
- sender `message_id`:
  `message-81babb53-1ce4-442c-b133-5d9196758a74`;
- sender `packet_id`:
  `packet-7b32bb37-5d13-412f-a52d-2d70bb1e7ed9`;
- target `newly_observed_after.matching_message_ids` contains that message id;
- target `newly_observed_after.matching_packet_ids` contains that packet id;
- verdict: `target_message_id_delivery_proven`.

Boundary:

- `transport_counter_delivery_observed=true` means target counters increased
  after the directed sender debug-send.
- `message_delivery_proven=true` now means target-after evidence contains a
  newly observed matching sender `message_id` and `packet_id` compared with
  target-before.
- This is still debug directed evidence, not production reliability/security.
  Remaining work is reliability sampling, route benchmark integration and
  production security boundary work, not the original P0 permission blocker.

### P1: Foreground-Service Failure Was Not Reproduced In Current State

Artifact:

- `20260612-140327-phone-audit-xiaomi-background-fgs-start-2026-06-12`

Current Xiaomi state:

- process already running;
- `START_FOREGROUND: allow`;
- foreground service present;
- `wifi_direct.active=true`.

The earlier saved `mAllowStartForeground=false` failure remains valid evidence,
but this run did not reproduce it. Current audit points more strongly at Wi-Fi
Direct permissions/session state than at an immediate FGS crash.

### P1: No Fresh Kraken FATAL EXCEPTION Found

Crash buffer contained unrelated crashes from other packages. The filtered
Kraken logs did not show a fresh `FATAL EXCEPTION` for `com.disser.kraken`
during this audit.

Xiaomi did show MIUI/PowerKeeper/MiSight lines mentioning Kraken as foreground,
but there is no evidence here that PowerKeeper killed Kraken during the tested
window.

## Recommended Fix Order

### 1. Fix Wi-Fi Direct Permission Model

Change the model from:

```kotlin
if API >= 33: request NEARBY_WIFI_DEVICES only
```

to a device-safe policy for Wi-Fi Direct:

```kotlin
if API >= 33: request NEARBY_WIFI_DEVICES + ACCESS_FINE_LOCATION
```

And update `AndroidManifest.xml` so `ACCESS_FINE_LOCATION` is requestable on
modern Android when Wi-Fi Direct is enabled.

Important wording boundary:

- this is not background location tracking;
- this is a runtime permission needed for Wi-Fi Direct peer/broadcast behavior;
- keep `neverForLocation` where appropriate for `NEARBY_WIFI_DEVICES`, but do
  not pretend all devices accept it as a full replacement for fine location in
  Wi-Fi Direct.

### 2. Add Permission Diagnostics To Route Export

Export separately:

- `nearbyWifiDevicesGranted`
- `fineLocationDeclared`
- `fineLocationGranted`
- `fineLocationAppOp`
- `wifiP2pBroadcastDeniedByPermission`

If logcat reports permission denial, surface it in the route evidence summary.

### 3. Extract Wi-Fi Direct Endpoint Resolver

Keep the previous architecture plan:

- source: DNS-SD / group-owner address / ARP device match / ARP single-client /
  manual debug;
- confidence: bound-peer / group-fallback / debug-only;
- TTL/freshness;
- clear endpoint on group/role changes.

This phone audit strengthens that recommendation because the group-owner side
lost peer binding during directed Samsung -> Xiaomi.

### 4. Replace Multi-Device Debug Send With Directional Harness

New evidence gate:

1. target start/hold export;
2. sender send export;
3. target post-send export;
4. compare target counters by message ID.

Do not accept `debug_send_success=true` without target post-send growth and
message ID evidence.

### 5. Keep Adamova Above Transport

This audit did not find an Adamova bypass. The failed Wi-Fi Direct delivery is
below the admission layer. Adamova effectiveness should still be proven through
transport-independent positive/negative admission tests, then replayed over
working routes.

## Current Conclusion

Most likely root cause cluster:

1. Wi-Fi Direct permission mismatch on API 35/36, especially Xiaomi enforcing
   `ACCESS_FINE_LOCATION` for `PEERS_CHANGED`.
2. Wi-Fi Direct session/endpoint state is too implicit and loses peer binding
   around group formation.
3. Existing evidence tooling can overclaim because sender success and target
   delivery are not captured as one message-id-bound sequence.

This is actionable: fix permissions first, then rerun the same directed
Samsung/Xiaomi tests before changing cryptography or message pipeline logic.

## Addendum: P0 Fix And Directed Rerun

Timestamp: 2026-06-12 17:25-17:28 Europe/Moscow.

Local fixes applied before rerun:

- Android 13+ Wi-Fi Direct now requests/checks both `NEARBY_WIFI_DEVICES` and
  `ACCESS_FINE_LOCATION`; `ACCESS_FINE_LOCATION` is no longer capped at SDK 32.
- Evidence exports now include `wifi_direct_permissions` and explicit fine
  location declared/granted/app-op status.
- Directed harness now launches the app before the debug broadcast when starting
  foreground Wi-Fi Direct, avoiding the previous background FGS blocker.
- `WifiDirectTransport` now retries endpoint resolution after connect/group
  formation and, when the local device is Wi-Fi Direct group owner, resolves the
  single client endpoint from `WifiP2pGroup`/`WifiP2pDevice` before falling back
  to ARP.

Directed artifacts:

- Samsung -> Xiaomi:
  `artifacts/directed-wifi-direct/20260612-172544-samsung-to-xiaomi-groupinfo-endpoint-autoforeground/manifest.json`
- Xiaomi -> Samsung:
  `artifacts/directed-wifi-direct/20260612-172712-xiaomi-to-samsung-groupinfo-endpoint-autoforeground/manifest.json`

Results:

| Direction | Sender success | Sender route | Sender endpoint | Target accepted delta | Target inbound delta | Permission warning | Verdict |
| --- | ---: | --- | --- | ---: | ---: | --- | --- |
| Samsung -> Xiaomi | true | `wifi-direct` | `192.168.49.59:48381` | 1 | 1 | null | `target_transport_counter_delta_observed` |
| Xiaomi -> Samsung | true | `wifi-direct` | `192.168.49.1:48381` | 1 | 1 | null | `target_transport_counter_delta_observed` |

Permission acceptance:

- sender `fine_location_granted=true` in both directions;
- target-before `fine_location_granted=true` in both directions;
- target-after `fine_location_granted=true` in both directions;
- `wifi_direct_permission_warning=null` in both final manifests.

Claim boundary:

- This proves fresh target transport counter delivery in both Wi-Fi Direct
  directions for this directed harness run.
- `message_delivery_proven` remains false because the harness still does not
  match target-side delivery by message id.

Next scoped work:

1. Add message-id-bound target evidence so `message_delivery_proven` can become
   true only when the target observes the same message id/packet id.
2. Keep the group-owner endpoint resolver guarded by diagnostics; if future
   devices expose no client IP through `WifiP2pGroup`, surface that explicitly
   instead of collapsing to `UNKNOWN_PEER`.
