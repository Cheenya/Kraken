#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


REQUIRED_RELAY_MODES = {"normal", "drop", "duplicate", "tamper"}


@dataclass(frozen=True, slots=True)
class AuditItem:
    requirement: str
    status: str
    evidence: str


@dataclass(frozen=True, slots=True)
class ClosureSuiteAudit:
    complete: bool
    suite_dir: str
    items: list[AuditItem]


def run_audit(suite_dir: Path) -> ClosureSuiteAudit:
    suite_dir = suite_dir.resolve()
    items: list[AuditItem] = []
    suite_manifest_path = suite_dir / "manifest.json"
    suite_manifest = _load_json(suite_manifest_path)

    _add_file_item(items, "Closure suite manifest", suite_manifest_path)
    _add_file_item(items, "ADB device snapshot", suite_dir / "adb_devices.txt")
    _add_file_item(items, "Git status snapshot", suite_dir / "git_status.txt")

    for key, direction in [
        ("wifi_direct_forward_dir", "sender_to_target"),
        ("wifi_direct_reverse_dir", "target_to_sender"),
    ]:
        capture_dir = suite_dir / str(suite_manifest.get(key, ""))
        capture_manifest = _load_json(capture_dir / "manifest.json")
        status, evidence = _wifi_direct_status(capture_manifest)
        items.append(
            AuditItem(
                f"Wi-Fi Direct {direction} directed counter evidence captured",
                status,
                f"{evidence}; manifest={capture_dir / 'manifest.json'}",
            )
        )

    benchmark_dir = suite_dir / str(suite_manifest.get("route_benchmark_dir", ""))
    benchmark_manifest = _load_json(benchmark_dir / "manifest.json")
    benchmark_status = str(benchmark_manifest.get("overall_status", "missing"))
    items.append(
        AuditItem(
            "Route benchmark minimum sample gate passed",
            "complete" if benchmark_status == "passed_min_sample_gate" else "failed",
            f"overall_status={benchmark_status}; manifest={benchmark_dir / 'manifest.json'}",
        )
    )

    relay_dir = suite_dir / str(suite_manifest.get("physical_relay_dir", ""))
    relay_manifest = _load_json(relay_dir / "manifest.json")
    modes = relay_manifest.get("modes", [])
    modes_by_name = {str(mode.get("mode")): mode for mode in modes}
    for mode in sorted(REQUIRED_RELAY_MODES):
        mode_payload = modes_by_name.get(mode, {})
        relay_status, relay_evidence = _relay_mode_status(mode, mode_payload)
        items.append(
            AuditItem(
                f"Physical inline relay mode evidence passed: {mode}",
                relay_status,
                f"{relay_evidence}; manifest={relay_dir / 'manifest.json'}",
            )
        )

    complete = all(item.status == "complete" for item in items)
    return ClosureSuiteAudit(
        complete=complete,
        suite_dir=str(suite_dir),
        items=items,
    )


