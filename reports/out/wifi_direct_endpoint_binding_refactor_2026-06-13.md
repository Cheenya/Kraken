# Wi-Fi Direct Endpoint Binding Refactor Report

Дата: 2026-06-13.

## Scope

Цель прохода: снять плоский `UNKNOWN_PEER` и разделить стадии Wi-Fi Direct
directed send:

- permissions ready;
- relationship ready;
- Wi-Fi Direct discovery ready;
- endpoint bound;
- send attempted;
- message delivery proven.

## Implemented

- Добавлена diagnostics-модель для Wi-Fi Direct endpoint binding:
  `UNSEEN`, `DISCOVERED_UNBOUND`, `BOUND`, `STALE`, `FAILED`.
- В route evidence добавлены:
  - group role: `owner` / `client` / `none`;
  - group owner address;
  - local P2P address;
  - visible P2P devices;
  - TXT records;
  - discovered peer endpoints;
  - bound fingerprint -> endpoint map;
  - relationship peer fingerprint prefix;
  - endpoint binding state/reason.
- Debug command result теперь экспортирует те же поля, чтобы directed harness
  мог строить stage-aware verdict.
- Directed harness verdict расширен:
  - `permissions_ready`;
  - `relationship_ready`;
  - `wifi_direct_discovery_ready`;
  - `endpoint_bound`;
  - `send_attempted`;
  - `message_delivery_proven`.
- Добавлен узкий fallback: если TXT binding отсутствует, но виден подходящий
  available Wi-Fi Direct device, transport пытается сформировать group по
  `WifiP2pDevice.deviceAddress`.
- Добавлен reset stale P2P negotiation на старте discovery:
  `cancelConnect` и `removeGroup` для очистки зависших invited/group states.
- Добавлены connect diagnostics:
  - last connect device name/address;
  - last connect result;
  - last connect failure reason.
- Target fallback больше не выбирает произвольное single visible device:
  named fallback разрешён только когда visible P2P device совпадает с
  relationship peer display name. Это исключает ложный bind к постороннему
  `DIRECT-D6-HP Smart Tank 510`.
- Добавлен retry для `WifiP2pManager.connect()` при `P2P_BUSY_REASON`.
- Retry расширен на generic `WifiP2pManager.ERROR` (`reason=0`): перед
  повтором выполняется reset stale negotiation, fresh discovery и следующая
  попытка использует другой `groupOwnerIntent` (`0`, `15`, `7`).
- Evidence экспортирует `wifi_direct_last_connect_group_owner_intent`.
- Directed harness получил debug-only режим `--prearm-target-group-owner`: target
  может заранее создать Wi-Fi Direct group owner group через public
  `WifiP2pManager.createGroup()`.
- Group formation wait после accepted connect увеличен до `45s`, чтобы не
  обрывать Android negotiation раньше типичных длительных P2P attempts.
- Перед fallback connect добавлено bounded ожидание relationship peer в fresh
  discovery window. Если peer не появляется, evidence фиксирует
  `relationship-peer-not-visible-after-fresh-discovery`.
- Directed harness больше не считает `endpoint_bound=true` только по строке
  `binding_state=BOUND`: требуется реальный bound endpoint или send host/port.
- Debug send result экспортирует `transport_error`, чтобы `UNKNOWN_PEER` не
  скрывал Wi-Fi Direct transport-level причину.

## Phone Evidence

Последний полный прогон:

- artifact root:
  `artifacts/wifi-direct-reliability/20260613-171846-samsung-xiaomi-fresh-peer-wait-verdict-3x/`
- observed trials: `6/6`
- permissions ready: `5/6`
- relationship ready: `6/6`
- Wi-Fi Direct discovery ready: `5/6`
- endpoint bound: `0/6`
- sender success: `0/6`
- transport counter delivery observed: `0/6`
- message delivery proven: `0/6`
- permission warnings: `0`

Direction summary:

| Direction | Trials | Endpoint bound | Message delivery | Permission warnings |
| --- | ---: | ---: | ---: | ---: |
| `R5CY22X6MSB -> d948ffd0` | 3 | 0/3 | 0/3 | 0 |
| `d948ffd0 -> R5CY22X6MSB` | 3 | 0/3 | 0/3 | 0 |

## Current Blocker

The permission blocker is not back. Latest strict evidence is `0/6` delivery
with `permission_warning_count=0`. The active blocker is endpoint binding:
the stable relationship peer exists, but Wi-Fi Direct does not reliably bind it
to a dynamic IP/port endpoint.

Representative final-trial fields:

