#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


DEFAULT_DEVELOPER_DIR = "/Applications/Xcode.app/Contents/Developer"
DEFAULT_OUTPUT_ROOT = "artifacts/ios-device-validation"


@dataclass(frozen=True)
class CommandResult:
    command: list[str]
    exitCode: int
    stdout: str
    stderr: str


@dataclass(frozen=True)
class ValidationGate:
    id: str
    title: str
    status: str
    requiredEvidence: list[str]
    passCriteria: list[str]
    artifactPaths: list[str]
    verifiedAtUtc: str | None
    verdictNotes: list[str]


def run(command: list[str], *, env: dict[str, str] | None = None) -> CommandResult:
    merged_env = os.environ.copy()
    if env:
        merged_env.update(env)
    completed = subprocess.run(command, env=merged_env, text=True, capture_output=True, check=False)
    return CommandResult(
        command=command,
        exitCode=completed.returncode,
        stdout=completed.stdout,
        stderr=completed.stderr,
    )


def parse_xctrace_devices(output: str) -> dict[str, list[str]]:
    devices: list[str] = []
    simulators: list[str] = []
    current: list[str] | None = None
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if line == "== Devices ==":
            current = devices
            continue
        if line == "== Simulators ==":
            current = simulators
            continue
        if not line or current is None:
            continue
        current.append(line)
    return {"devices": devices, "simulators": simulators}


def parse_adb_devices(output: str) -> list[str]:
    devices: list[str] = []
    for line in output.splitlines()[1:]:
        stripped = line.strip()
        if stripped:
            devices.append(stripped)
    return devices


def default_gates() -> list[ValidationGate]:
    return [
        ValidationGate(
            id="ios-multipeer-two-device",
            title="Two real iPhone/iPad MultipeerConnectivity exchange",
            status="not_run",
            requiredEvidence=[
                "Two physical iOS/iPadOS device identifiers from xctrace or Xcode.",
                "Kraken iOS installed and launched on both devices.",
                "Screenshots or exported evidence JSON showing discovery, active relationship, message send, inbound message, ACK.",
                "Negative proof that an unresolved peer cannot mutate timeline.",
            ],
            passCriteria=[
                "Both devices discover each other over local Apple transport.",
                "A trusted active relationship is required before timeline mutation.",
                "A kraken.ios.packet.v1 message reaches the peer and ACK marks outbox delivered.",
                "Wrong relationship_id or peer_fingerprint is rejected.",
            ],
            artifactPaths=[],
            verifiedAtUtc=None,
            verdictNotes=[],
        ),
        ValidationGate(
            id="android-ios-qr-handshake",
            title="Android invite -> iOS response -> Android confirmation interop",
            status="not_run",
            requiredEvidence=[
                "Android serial from adb and physical iOS device identifier.",
                "Android invite QR data or screenshot.",
                "iOS generated response QR data or screenshot.",
                "Android confirmation QR data or screenshot.",
                "Final active relationship evidence on both platforms.",
            ],
            passCriteria=[
                "iOS imports Android invite and creates pending relationship.",
                "iOS response QR carries matching invite_id, response_id and responder_fingerprint.",
                "iOS activates only after matching confirmation.",
                "Mismatched response_id or responder_fingerprint does not activate the relationship.",
            ],
            artifactPaths=[],
            verifiedAtUtc=None,
            verdictNotes=[],
        ),
        ValidationGate(
            id="android-ios-packet-negative-policy",
            title="Android/iOS packet policy negative cases on devices",
            status="not_run",
            requiredEvidence=[
                "Device logs or exported evidence for expired TTL, duplicate packet, wrong recipient, wrong sender and wrong relationship id.",
                "Before/after timeline counts for each negative case.",
            ],
            passCriteria=[
                "Packet policy runs before timeline mutation.",
                "Each malformed or untrusted packet is rejected with no new message.",
                "Accepted trusted packet still works after negative cases.",
            ],
            artifactPaths=[],
            verifiedAtUtc=None,
            verdictNotes=[],
        ),
        ValidationGate(
            id="ios-persistence-lifecycle",
            title="Real device persistence across app lifecycle",
            status="not_run",
            requiredEvidence=[
                "Evidence before app termination: identity, pending relationship, active relationship, outbox failure and diagnostics.",
                "Evidence after force quit and relaunch.",
            ],
            passCriteria=[
                "Application Support JSON restores identity and selected relationship.",
                "Pending and active relationships survive relaunch.",
                "Outbox and diagnostics survive relaunch without demo mode.",
            ],
            artifactPaths=[],
            verifiedAtUtc=None,
            verdictNotes=[],
        ),
        ValidationGate(
            id="ios-physical-visual-review",
            title="Physical iPhone/iPad visual UI review",
            status="not_run",
            requiredEvidence=[
                "Photos or screenshots of launch, welcome, chats, contacts, QR states, realms and settings on a physical iPhone.",
                "At least one iPad visual review if iPad support is claimed beyond orientation metadata.",
            ],
            passCriteria=[
                "Launch and welcome match current Kraken assets and copy.",
                "Native bottom tab bar does not overlap bottom content.",
                "Headers and controls remain readable without oversized layout.",
                "Liquid Glass/native iPhone chrome is preserved on supported OS.",
            ],
            artifactPaths=[],
            verifiedAtUtc=None,
            verdictNotes=[],
        ),
    ]


