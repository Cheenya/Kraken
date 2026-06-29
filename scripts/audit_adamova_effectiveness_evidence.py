from __future__ import annotations

import json
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "adamova_effectiveness_experiment.json"
MD_PATH = REPORT_DIR / "adamova_effectiveness_experiment.md"
CSV_PATH = REPORT_DIR / "adamova_effectiveness_experiment.csv"
THESIS_TABLE_PATH = REPORT_DIR / "adamova_effectiveness_dissertation_table.md"
AUDIT_JSON_PATH = REPORT_DIR / "adamova_effectiveness_completion_audit.json"
AUDIT_MD_PATH = REPORT_DIR / "adamova_effectiveness_completion_audit.md"
LIVE_EVIDENCE_ROOT = ROOT / "artifacts" / "android-adamova-live"


@dataclass(frozen=True, slots=True)
class AuditItem:
    requirement: str
    status: str
    evidence: str


@dataclass(frozen=True, slots=True)
class CompletionAudit:
    complete: bool
    adb_device_connected: bool
    live_android_evidence_present: bool
    items: list[AuditItem]


def run_audit() -> CompletionAudit:
    items: list[AuditItem] = []
    payload = _load_payload()

    _add_file_item(items, "Markdown report", MD_PATH)
    _add_file_item(items, "JSON report", JSON_PATH)
    _add_file_item(items, "CSV report", CSV_PATH)
    _add_file_item(items, "Dissertation table", THESIS_TABLE_PATH)

    if payload:
        metrics = payload["metrics"]
        items.append(
            AuditItem(
                "C++ Adamova gate backend",
                "complete" if payload["gate_backend"] == "host_cpp_native_core_cli_from_android_source" else "incomplete",
                (
                    f"gate_backend={payload['gate_backend']}; "
                    f"native_cli_path={payload.get('native_cli_path')}; "
                    f"native_cli_retention={payload.get('native_cli_retention', 'unknown')}"
                ),
            )
        )
        items.append(
            AuditItem(
                "Required corpus families",
                "complete" if _has_required_families(payload) else "incomplete",
                ", ".join(sorted({row["family"] for row in payload["results"]})),
            )
        )
        items.append(
            AuditItem(
                "Sage/reference controls",
                "complete" if metrics.get("sage_fixture_controls", 0) > 0 else "incomplete",
                f"sage_fixture_controls={metrics.get('sage_fixture_controls')}; generated_controls={metrics.get('generated_control_profiles')}",
            )
        )
        items.append(
            AuditItem(
                "Weak profile acceptance reduced to zero",
                "complete" if metrics["accepted_by_adamova_gate_weak"] == 0 else "incomplete",
                (
                    f"no_precheck={metrics['accepted_without_precheck_weak']}/"
                    f"{metrics['weak_or_invalid_total']}; discriminant_only="
                    f"{metrics['accepted_by_discriminant_only_weak']}/{metrics['weak_or_invalid_total']}; "
                    f"adamova={metrics['accepted_by_adamova_gate_weak']}/{metrics['weak_or_invalid_total']}"
                ),
            )
        )
        items.append(
            AuditItem(
                "Size/unsupported cases routed away from admission",
                "complete" if metrics["needs_reference_validation"] >= 1 and metrics["size_guarded"] >= 1 else "incomplete",
                f"needs_reference_validation={metrics['needs_reference_validation']}; size_guarded={metrics['size_guarded']}",
            )
        )
        items.append(
            AuditItem(
                "Thesis statement present",
                "complete" if "to 0/" in payload["thesis_statement"] else "incomplete",
                payload["thesis_statement"],
            )
        )
    else:
        items.append(AuditItem("Effectiveness JSON payload", "missing", str(JSON_PATH)))

    adb_device_connected = _adb_device_connected()
    live_evidence = _live_android_evidence_present()
    items.append(
        AuditItem(
            "Live Android evidence",
            "complete" if live_evidence else "pending",
            (
                f"adb_device_connected={adb_device_connected}; "
                f"live_evidence_root={LIVE_EVIDENCE_ROOT}"
            ),
        )
    )

    complete = all(item.status == "complete" for item in items)
    return CompletionAudit(
        complete=complete,
        adb_device_connected=adb_device_connected,
        live_android_evidence_present=live_evidence,
        items=items,
    )


def write_audit(audit: CompletionAudit) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT_JSON_PATH.write_text(json.dumps(_audit_to_dict(audit), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    AUDIT_MD_PATH.write_text(_audit_to_markdown(audit), encoding="utf-8")


def _load_payload() -> dict[str, object] | None:
    if not JSON_PATH.exists():
        return None
    return json.loads(JSON_PATH.read_text(encoding="utf-8"))


def _add_file_item(items: list[AuditItem], requirement: str, path: Path) -> None:
    items.append(
        AuditItem(
            requirement=requirement,
            status="complete" if path.exists() and path.stat().st_size > 0 else "missing",
            evidence=str(path),
        )
    )


def _has_required_families(payload: dict[str, object]) -> bool:
    families = {row["family"] for row in payload["results"]}
    return {
        "singular",
        "small_torsion",
        "malformed",
        "size_guarded",
        "downgrade",
        "packet_mismatch",
        "accepted_control",
    }.issubset(families)


def _adb_device_connected() -> bool:
    try:
        output = subprocess.check_output(["adb", "devices"], cwd=ROOT, text=True, stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return False
    return any(line.strip().endswith("\tdevice") for line in output.splitlines())


def _live_android_evidence_present() -> bool:
    if not LIVE_EVIDENCE_ROOT.exists():
        return False
    for candidate in LIVE_EVIDENCE_ROOT.iterdir():
        if not candidate.is_dir():
            continue
        if (
            (candidate / "research_mode_screen.png").exists()
            and (candidate / "admission_gate_attack_demo_latest.json").exists()
            and (candidate / "admission_gate_attack_demo_latest.md").exists()
        ):
            return True
    return False


def _audit_to_dict(audit: CompletionAudit) -> dict[str, object]:
    return {
        "complete": audit.complete,
        "adb_device_connected": audit.adb_device_connected,
        "live_android_evidence_present": audit.live_android_evidence_present,
        "items": [asdict(item) for item in audit.items],
    }


def _audit_to_markdown(audit: CompletionAudit) -> str:
    lines = [
        "# Adamova Effectiveness Completion Audit",
        "",
        f"Complete: `{str(audit.complete).lower()}`.",
        f"ADB device connected: `{str(audit.adb_device_connected).lower()}`.",
        f"Live Android evidence present: `{str(audit.live_android_evidence_present).lower()}`.",
        "",
        "| Requirement | Status | Evidence |",
        "| --- | --- | --- |",
    ]
    for item in audit.items:
        lines.append(f"| {item.requirement} | `{item.status}` | {item.evidence} |")
    lines.extend(
        [
            "",
            "## Boundary",
            "",
            "This audit checks whether the requested Adamova effectiveness evidence pack is present.",
            "It does not turn the experiment into a production cryptographic security proof.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> None:
    audit = run_audit()
    write_audit(audit)
    print(f"Wrote {AUDIT_MD_PATH}")
    print(f"Wrote {AUDIT_JSON_PATH}")
    print(f"complete={audit.complete}")


if __name__ == "__main__":
    main()