- `samsung-to-xiaomi/trial-01`:
  - `status=sender_endpoint_not_bound`
  - `wifi_direct_endpoint_binding_state=DISCOVERED_UNBOUND`
  - `wifi_direct_endpoint_binding_reason=p2p-group-not-formed:connect=failed:2:0`
  - `last_connect_device_name=Xiaomi 12 Pro`
  - `last_connect_result=failed:2:0`
- `xiaomi-to-samsung/trial-02`:
  - `status=sender_endpoint_not_bound`
  - `wifi_direct_endpoint_binding_state=UNSEEN`
  - `wifi_direct_endpoint_binding_reason=relationship-peer-not-visible-after-fresh-discovery:Cheenya`
- `xiaomi-to-samsung/trial-03`:
  - `status=sender_endpoint_not_bound`
  - `wifi_direct_endpoint_binding_state=DISCOVERED_UNBOUND`
  - `wifi_direct_endpoint_binding_reason=p2p-group-not-formed:connect=failed:1:0`
- visible devices frequently include only:
  - `DIRECT-D6-HP Smart Tank 510`

Transport-error smoke after adding `transport_error`:

- `artifacts/directed-wifi-direct/20260613-173440-samsung-to-xiaomi-transport-error-smoke/`
- `artifacts/directed-wifi-direct/20260613-173642-xiaomi-to-samsung-transport-error-smoke/`
- both directions:
  - `endpoint_bound=false`
  - `transport_error=wifi-direct:wifi-direct-peer-not-found`
  - binding reason:
    `relationship-peer-not-visible-after-fresh-discovery:<peer display name>`

Interpretation: diagnostics now separate relationship identity from endpoint
binding and do not overclaim. The app still returns legacy rejection
`UNKNOWN_PEER`, but debug evidence now includes the Wi-Fi Direct transport
failure path and binding reason.

Post-retry smoke after adding `ERROR=0` retries and group-owner intent rotation:

- `artifacts/directed-wifi-direct/20260613-174449-samsung-to-xiaomi-error0-retry-intent-smoke/`
- `artifacts/directed-wifi-direct/20260613-174709-xiaomi-to-samsung-error0-retry-intent-smoke/`
- Samsung -> Xiaomi:
  - `endpoint_bound=false`
  - final connect attempt: `groupOwnerIntent=7`
  - `transport_error=wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=failed:3:7:0`
  - later attempts also reported stale target visibility:
    `single-visible-device-stale-no-current-target-visible`
- Xiaomi -> Samsung:
  - `endpoint_bound=false`
  - no connect attempt, because Samsung relationship peer was not visible after
    fresh discovery
  - `transport_error=wifi-direct:wifi-direct-peer-not-found:relationship-peer-not-visible-after-fresh-discovery:Cheenya`

Interpretation update: retrying `reason=0` and rotating group-owner intent did
not create a P2P group in the observed environment. The remaining failure is
below route selection: Android Wi-Fi Direct discoverability/connect negotiation
does not produce a usable group endpoint.

Pre-armed target group-owner smoke:

- `artifacts/directed-wifi-direct/20260613-175757-samsung-to-xiaomi-prearmed-go-smoke/`
- `artifacts/directed-wifi-direct/20260613-180058-xiaomi-to-samsung-prearmed-go-smoke/`
- target `createGroup()` succeeded in both directions:
  `ensure_wifi_direct_group_owner_result=owner`
- Samsung -> Xiaomi still failed to bind:
  - target group role: `owner`
  - target local P2P address: `192.168.49.1`
  - sender saw accepted TXT records for `Xiaomi 12 Pro`
  - sender endpoint remained unbound
- Xiaomi -> Samsung still failed earlier:
  - target became group owner
  - sender did not see Samsung relationship peer after fresh discovery

Long-wait pre-armed Samsung -> Xiaomi smoke:

- `artifacts/directed-wifi-direct/20260613-180442-samsung-to-xiaomi-prearmed-go-long-wait-smoke/`
- target `createGroup()` succeeded: `owner / 192.168.49.1`
- sender discovered accepted Kraken TXT records for `Xiaomi 12 Pro`
- sender saw `Xiaomi 12 Pro` as visible P2P device
- connect attempts reached `accepted:2:15`, but `groupFormed` never became true
  within the longer wait
- final result:
  `endpoint_bound=false`,
  `transport_error=wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=accepted:2:15`

Interpretation update: even with target pre-armed as group owner and a longer
group formation wait, sender does not get a usable group/host endpoint. The
blocker is now specifically Android P2P connection negotiation between the
devices, not Kraken TXT discovery or relationship identity binding.

P2P-state summary smoke after adding compact `dumpsys wifip2p` export:

