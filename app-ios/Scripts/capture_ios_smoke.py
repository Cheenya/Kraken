#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from render_ios_launch_reference import OUTPUT_NAME as LAUNCH_REFERENCE_NAME
from render_ios_launch_reference import composite_launch_reference
from verify_ios_smoke import has_bottom_tab_chrome
from verify_ios_smoke import has_tablet_top_tab_chrome


DEFAULT_PRIMARY_SIM = "auto"
DEFAULT_COMPACT_SIM = "auto"
DEFAULT_TABLET_SIM = "auto"
DEFAULT_DEVELOPER_DIR = "/Applications/Xcode.app/Contents/Developer"
BUNDLE_ID = "local.kraken.ios"
PRIMARY_DEVICE_NAME = "iPhone 17"
COMPACT_DEVICE_NAME = "Kraken iOS Compact SE"
COMPACT_DEVICE_TYPE = "com.apple.CoreSimulator.SimDeviceType.iPhone-SE-3rd-generation"
TABLET_DEVICE_NAME = "iPad (A16)"
RUNTIME_ID = "com.apple.CoreSimulator.SimRuntime.iOS-26-5"


@dataclass(frozen=True)
class SmokeScenario:
    file_name: str
    simulator: str
    tab: str | None = None
    qr: str | None = None
    scroll: str | None = None
    skip_welcome: bool = True
    extra_delay: float = 0.0


PRIMARY_SCENARIOS = [
    SmokeScenario("kraken-ios-native-welcome.png", simulator="primary", skip_welcome=False, extra_delay=2.0),
    SmokeScenario("kraken-ios-native-home.png", simulator="primary", tab="home"),
    SmokeScenario("kraken-ios-native-contacts.png", simulator="primary", tab="contacts"),
    SmokeScenario("kraken-ios-native-contacts-bottom.png", simulator="primary", tab="contacts", scroll="bottom"),
    SmokeScenario("kraken-ios-native-contacts-qr-invite.png", simulator="primary", tab="contacts", qr="invite"),
    SmokeScenario("kraken-ios-native-contacts-qr-response.png", simulator="primary", tab="contacts", qr="response"),
    SmokeScenario("kraken-ios-native-contacts-qr-response-bottom.png", simulator="primary", tab="contacts", qr="response", scroll="bottom"),
    SmokeScenario("kraken-ios-native-contacts-qr-confirmation.png", simulator="primary", tab="contacts", qr="confirmation"),
    SmokeScenario("kraken-ios-native-realms.png", simulator="primary", tab="realms"),
    SmokeScenario("kraken-ios-native-realms-bottom.png", simulator="primary", tab="realms", scroll="bottom"),
    SmokeScenario("kraken-ios-native-settings.png", simulator="primary", tab="settings"),
    SmokeScenario("kraken-ios-native-settings-bottom.png", simulator="primary", tab="settings", scroll="bottom"),
]

COMPACT_SCENARIOS = [
    SmokeScenario("kraken-ios-compact-welcome.png", simulator="compact", skip_welcome=False, extra_delay=3.0),
    SmokeScenario("kraken-ios-compact-contacts-qr-response-bottom.png", simulator="compact", tab="contacts", qr="response", scroll="bottom"),
    SmokeScenario("kraken-ios-compact-settings-bottom.png", simulator="compact", tab="settings", scroll="bottom"),
]

TABLET_SCENARIOS = [
    SmokeScenario("kraken-ios-tablet-welcome.png", simulator="tablet", skip_welcome=False, extra_delay=3.0),
    SmokeScenario("kraken-ios-tablet-settings-bottom.png", simulator="tablet", tab="settings", scroll="bottom"),
]

STALE_EXCLUDED_PATTERNS = [
    "kraken-ios-android-",
    "kraken-ios-chat",
    "kraken-ios-native-chats.png",
    "kraken-ios-launch.png",
    "kraken-ios-native-launch.png",
    "tmp-",
]


def run(
    command: list[str],
    *,
    env: dict[str, str] | None = None,
    check: bool = True,
    quiet: bool = False,
) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(command), flush=True)
    if quiet:
        return subprocess.run(
            command,
            env=env,
            check=check,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            text=True,
        )
    return subprocess.run(command, env=env, check=check, text=True)


def xcrun(
    developer_dir: str,
    args: list[str],
    *,
    env: dict[str, str] | None = None,
    check: bool = True,
    quiet: bool = False,
) -> subprocess.CompletedProcess[str]:
    merged_env = os.environ.copy()
    merged_env["DEVELOPER_DIR"] = developer_dir
    if env:
        merged_env.update(env)
    return run(["xcrun", *args], env=merged_env, check=check, quiet=quiet)