def write_runbook(output_dir: Path, evidence_name: str) -> None:
    text = f"""# Kraken iOS Device Validation Runbook

This packet is a placeholder for physical-device evidence. It does not claim
that the gates passed until `evidence.json` is filled with real run data.

Evidence template: `{evidence_name}`

## Preflight

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun xctrace list devices
adb devices -l
python3 app-ios/Scripts/verify_ios_smoke.py
```

## Build For A Real iOS Device

Signing must be configured locally in Xcode before real-device install.

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \\
xcodebuild build \\
  -project app-ios/KrakenIOS.xcodeproj \\
  -scheme KrakenIOS \\
  -destination 'id=<IOS_DEVICE_UDID>' \\
  -derivedDataPath app-ios/DerivedData
```

## Required Gates

1. `ios-multipeer-two-device`: two real iPhone/iPad devices exchange a trusted
   `kraken.ios.packet.v1` message and ACK; unresolved or wrong-fingerprint peers
   do not mutate the timeline.
2. `android-ios-qr-handshake`: Android invite -> iOS response -> Android
   confirmation reaches active relationship on both platforms.
3. `android-ios-packet-negative-policy`: expired, duplicate, wrong-recipient,
   wrong-sender and wrong-relationship packets are rejected on device.
4. `ios-persistence-lifecycle`: identity, pending/active relationships, outbox
   and diagnostics survive force quit and relaunch.
5. `ios-physical-visual-review`: launch, welcome, tabs, QR states and long
   screens are visually reviewed on physical iPhone, and iPad if claimed.

## Boundary

iOS transport is local Apple transport via MultipeerConnectivity. Do not
record this as Android Wi-Fi Direct parity.

## Completion Evidence Schema

Only mark a gate as passed after filling all of these fields in `evidence.json`:

- `status`: `passed`
- `verifiedAtUtc`: ISO-8601 timestamp for the physical run
- `artifactPaths`: non-empty repo-relative paths that exist in this checkout and
  point to screenshots, logs or exported evidence JSON from that gate
- `verdictNotes`: non-empty notes tying the artifacts to the pass criteria

The readiness audit treats physical validation as pending until every required
gate has `status: "passed"`, a valid timestamp, existing artifact paths and
verdict notes.
"""
    (output_dir / "runbook.md").write_text(text, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare a Kraken iOS physical-device validation evidence packet.")
    parser.add_argument("--developer-dir", default=DEFAULT_DEVELOPER_DIR)
    parser.add_argument("--output-root", default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--label", default=None, help="Optional output directory suffix.")
    parser.add_argument("--ios-device", action="append", default=[], help="Expected physical iOS/iPadOS device UDID. Repeat for two-device runs.")
    parser.add_argument("--android-serial", action="append", default=[], help="Expected Android adb serial. Repeat if needed.")
    parser.add_argument("--require-ios-count", type=int, default=0)
    parser.add_argument("--require-android-count", type=int, default=0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    suffix = f"-{args.label}" if args.label else ""
    output_dir = repo_root / args.output_root / f"{stamp}{suffix}"
    output_dir.mkdir(parents=True, exist_ok=True)

    xctrace = run(
        ["xcrun", "xctrace", "list", "devices"],
        env={"DEVELOPER_DIR": args.developer_dir},
    )
    adb = run(["adb", "devices", "-l"])
    xcode_devices = parse_xctrace_devices(xctrace.stdout) if xctrace.exitCode == 0 else {"devices": [], "simulators": []}
    android_devices = parse_adb_devices(adb.stdout) if adb.exitCode == 0 else []

    evidence = {
        "version": 1,
        "createdAtUtc": datetime.now(timezone.utc).isoformat(),
        "status": "template_not_run",
        "branch": "codex/kraken-ios",
        "commands": {
            "xctraceListDevices": asdict(xctrace),
            "adbDevices": asdict(adb),
        },
        "inventory": {
            "xcodeDevices": xcode_devices["devices"],
            "xcodeSimulators": xcode_devices["simulators"],
            "androidDevices": android_devices,
            "expectedIosDevices": args.ios_device,
            "expectedAndroidSerials": args.android_serial,
        },
        "requirements": {
            "requireIosCount": args.require_ios_count,
            "requireAndroidCount": args.require_android_count,
        },
        "gates": [asdict(gate) for gate in default_gates()],
        "notes": [
            "Fill each gate with real artifact paths, timestamps and pass/fail verdicts after a physical run.",
            "Do not mark the iOS port complete from this template alone.",
            "Do not claim Android Wi-Fi Direct parity for iOS MultipeerConnectivity evidence.",
        ],
    }

    physical_ios_count = sum(1 for line in xcode_devices["devices"] if "Mac" not in line)
    android_count = len(android_devices)
    missing: list[str] = []
    if args.require_ios_count and physical_ios_count < args.require_ios_count:
        missing.append("ios_devices")
    if args.require_android_count and android_count < args.require_android_count:
        missing.append("android_devices")
    if missing:
        evidence["status"] = "blocked_missing_" + "_and_".join(missing)

    evidence_path = output_dir / "evidence.json"
    evidence_path.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_runbook(output_dir, evidence_path.name)
    print(output_dir.relative_to(repo_root))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