- `artifacts/directed-wifi-direct/20260613-181441-samsung-to-xiaomi-prearmed-p2p-summary-smoke/`
- directed verdict:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `send_attempted=true`
  - `message_delivery_proven=false`
  - `status=sender_endpoint_not_bound`
- sender transport errors now include the more specific endpoint failures:
  - `wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=accepted:1:0`
  - `wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=failed:3:7:0`
  - `wifi-direct:wifi-direct-peer-not-found:relationship-peer-not-visible-after-fresh-discovery:Xiaomi`
- compact P2P state summary:
  - sender before/after: `curState=ProvisionDiscoveryState`,
    `groupFormed=false`, `mGroup null`
  - sender saved peer config points at Xiaomi P2P device address
    `22:21:c9:0a:44:e1`
  - sender recent connection events end in `connectivityLevelFailureCode=CANCEL`
    and an open provisioning event
  - target prearm succeeded: `curState=GroupCreatedState`,
    `groupFormed=true`, `isGroupOwner=true`, `groupOwnerIpAddress=192.168.49.1`

Interpretation update: the harness now records the exact Android Wi-Fi Direct
state transition around the failed send. The sender sees the relationship peer
and discovery evidence, but remains in provisioning with no formed group, so no
dynamic endpoint can be bound.

Post-guard smoke after suppressing external rediscovery during connect wait:

- `artifacts/directed-wifi-direct/20260613-182137-samsung-to-xiaomi-suppress-rediscovery-during-connect/`
- directed verdict stayed negative:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `send_attempted=true`
  - `message_delivery_proven=false`
  - `status=sender_endpoint_not_bound`
- sender errors:
  - `wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=failed:3:7:0`
  - `wifi-direct:wifi-direct-peer-not-found:relationship-peer-not-visible-after-fresh-discovery:Xiaomi`
- compact P2P state summary:
  - sender before/after: `curState=InactiveState`, `groupFormed=false`,
    `mGroup null`
  - sender recent connection events still include `CANCEL` and `GROUP_REMOVED`
  - target prearm still succeeded initially:
    `curState=GroupCreatedState`, `groupFormed=true`, `isGroupOwner=true`,
    `groupOwnerIpAddress=192.168.49.1`

Interpretation update: suppressing Kraken's external rediscovery loop during
the connect wait did not make Samsung join Xiaomi's pre-armed group. This keeps
the blocker below relationship identity and TXT discovery: Android/OEM Wi-Fi
Direct provisioning still cancels/removes the group before a sendable endpoint
exists.

Post-stop-discovery smoke before connect:

- `artifacts/directed-wifi-direct/20260613-204518-samsung-to-xiaomi-stop-discovery-before-connect-fresh/`
- directed verdict stayed negative:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `send_attempted=true`
  - `message_delivery_proven=false`
  - `status=sender_endpoint_not_bound`
- sender selected route: `wifi-direct`
- sender endpoint state:
  - `wifi_direct_endpoint_binding_state=DISCOVERED_UNBOUND`
  - `wifi_direct_endpoint_binding_reason=p2p-group-not-formed:connect=failed:3:7:0`
  - `wifi_direct_last_connect_device_name=Xiaomi 12 Pro`
  - `wifi_direct_last_connect_failure_reason=0`
- target P2P group survived the attempt:
  - target before/after: `curState=GroupCreatedState`
  - target before/after: `groupFormed=true`, `isGroupOwner=true`,
    `groupOwnerIpAddress=192.168.49.1`
- sender P2P state:
  - before: `curState=P2pDisabledState`, `groupFormed=false`, `mGroup null`
  - after: `curState=InactiveState`, `groupFormed=false`, `mGroup null`

Interpretation update: stopping peer discovery before `connect()` removed the
extra `relationship-peer-not-visible` error in this Samsung -> Xiaomi sample,
but still did not create a sendable endpoint. The remaining blocker is sender
side P2P join/provisioning: Xiaomi can remain a group owner, but Samsung never
transitions into a formed group/client endpoint.

Reverse post-stop-discovery smoke:

- `artifacts/directed-wifi-direct/20260613-205342-xiaomi-to-samsung-stop-discovery-before-connect-fresh/`
- directed verdict stayed negative:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `send_attempted=true`
  - `message_delivery_proven=false`
  - `status=sender_endpoint_not_bound`
- sender errors:
  - `wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=failed:1:0:0`
  - `wifi-direct:wifi-direct-endpoint-unavailable:p2p-group-not-formed:connect=failed:3:7:0`
- target deltas:
  - `accepted_connections=0`
  - `inbound_packets=0`
  - `malformed_frames_dropped=0`