def xcrun_output(developer_dir: str, args: list[str]) -> str:
    merged_env = os.environ.copy()
    merged_env["DEVELOPER_DIR"] = developer_dir
    return subprocess.check_output(["xcrun", *args], env=merged_env, text=True)


def available_devices(developer_dir: str) -> list[dict[str, object]]:
    raw = xcrun_output(developer_dir, ["simctl", "list", "-j", "devices", "available"])
    devices_by_runtime = json.loads(raw).get("devices", {})
    return list(devices_by_runtime.get(RUNTIME_ID, []))


def resolve_simulator_id(developer_dir: str, requested: str, role: str) -> str:
    if requested != "auto":
        return requested

    devices = available_devices(developer_dir)
    if role == "primary":
        for device in devices:
            if device.get("name") == PRIMARY_DEVICE_NAME:
                return str(device["udid"])
        raise RuntimeError(f"Could not find {PRIMARY_DEVICE_NAME} on {RUNTIME_ID}; pass --primary-sim <UDID>")

    if role == "tablet":
        for device in devices:
            if device.get("name") == TABLET_DEVICE_NAME:
                return str(device["udid"])
        raise RuntimeError(f"Could not find {TABLET_DEVICE_NAME} on {RUNTIME_ID}; pass --tablet-sim <UDID>")

    for device in devices:
        if device.get("name") == COMPACT_DEVICE_NAME:
            return str(device["udid"])

    created = xcrun_output(
        developer_dir,
        [
            "simctl",
            "create",
            COMPACT_DEVICE_NAME,
            COMPACT_DEVICE_TYPE,
            RUNTIME_ID,
        ],
    ).strip()
    if not created:
        raise RuntimeError("Could not create compact iPhone SE simulator")
    return created


def build_app(repo_root: Path, developer_dir: str, simulator_id: str) -> None:
    run(
        [
            "xcodebuild",
            "build",
            "-project",
            str(repo_root / "app-ios/KrakenIOS.xcodeproj"),
            "-scheme",
            "KrakenIOS",
            "-destination",
            f"id={simulator_id}",
            "-derivedDataPath",
            str(repo_root / "app-ios/DerivedData"),
            "-quiet",
        ],
        env={**os.environ, "DEVELOPER_DIR": developer_dir},
    )


def boot_and_install(repo_root: Path, developer_dir: str, simulator_id: str) -> None:
    app_path = repo_root / "app-ios/DerivedData/Build/Products/Debug-iphonesimulator/KrakenIOS.app"
    xcrun(developer_dir, ["simctl", "boot", simulator_id], check=False, quiet=True)
    xcrun(developer_dir, ["simctl", "bootstatus", simulator_id, "-b"])
    xcrun(developer_dir, ["simctl", "install", simulator_id, str(app_path)])


def launch_environment(scenario: SmokeScenario) -> dict[str, str]:
    env = {"SIMCTL_CHILD_KRAKEN_DEMO_MODE": "1"}
    if scenario.skip_welcome:
        env["SIMCTL_CHILD_KRAKEN_SKIP_WELCOME"] = "1"
    if scenario.tab:
        env["SIMCTL_CHILD_KRAKEN_TAB"] = scenario.tab
    if scenario.qr:
        env["SIMCTL_CHILD_KRAKEN_QR"] = scenario.qr
    if scenario.scroll:
        env["SIMCTL_CHILD_KRAKEN_SCROLL"] = scenario.scroll
    return env


def capture_scenario(repo_root: Path, developer_dir: str, simulator_id: str, scenario: SmokeScenario, delay: float) -> None:
    output_path = repo_root / "artifacts/ios-smoke" / scenario.file_name
    output_path.parent.mkdir(parents=True, exist_ok=True)
    xcrun(developer_dir, ["simctl", "terminate", simulator_id, BUNDLE_ID], check=False, quiet=True)
    xcrun(developer_dir, ["simctl", "launch", simulator_id, BUNDLE_ID], env=launch_environment(scenario))
    attempts = 1 if not scenario.skip_welcome else 5
    for attempt in range(1, attempts + 1):
        time.sleep(delay if attempt == 1 else 2.0)
        xcrun(developer_dir, ["simctl", "io", simulator_id, "screenshot", str(output_path)])
        if not scenario.skip_welcome:
            return
        rendered = has_tablet_top_tab_chrome(output_path) if scenario.simulator == "tablet" else has_bottom_tab_chrome(output_path)
        if rendered:
            return
        print(f"Retrying {scenario.file_name}: screenshot still looks like an early launch frame", flush=True)
    raise RuntimeError(f"{scenario.file_name} did not render expected native tab chrome after {attempts} screenshots")


