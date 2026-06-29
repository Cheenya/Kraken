# Physical Inline Relay Runner

Date: `2026-06-10`.

## Result

Added `scripts/run_physical_inline_relay_trials.sh`, a repeatable harness for
physical Android A -> Mac -> Android B LAN inline relay trials.

The runner orchestrates:

- target before-capture through `scripts/capture_debug_route_evidence.sh`;
- target endpoint/fingerprint inference from the captured manifest;
- Mac relay startup through `scripts/run_android_inline_lan_relay.py`;
- sender debug-send through the Mac relay manual peer;
- target after-capture;
- one consolidated `manifest.json` under ignored
  `artifacts/physical-inline-relay/`.

Default modes are `normal/drop/duplicate/tamper`.

## Status

The runner closes the procedure gap only. It was not executed as physical attack
evidence. Samsung `R5CY22X6MSB` and Xiaomi `d948ffd0` are now both visible
through ADB, but the open blocker is the external physical path: a Mac LAN host
reachable from the sender and a direct Mac -> Android B listener path for
`normal/drop/duplicate/tamper` mode captures.

## Required Command

When the Mac LAN address is known and the Mac can reach the target Android
listener:

```bash
scripts/run_physical_inline_relay_trials.sh \
  --sender-device R5CY22X6MSB \
  --target-device d948ffd0 \
  --mac-host <MAC_LAN_IP>
```

Swap `--sender-device` / `--target-device` for the reverse direction.

## Claim Boundary

This does not close physical attack evidence by itself. A `10/10` attack claim
still requires successful before/send/relay/after artifacts for every claimed
mode on two phones, and it still would not prove production security or
cryptographic MITM resistance.
