#!/usr/bin/env python3
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
MATRIX_JSON = REPORT_DIR / "kraken_10_10_completion_matrix_2026-06-10.json"
MATRIX_MD = REPORT_DIR / "kraken_10_10_completion_matrix_2026-06-10.md"
FOLLOWUP_JSON = REPORT_DIR / "kraken_10_10_followup_audit_2026-06-10.json"
READINESS_JSON = REPORT_DIR / "current_project_readiness_2026-06-08.json"
README = ROOT / "README.md"
RESEARCH_NOTES_INDEX = ROOT / "docs" / "research-notes-index.md"
AUDIT_JSON = REPORT_DIR / "kraken_10_10_completion_matrix_audit_2026-06-10.json"
AUDIT_MD = REPORT_DIR / "kraken_10_10_completion_matrix_audit_2026-06-10.md"

REQUIRED_OPEN_GATES = {
    "wifi_direct_route_delivery",
    "wifi_direct_negative_tests",
    "physical_mac_inline_relay_modes",
    "physical_hostile_packet_injection_lan_ble_wifi_direct",
    "route_benchmark_min_n",
}
REQUIRED_CLOSED_OR_BOUNDED_GATES = {
    "release_like_prototype_hard_gate",
    "adamova_product_path_research",
    "lan_ble_dissertation_smoke",
}
EXPECTED_COMMANDS = {
    "wifi_direct_route_capture",
    "route_benchmark",
    "physical_inline_relay",
}


@dataclass(frozen=True, slots=True)
class AuditItem:
    requirement: str
    status: str
    evidence: str


@dataclass(frozen=True, slots=True)
class CompletionMatrixAudit:
    complete: bool
    current_10_10_verdict: str
    open_gate_count: int
    items: list[AuditItem]


def run_audit() -> CompletionMatrixAudit:
    items: list[AuditItem] = []
    matrix = _load_json(MATRIX_JSON)
    followup = _load_json(FOLLOWUP_JSON)
    readiness = _load_json(READINESS_JSON)
    gates = {str(gate.get("id")): gate for gate in matrix.get("gates", [])}

    _add_file_item(items, "Completion matrix JSON", MATRIX_JSON)
    _add_file_item(items, "Completion matrix markdown", MATRIX_MD)

    verdict = str(matrix.get("current_10_10_verdict", "missing"))
    items.append(
        AuditItem(
            "Matrix keeps 10/10 incomplete while gates are open",
            "complete" if verdict == "not_complete" else "failed",
            f"current_10_10_verdict={verdict}",
        )
    )

    for gate_id in sorted(REQUIRED_OPEN_GATES):
        gate = gates.get(gate_id)
        status = str(gate.get("status")) if gate else "missing"
        items.append(
            AuditItem(
                f"Required open gate: {gate_id}",
                "complete" if status == "open" else "failed",
                f"status={status}",
            )
        )

    for gate_id in sorted(REQUIRED_CLOSED_OR_BOUNDED_GATES):
        gate = gates.get(gate_id)
        status = str(gate.get("status")) if gate else "missing"
        ok = status in {
            "closed_for_minimal_gate",
            "closed_for_research_prototype",
            "closed_for_smoke_not_reliability",
        }
        items.append(
            AuditItem(
                f"Bounded/closed gate keeps scoped status: {gate_id}",
                "complete" if ok else "failed",
                f"status={status}",
            )
        )

    for gate_id, gate in gates.items():
        for evidence in gate.get("current_evidence", []):
            path = ROOT / str(evidence)
            items.append(
                AuditItem(
                    f"Evidence path exists for {gate_id}",
                    "complete" if path.exists() else "missing",
                    str(evidence),
                )
            )

    commands = matrix.get("commands", {})
    for command_name in sorted(EXPECTED_COMMANDS):
        command = str(commands.get(command_name, ""))
        script_name = command.split()[0] if command else ""
        script_path = ROOT / script_name if script_name else ROOT / "__missing__"
        items.append(
            AuditItem(
                f"Runnable command script exists: {command_name}",
                "complete" if command and script_path.exists() else "missing",
                command,
            )
        )

    matrix_ref = "reports/out/kraken_10_10_completion_matrix_2026-06-10.md"
    items.append(
        AuditItem(
            "Follow-up audit references completion matrix",
            "complete" if followup.get("completion_matrix", {}).get("artifact") == matrix_ref else "failed",
            json.dumps(followup.get("completion_matrix", {}), ensure_ascii=False),
        )
    )
    items.append(
        AuditItem(
            "Readiness source-of-truth references completion matrix",
            "complete" if matrix_ref in readiness.get("current_source_of_truth", []) else "failed",
            matrix_ref,
        )
    )

    for path in [README, RESEARCH_NOTES_INDEX]:
        text = path.read_text(encoding="utf-8")
        items.append(
            AuditItem(
                f"Entrypoint references completion matrix: {path.relative_to(ROOT)}",
                "complete" if "kraken_10_10_completion_matrix_2026-06-10.md" in text else "failed",
                str(path.relative_to(ROOT)),
            )
        )

    open_gate_count = sum(1 for gate in gates.values() if gate.get("status") == "open")
    complete = all(item.status == "complete" for item in items) and open_gate_count == 0
    return CompletionMatrixAudit(
        complete=complete,
        current_10_10_verdict=verdict,
        open_gate_count=open_gate_count,
        items=items,
    )


def write_audit(audit: CompletionMatrixAudit) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    AUDIT_JSON.write_text(
        json.dumps(_audit_to_dict(audit), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    AUDIT_MD.write_text(_audit_to_markdown(audit), encoding="utf-8")


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


def _audit_to_dict(audit: CompletionMatrixAudit) -> dict[str, Any]:
    return {
        "report": "kraken_10_10_completion_matrix_audit",
        "complete": audit.complete,
        "current_10_10_verdict": audit.current_10_10_verdict,
        "open_gate_count": audit.open_gate_count,
        "items": [asdict(item) for item in audit.items],
        "claim_boundary": (
            "Audit of completion-control metadata only. complete=false is expected while "
            "physical two-device route/attack/benchmark gates remain open."
        ),
    }


def _audit_to_markdown(audit: CompletionMatrixAudit) -> str:
    lines = [
        "# Kraken 10/10 Completion Matrix Audit",
        "",
        f"Complete: `{str(audit.complete).lower()}`",
        f"Current 10/10 verdict: `{audit.current_10_10_verdict}`",
        f"Open gate count: `{audit.open_gate_count}`",
        "",
        "## Items",
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
            "This audit checks completion-control metadata only. It does not close "
            "two-device route, physical attack, benchmark or production security gates.",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    audit = run_audit()
    write_audit(audit)
    print(AUDIT_JSON)
    return 0 if all(item.status == "complete" for item in audit.items) else 1


if __name__ == "__main__":
    raise SystemExit(main())