- target P2P group also survived in reverse:
  - Samsung target before/after: `curState=GroupCreatedState`
  - Samsung target before/after: `groupFormed=true`, `isGroupOwner=true`,
    `groupOwnerIpAddress=192.168.49.1`
- sender P2P state:
  - Xiaomi sender before: `curState=GroupCreatedState`, `groupFormed=true`,
    `isGroupOwner=true`
  - Xiaomi sender after: `curState=InactiveState`, `groupFormed=false`,
    `mGroup null`

Interpretation update: both directions now reproduce the same stage failure:
pre-armed target GO remains alive, relationship/discovery are ready, but sender
does not join or form a sendable Wi-Fi Direct group. The next fix should target
sender-side join/provisioning rather than permissions, TXT discovery, or target
GO creation.

Blocking stale-reset smoke:

- `artifacts/directed-wifi-direct/20260613-210150-xiaomi-to-samsung-blocking-stale-reset/`
- `artifacts/directed-wifi-direct/20260613-210412-samsung-to-xiaomi-blocking-stale-reset/`
- local change under test: stale P2P reset now waits for `cancelConnect()` and
  `removeGroup()` completion before the sender publishes/discovers/connects
- Xiaomi -> Samsung:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `message_delivery_proven=false`
  - sender before/after: `groupFormed=false`, `mGroup null`
  - target after: `GroupCreatedState`, `groupFormed=true`,
    `groupOwnerIpAddress=192.168.49.1`
  - stage failure moved back to
    `relationship-peer-not-visible-after-fresh-discovery:Cheenya`
- Samsung -> Xiaomi:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `message_delivery_proven=false`
  - sender before: stale `GroupCreatedState`
  - sender after: `InactiveState`, `groupFormed=false`, `mGroup null`
  - target after: `GroupCreatedState`, `groupFormed=true`,
    `groupOwnerIpAddress=192.168.49.1`
  - stage failure remained
    `p2p-group-not-formed:connect=failed:1:0:0`

Interpretation update: blocking stale reset removes stale sender group state
before/after the sender run, so stale autonomous GO is no longer the primary
explanation. Delivery is still `0/2` for these fresh directed smokes. The
remaining blocker is either relationship peer visibility after reset in reverse
or Android connect/provisioning failure in the forward direction.

Post-GO service republish smoke:

- `artifacts/directed-wifi-direct/20260613-211103-xiaomi-to-samsung-republish-go-service/`
- `artifacts/directed-wifi-direct/20260613-211329-samsung-to-xiaomi-republish-go-service/`
- local change under test: debug `ensureDebugGroupOwner()` republishes the
  Kraken DNS-SD local service after confirming group-owner state
- Xiaomi -> Samsung:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `message_delivery_proven=false`
  - sender still observed `0` TXT records and `0` discovered Wi-Fi Direct peers
  - failure remained `relationship-peer-not-visible-after-fresh-discovery:Cheenya`
- Samsung -> Xiaomi:
  - `permissions_ready=true`
  - `relationship_ready=true`
  - `wifi_direct_discovery_ready=true`
  - `endpoint_bound=false`
  - `message_delivery_proven=false`
  - sender observed one accepted Xiaomi TXT record and one discovered peer
  - failure remained `p2p-group-not-formed:connect=failed:3:7:0`

Interpretation update: republishing local service after target GO creation did
not fix Samsung TXT discovery from Xiaomi, and did not change the forward
connect failure. The next useful diagnostic needs to expose why Samsung target
does not surface a Kraken TXT record to Xiaomi, or provide a directed debug
peer-address hint to bypass TXT and test connect independently.

## Next Narrow Step

Do not return to permissions. Next work should target Wi-Fi Direct group
formation and discoverability:

- investigate Android `WifiP2pManager.connect()` failure reason `0` on both
  devices;
- investigate why accepted `connect()` does not transition to `groupFormed`
  against a pre-armed target GO;
- separate "peer visible by name" from "P2P group formed" from "host resolved";
- collect/compare `dumpsys wifip2p` group/client/invitation state around the
  failing `connect=accepted:2:15` and `connect=failed:3:7:2` paths;
- optionally disable or move away from the HP Smart Tank Wi-Fi Direct device
  during validation;
- rerun Samsung -> Xiaomi and Xiaomi -> Samsung until both directions have a
  real bound endpoint before send.

## Claim Boundary

This commit improves diagnosis and prevents overclaiming. Current strict phone
evidence is `0/6` message delivery with clean permissions and unbound endpoints.
The remaining blocker is Wi-Fi Direct endpoint binding/group formation, not the
Android runtime permission model.