def write_audit(audit: ClosureSuiteAudit, out_json: Path, out_md: Path) -> None:
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_md.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(_audit_to_dict(audit), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    out_md.write_text(_audit_to_markdown(audit), encoding="utf-8")


def _load_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def _relay_mode_status(mode: str, payload: dict[str, Any]) -> tuple[str, str]:
    frames_received = int(payload.get("frames_received") or 0)
    decision = payload.get("relay_decision") or {}
    stats = payload.get("relay_stats") or {}
    attempts = payload.get("forward_attempts") or []
    target_before_port = _optional_int(payload.get("target_before_local_port"))
    target_after_port = _optional_int(payload.get("target_after_local_port"))
    relay_target_port = _optional_int(payload.get("relay_target_port"))
    target_before_inbound = int(payload.get("target_before_inbound_packets") or 0)
    target_after_inbound = int(payload.get("target_after_inbound_packets") or 0)
    target_before_malformed = int(payload.get("target_before_malformed_frames_dropped") or 0)
    target_after_malformed = int(payload.get("target_after_malformed_frames_dropped") or 0)
    successful_forwards = [attempt for attempt in attempts if attempt.get("success") is True]
    failed_forwards = [attempt for attempt in attempts if attempt.get("success") is False]
    target_port_stable = target_before_port is not None and target_before_port == target_after_port
    relay_port_matches = relay_target_port is not None and relay_target_port == target_before_port

    common = (
        f"frames_received={frames_received}; relay_decision={decision}; "
        f"target_before_port={target_before_port}; relay_target_port={relay_target_port}; "
        f"target_after_port={target_after_port}; "
        f"target_inbound_delta={target_after_inbound - target_before_inbound}; "
        f"target_malformed_delta={target_after_malformed - target_before_malformed}; "
        f"successful_forwards={len(successful_forwards)}; failed_forwards={len(failed_forwards)}"
    )
    if frames_received < 1 or not decision:
        return "failed", common

    if mode == "normal":
        passed = target_port_stable and relay_port_matches and bool(successful_forwards) and target_after_inbound > target_before_inbound
        return ("complete" if passed else "failed"), common
    if mode == "drop":
        passed = target_port_stable and int(stats.get("dropped") or 0) >= 1 and not attempts and target_after_inbound == target_before_inbound
        return ("complete" if passed else "failed"), common
    if mode == "duplicate":
        passed = (
            target_port_stable and relay_port_matches and int(stats.get("duplicated") or 0) >= 1
            and len(successful_forwards) >= 2 and target_after_inbound > target_before_inbound
        )
        return ("complete" if passed else "failed"), common
    if mode == "tamper":
        passed = (
            target_port_stable and relay_port_matches and int(stats.get("tampered") or 0) >= 1
            and bool(successful_forwards) and target_after_malformed > target_before_malformed
        )
        return ("complete" if passed else "failed"), common

    return "failed", common


def _wifi_direct_status(capture_manifest: dict[str, Any]) -> tuple[str, str]:
    if capture_manifest.get("report_version") == "kraken.directed_wifi_direct_route_trial.v1":
        verdict = capture_manifest.get("verdict") or {}
        sender = capture_manifest.get("sender") or {}
        target = capture_manifest.get("target") or {}
        deltas = (target.get("deltas") or {}) if isinstance(target, dict) else {}
        sender_success = sender.get("debug_send_success") is True
        transport_counter_delivery_observed = verdict.get("transport_counter_delivery_observed") is True
        message_delivery_proven = verdict.get("message_delivery_proven") is True
        evidence = (
            f"sender_success={sender_success}; "
            f"target_inbound_delta={deltas.get('inbound_packets')}; "
            f"target_accepted_delta={deltas.get('accepted_connections')}; "
            f"message_delivery_proven={message_delivery_proven}"
        )
        passed = sender_success and transport_counter_delivery_observed
        return ("complete" if passed else "failed"), evidence

    devices = capture_manifest.get("devices", [])
    delivered = [
        device
        for device in devices
        if device.get("selected_route") == "wifi-direct"
        and (device.get("command_result") or {}).get("debug_send_success") is True
    ]
    return (
        "complete" if len(devices) >= 2 and delivered else "failed",
        f"legacy_devices={len(devices)}; legacy_sender_successes={len(delivered)}",
    )


def _optional_int(value: Any) -> int | None:
    if value is None:
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _add_file_item(items: list[AuditItem], requirement: str, path: Path) -> None:
    items.append(
        AuditItem(
            requirement=requirement,
            status="complete" if path.exists() and path.stat().st_size > 0 else "missing",
            evidence=str(path),
        )
    )


def _audit_to_dict(audit: ClosureSuiteAudit) -> dict[str, Any]:
    return {
        "report": "kraken_10_10_closure_suite_output_audit",
        "complete": audit.complete,
        "suite_dir": audit.suite_dir,
        "items": [asdict(item) for item in audit.items],
        "claim_boundary": (
            "This verifies one closure-suite output directory only. complete=true requires "
            "bidirectional Wi-Fi Direct directed counter evidence, route benchmark sample gate success and "
            "physical inline relay evidence for normal/drop/duplicate/tamper modes, including "
            "a successful normal forward and attack-specific drop/duplicate/tamper effects. It is "
            "still not message-id-bound Wi-Fi Direct delivery proof or a production cryptographic security review."
        ),
    }


def _audit_to_markdown(audit: ClosureSuiteAudit) -> str:
    lines = [
        "# Kraken 10/10 Closure Suite Output Audit",
        "",
        f"Complete: `{str(audit.complete).lower()}`",
        f"Suite dir: `{audit.suite_dir}`",
        "",
        "| Requirement | Status | Evidence |",
        "| --- | --- | --- |",
    ]
    for item in audit.items:
        evidence = item.evidence.replace("|", "\\|")
        lines.append(f"| {item.requirement} | `{item.status}` | `{evidence}` |")
    lines.extend(
        [
            "",
            "## Boundary",
            "",
            "This verifies one closure-suite output directory only. Physical relay evidence",
            "requires a successful normal forward and attack-specific mode effects. It does not replace",
            "production cryptographic review or release readiness.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit a Kraken 10/10 closure-suite output directory.")
    parser.add_argument("--suite-dir", required=True, type=Path)
    parser.add_argument("--out-json", type=Path)
    parser.add_argument("--out-md", type=Path)
    args = parser.parse_args()

    audit = run_audit(args.suite_dir)
    out_json = args.out_json or (args.suite_dir / "closure_suite_output_audit.json")
    out_md = args.out_md or (args.suite_dir / "closure_suite_output_audit.md")
    write_audit(audit, out_json, out_md)
    print(out_json)
    return 0 if audit.complete else 2


if __name__ == "__main__":
    raise SystemExit(main())
