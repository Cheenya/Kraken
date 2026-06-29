#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
BLOCKER_JSON = REPORT_DIR / "kraken_10_10_current_blocker_audit_2026-06-11.json"
RELEASE_PACKAGE_JSON = REPORT_DIR / "kraken_release_package_2026-06-11.json"
LIVE_AUDIT_JSON = REPORT_DIR / "kraken_10_10_live_audit_2026-06-11.json"
LIVE_AUDIT_MD = REPORT_DIR / "kraken_10_10_live_audit_2026-06-11.md"

REQUIRED_OPEN_GATES = {
    "wifi_direct_route_delivery",
    "wifi_direct_negative_tests",
    "physical_mac_inline_relay_modes",
    "physical_hostile_packet_injection_lan_ble_wifi_direct",
    "route_benchmark_min_n",
}
REQUIRED_PACKAGE_VALIDATION = {
    "targeted_gradle_unit_tests",
    "pytest_android_policy_guards",
    "lint_debug",
    "assemble_debug",
    "apksigner_verify",
    "apkanalyzer_manifest",
    "checksum_verify",
    "zip_integrity",
    "smoke_install_samsung",
    "smoke_launch_samsung",
    "git_diff_check",
}


@dataclass(frozen=True, slots=True)
class AdbDevice:
    serial: str
    state: str
    model: str


@dataclass(frozen=True, slots=True)
class AuditItem:
    requirement: str
    status: str
    evidence: str


@dataclass(frozen=True, slots=True)
class LiveAudit:
    complete: bool
    connected_device_count: int
    connected_devices: list[AdbDevice]
    open_gate_count: int
    items: list[AuditItem]


def run_audit(adb_devices_text: str | None = None) -> LiveAudit:
    blocker = _load_json(BLOCKER_JSON)
    package = _load_json(RELEASE_PACKAGE_JSON)
    devices = parse_adb_devices_l(adb_devices_text if adb_devices_text is not None else _adb_devices_l())
    items: list[AuditItem] = []

    _add_file_item(items, "Current blocker audit JSON", BLOCKER_JSON)
    _add_file_item(items, "Friend-test package JSON", RELEASE_PACKAGE_JSON)

    completion_allowed = blocker.get("completion_claim_allowed")
    verdict = str(blocker.get("current_10_10_verdict", "missing"))
    items.append(
        AuditItem(
            "Current blocker audit forbids false 10/10 claim",
            "complete" if completion_allowed is False and verdict == "not_complete" else "failed",
            f"completion_claim_allowed={completion_allowed}; current_10_10_verdict={verdict}",
        )
    )

    connected_count = len([device for device in devices if device.state == "device"])
    items.append(
        AuditItem(
            "At least two Android devices visible for physical route validation",
            "complete" if connected_count >= 2 else "blocked",
            f"connected_device_count={connected_count}",
        )
    )

    observed_open_gates = {
        str(gate.get("id")): str(gate.get("status"))
        for gate in blocker.get("open_gates", [])
    }
    for gate_id in sorted(REQUIRED_OPEN_GATES):
        status = observed_open_gates.get(gate_id, "missing")
        items.append(
            AuditItem(
                f"Required still-open gate is tracked: {gate_id}",
                "complete" if status == "open" else "failed",
                f"status={status}",
            )
        )

    validation = package.get("validation", {})
    for validation_id in sorted(REQUIRED_PACKAGE_VALIDATION):
        value = str(validation.get(validation_id, "missing"))
        items.append(
            AuditItem(
                f"Friend-test package validation recorded: {validation_id}",
                "complete" if value.startswith("passed") or value.endswith("passed") else "failed",
                value,
            )
        )

    commands = blocker.get("next_commands_when_xiaomi_and_mac_are_available", {})
    for command_name, command in sorted(commands.items()):
        script_name = str(command).split()[0] if command else ""
        script_path = ROOT / script_name if script_name else ROOT / "__missing__"
        items.append(
            AuditItem(
                f"Next-step command script exists: {command_name}",
                "complete" if script_path.exists() else "missing",
                str(command),
            )
        )

    open_gate_count = sum(1 for gate in blocker.get("open_gates", []) if gate.get("status") == "open")
    complete = all(item.status == "complete" for item in items) and open_gate_count == 0
    return LiveAudit(
        complete=complete,
        connected_device_count=connected_count,
        connected_devices=devices,
        open_gate_count=open_gate_count,
        items=items,
    )


def write_audit(audit: LiveAudit) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    LIVE_AUDIT_JSON.write_text(
        json.dumps(_audit_to_dict(audit), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    LIVE_AUDIT_MD.write_text(_audit_to_markdown(audit), encoding="utf-8")


def parse_adb_devices_l(text: str) -> list[AdbDevice]:
    devices: list[AdbDevice] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        model_match = re.search(r"\bmodel:([^\s]+)", line)
        devices.append(
            AdbDevice(
                serial=parts[0],
                state=parts[1],
                model=model_match.group(1) if model_match else "unknown",
            )
        )
    return devices


def _adb_devices_l() -> str:
    try:
        return subprocess.check_output(
            ["adb", "devices", "-l"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (OSError, subprocess.CalledProcessError):
        return ""


def _load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _add_file_item(items: list[AuditItem], requirement: str, path: Path) -> None:
    items.append(
        AuditItem(
            requirement=requirement,
            status="complete" if path.exists() and path.stat().st_size > 0 else "missing",
            evidence=str(path.relative_to(ROOT)),
        )
    )


def _audit_to_dict(audit: LiveAudit) -> dict[str, Any]:
    return {
        "report": "kraken_10_10_live_audit",
        "complete": audit.complete,
        "connected_device_count": audit.connected_device_count,
        "connected_devices": [asdict(device) for device in audit.connected_devices],
        "open_gate_count": audit.open_gate_count,
        "items": [asdict(item) for item in audit.items],
        "claim_boundary": (
            "Live/current-state audit only. complete=false is expected until two-device "
            "Wi-Fi Direct, physical attack, route benchmark and any in-scope production "
            "security gates are proven by current evidence."
        ),
    }


def _audit_to_markdown(audit: LiveAudit) -> str:
    lines = [
        "# Kraken 10/10 Live Audit",
        "",
        f"Complete: `{str(audit.complete).lower()}`",
        f"Connected device count: `{audit.connected_device_count}`",
        f"Open gate count: `{audit.open_gate_count}`",
        "",
        "## Connected Devices",
        "",
        "| Serial | State | Model |",
        "| --- | --- | --- |",
    ]
    for device in audit.connected_devices:
        lines.append(f"| `{device.serial}` | `{device.state}` | `{device.model}` |")
    lines.extend(
        [
            "",
            "## Items",
            "",
            "| Requirement | Status | Evidence |",
            "| --- | --- | --- |",
        ]
    )
    for item in audit.items:
        evidence = item.evidence.replace("|", "\\|")
        lines.append(f"| {item.requirement} | `{item.status}` | `{evidence}` |")
    lines.extend(
        [
            "",
            "## Boundary",
            "",
            "This is a live/current-state audit only. It does not close two-device",
            "Wi-Fi Direct, physical attack, route benchmark or production security",
            "gates; it records whether current evidence is sufficient to claim closure.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    audit = run_audit()
    write_audit(audit)
    print(LIVE_AUDIT_JSON)
    return 0 if audit.complete else 2


if __name__ == "__main__":
    raise SystemExit(main())