def write_current_manifest(repo_root: Path) -> None:
    smoke_dir = repo_root / "artifacts/ios-smoke"
    all_scenarios = [*PRIMARY_SCENARIOS, *COMPACT_SCENARIOS, *TABLET_SCENARIOS]
    current = [scenario.file_name for scenario in all_scenarios]
    manifest = {
        "version": 1,
        "evidenceSet": "kraken-ios-current-smoke",
        "generatedBy": "app-ios/Scripts/capture_ios_smoke.py",
        "currentScreenshots": current,
        "launchReference": {
            "file": LAUNCH_REFERENCE_NAME,
            "source": "UILaunchScreen BrandBackground + LaunchGlyph assets",
            "pixels": [1170, 2532],
            "note": "Reference image, not a Simulator launch screenshot.",
        },
        "compactViewport": {
            "device": "iPhone SE (3rd generation)",
            "expectedPixels": [750, 1334],
            "screenshots": [scenario.file_name for scenario in all_scenarios if scenario.simulator == "compact"],
        },
        "tabletViewport": {
            "device": TABLET_DEVICE_NAME,
            "runtime": RUNTIME_ID,
            "screenshots": [scenario.file_name for scenario in all_scenarios if scenario.simulator == "tablet"],
            "note": "Simulator evidence only; physical iPad review remains a device gate.",
        },
        "staleExcludedPatterns": STALE_EXCLUDED_PATTERNS,
        "notes": [
            "Only currentScreenshots are current iOS UI evidence.",
            "Older kraken-ios-android-* and kraken-ios-chat* captures are stale comparison files.",
            "tmp-* files are manual debug captures and are not current UI evidence.",
            "Launch screen evidence is verified structurally through UILaunchScreen and LaunchGlyph assets.",
            "Non-welcome screenshots are pixel-checked so early LaunchGlyph frames cannot pass as tab evidence.",
        ],
    }
    (smoke_dir / "current-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Capture current Kraken iOS smoke screenshots.")
    parser.add_argument("--primary-sim", default=DEFAULT_PRIMARY_SIM, help="Simulator UDID for full native screenshot set, or auto.")
    parser.add_argument("--compact-sim", default=DEFAULT_COMPACT_SIM, help="Simulator UDID for compact iPhone SE viewport screenshot set, or auto.")
    parser.add_argument("--tablet-sim", default=DEFAULT_TABLET_SIM, help="Simulator UDID for iPad screenshot set, or auto.")
    parser.add_argument("--developer-dir", default=DEFAULT_DEVELOPER_DIR)
    parser.add_argument("--skip-build", action="store_true", help="Reuse app-ios/DerivedData build output.")
    parser.add_argument("--delay", type=float, default=2.0, help="Seconds to wait after launch before screenshot.")
    parser.add_argument("--compact-delay", type=float, default=3.5, help="Seconds to wait for compact captures.")
    parser.add_argument("--tablet-delay", type=float, default=3.5, help="Seconds to wait for iPad captures.")
    parser.add_argument("--only", choices=["all", "primary", "compact", "tablet"], default="all")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    scenarios: list[SmokeScenario] = []
    if args.only in {"all", "primary"}:
        scenarios.extend(PRIMARY_SCENARIOS)
    if args.only in {"all", "compact"}:
        scenarios.extend(COMPACT_SCENARIOS)
    if args.only in {"all", "tablet"}:
        scenarios.extend(TABLET_SCENARIOS)

    primary_sim = (
        resolve_simulator_id(args.developer_dir, args.primary_sim, "primary")
        if args.only in {"all", "primary"}
        else ""
    )
    compact_sim = (
        resolve_simulator_id(args.developer_dir, args.compact_sim, "compact")
        if args.only in {"all", "compact"}
        else ""
    )
    tablet_sim = (
        resolve_simulator_id(args.developer_dir, args.tablet_sim, "tablet")
        if args.only in {"all", "tablet"}
        else ""
    )

    if not args.skip_build:
        build_app(repo_root, args.developer_dir, next(sim for sim in [primary_sim, compact_sim, tablet_sim] if sim))

    simulator_ids = {
        "primary": primary_sim,
        "compact": compact_sim,
        "tablet": tablet_sim,
    }
    for simulator_id in sorted({simulator_ids[scenario.simulator] for scenario in scenarios}):
        boot_and_install(repo_root, args.developer_dir, simulator_id)

    for scenario in scenarios:
        simulator_id = simulator_ids[scenario.simulator]
        if scenario.simulator == "compact":
            base_delay = args.compact_delay
        elif scenario.simulator == "tablet":
            base_delay = args.tablet_delay
        else:
            base_delay = args.delay
        delay = base_delay + scenario.extra_delay
        capture_scenario(repo_root, args.developer_dir, simulator_id, scenario, delay)

    composite_launch_reference(repo_root)
    write_current_manifest(repo_root)
    print(f"Captured {len(scenarios)} iOS smoke screenshots in artifacts/ios-smoke")
    return 0


if __name__ == "__main__":
    sys.exit(main())
