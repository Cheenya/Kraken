# Multi-Transport Mesh Roadmap

Status: roadmap plus current prototype inventory. Wi-Fi Direct, LAN NSD + TCP
and BLE GATT are implemented as research prototype transports. LAN/BLE have
manual Samsung/Xiaomi evidence; Wi-Fi Direct still requires phone route evidence
and negative tests before any `10/10` claim.

## Implemented Transport

| Transport | Status | Notes |
| --- | --- | --- |
| Wi-Fi Direct | implemented prototype; evidence pending | `WifiP2pManager` + DNS-SD TXT identity + socket frame path; requires two-phone route/negative evidence |
| LAN NSD + TCP | implemented prototype | local Wi-Fi/LAN only, QR trust still required; manual Samsung/Xiaomi smoke captured |
| BLE GATT | implemented prototype | Android 12+ permissions, advertiser/scanner/GATT server/client and chunk codec exist; manual Samsung/Xiaomi direct-route evidence and 2026-06-08 route-specific counters captured; physical attack/rejection cases and reliability benchmark remain open |

## Planned Transports

| Transport | Status | Why not now |
| --- | --- | --- |
| Nearby Connections | explicit approval required | Google dependency and privacy tradeoff |
| Manual QR packet transfer | fallback idea | not radio P2P |

## Capability Model

Android code uses `TransportCapability`:

- `DISCOVERY`;
- `DIRECT_SEND`;
- `RELAY`;
- `LOW_BANDWIDTH`;
- `HIGH_BANDWIDTH`;
- `REQUIRES_PERMISSION`;
- `REQUIRES_USER_ACTION`.

## Product Rules

- no cloud relay;
- no server-scale architecture;
- no public discovery;
- no account/login/phone/email identity;
- UI exposes only implemented transport modes with route caveats;
- roadmap items must not look enabled;
- Wi-Fi Direct is a required P0 transport for any honest `10/10` claim.

## Next Decision

LAN direct messaging and BLE direct-route messaging have manual two-phone
prototype evidence. Next transport work should capture Wi-Fi Direct two-phone
route evidence, add route-specific negative tests, and then run latency/loss
metrics across Wi-Fi Direct, LAN and BLE.
