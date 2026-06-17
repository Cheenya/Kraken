from __future__ import annotations

import csv
import importlib.util
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = ROOT / "scripts" / "adamova_effectiveness_experiment.py"
AUDIT_SCRIPT_PATH = ROOT / "scripts" / "audit_adamova_effectiveness_evidence.py"
SPEC = importlib.util.spec_from_file_location("adamova_effectiveness_experiment", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
adamova_effectiveness = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = adamova_effectiveness
SPEC.loader.exec_module(adamova_effectiveness)
AUDIT_SPEC = importlib.util.spec_from_file_location("audit_adamova_effectiveness_evidence", AUDIT_SCRIPT_PATH)
assert AUDIT_SPEC is not None and AUDIT_SPEC.loader is not None
adamova_audit = importlib.util.module_from_spec(AUDIT_SPEC)
sys.modules[AUDIT_SPEC.name] = adamova_audit
AUDIT_SPEC.loader.exec_module(adamova_audit)

CSV_PATH = adamova_effectiveness.CSV_PATH
JSON_PATH = adamova_effectiveness.JSON_PATH
MD_PATH = adamova_effectiveness.MD_PATH
THESIS_TABLE_PATH = adamova_effectiveness.THESIS_TABLE_PATH
AUDIT_JSON_PATH = adamova_audit.AUDIT_JSON_PATH
AUDIT_MD_PATH = adamova_audit.AUDIT_MD_PATH
run_experiment = adamova_effectiveness.run_experiment
write_reports = adamova_effectiveness.write_reports
run_audit = adamova_audit.run_audit
write_audit = adamova_audit.write_audit


def test_adamova_effectiveness_experiment_reduces_weak_accepts_to_zero() -> None:
    report = run_experiment()
    metrics = report.metrics

    assert metrics.profiles_total == 20
    assert metrics.weak_or_invalid_total == 10
    assert metrics.accepted_control_total == 10
    assert metrics.constructed_reference_cases == 10
    assert metrics.sage_fixture_controls == 3
    assert metrics.generated_control_profiles == 7
    assert metrics.accepted_without_precheck_weak == 8
    assert metrics.accepted_by_discriminant_only_weak == 6
    assert metrics.accepted_by_adamova_gate_weak == 0
    assert metrics.rejected_or_blocked_by_adamova_gate_weak == 10
    assert metrics.accepted_controls_by_adamova_gate == 10
    assert metrics.needs_reference_validation == 2
    assert metrics.size_guarded == 1
    assert "to 0/10" in report.thesis_statement
    assert "not production message cryptographic security" in report.claim_boundary
    assert report.profile_policy_version == 1
    assert report.gate_backend == "host_cpp_native_core_cli_from_android_source"
    assert report.native_cli_path is not None


def test_adamova_effectiveness_experiment_writes_reports() -> None:
    report = run_experiment()
    original_paths = {
        "REPORT_DIR": adamova_effectiveness.REPORT_DIR,
        "JSON_PATH": adamova_effectiveness.JSON_PATH,
        "CSV_PATH": adamova_effectiveness.CSV_PATH,
        "MD_PATH": adamova_effectiveness.MD_PATH,
        "THESIS_TABLE_PATH": adamova_effectiveness.THESIS_TABLE_PATH,
    }
    tmp_report_dir = ROOT / "build" / "test-output" / "adamova-effectiveness"
    adamova_effectiveness.REPORT_DIR = tmp_report_dir
    adamova_effectiveness.JSON_PATH = tmp_report_dir / "adamova_effectiveness_experiment.json"
    adamova_effectiveness.CSV_PATH = tmp_report_dir / "adamova_effectiveness_experiment.csv"
    adamova_effectiveness.MD_PATH = tmp_report_dir / "adamova_effectiveness_experiment.md"
    adamova_effectiveness.THESIS_TABLE_PATH = tmp_report_dir / "adamova_effectiveness_dissertation_table.md"
    try:
        write_reports(report)

        assert adamova_effectiveness.MD_PATH.exists()
        assert adamova_effectiveness.JSON_PATH.exists()
        assert adamova_effectiveness.CSV_PATH.exists()
        assert adamova_effectiveness.THESIS_TABLE_PATH.exists()

        payload = json.loads(adamova_effectiveness.JSON_PATH.read_text(encoding="utf-8"))
        assert payload["profile_policy_version"] == 1
        assert payload["gate_backend"] == "host_cpp_native_core_cli_from_android_source"
        assert payload["native_cli_path"].endswith("build/adamova-host/adamova_native_cli")
        assert payload["native_cli_retention"] == "generated_on_demand_disposable_build_output"
        assert "Adamova Stage A diagnostics" in payload["native_backend_version"]
        assert payload["metrics"]["accepted_by_adamova_gate_weak"] == 0
        assert payload["metrics"]["accepted_by_discriminant_only_weak"] == 6
        assert payload["metrics"]["sage_fixture_controls"] == 3
        assert payload["metrics"]["generated_control_profiles"] == 7
        assert payload["results"][0]["scenario_id"] == "singular_zero_zero"

        rows = list(csv.DictReader(adamova_effectiveness.CSV_PATH.open(encoding="utf-8")))
        assert len(rows) == report.metrics.profiles_total
        assert {row["family"] for row in rows} >= {
            "singular",
            "small_torsion",
            "malformed",
            "size_guarded",
            "downgrade",
            "packet_mismatch",
            "accepted_control",
        }
        assert b"\r\n" not in adamova_effectiveness.CSV_PATH.read_bytes()

        markdown = adamova_effectiveness.MD_PATH.read_text(encoding="utf-8")
        assert "Adamova Effectiveness Experiment" in markdown
        assert "`accepted_by_adamova_gate_weak` | 0" in markdown
        assert "Native CLI retention" in markdown

        thesis_table = adamova_effectiveness.THESIS_TABLE_PATH.read_text(encoding="utf-8")
        assert "Таблица для диссертации" in thesis_table
        assert "Принято Adamova gate | 0" in thesis_table
        assert "Native CLI retention" in thesis_table
    finally:
        adamova_effectiveness.REPORT_DIR = original_paths["REPORT_DIR"]
        adamova_effectiveness.JSON_PATH = original_paths["JSON_PATH"]
        adamova_effectiveness.CSV_PATH = original_paths["CSV_PATH"]
        adamova_effectiveness.MD_PATH = original_paths["MD_PATH"]
        adamova_effectiveness.THESIS_TABLE_PATH = original_paths["THESIS_TABLE_PATH"]


def test_adamova_completion_audit_keeps_android_live_evidence_pending_without_capture(tmp_path: Path) -> None:
    report = run_experiment()
    original_effectiveness_paths = {
        "REPORT_DIR": adamova_effectiveness.REPORT_DIR,
        "JSON_PATH": adamova_effectiveness.JSON_PATH,
        "CSV_PATH": adamova_effectiveness.CSV_PATH,
        "MD_PATH": adamova_effectiveness.MD_PATH,
        "THESIS_TABLE_PATH": adamova_effectiveness.THESIS_TABLE_PATH,
    }
    original_audit_paths = {
        "REPORT_DIR": adamova_audit.REPORT_DIR,
        "JSON_PATH": adamova_audit.JSON_PATH,
        "MD_PATH": adamova_audit.MD_PATH,
        "CSV_PATH": adamova_audit.CSV_PATH,
        "THESIS_TABLE_PATH": adamova_audit.THESIS_TABLE_PATH,
        "AUDIT_JSON_PATH": adamova_audit.AUDIT_JSON_PATH,
        "AUDIT_MD_PATH": adamova_audit.AUDIT_MD_PATH,
        "LIVE_EVIDENCE_ROOT": adamova_audit.LIVE_EVIDENCE_ROOT,
    }
    tmp_report_dir = tmp_path / "reports"
    adamova_effectiveness.REPORT_DIR = tmp_report_dir
    adamova_effectiveness.JSON_PATH = tmp_report_dir / "adamova_effectiveness_experiment.json"
    adamova_effectiveness.CSV_PATH = tmp_report_dir / "adamova_effectiveness_experiment.csv"
    adamova_effectiveness.MD_PATH = tmp_report_dir / "adamova_effectiveness_experiment.md"
    adamova_effectiveness.THESIS_TABLE_PATH = tmp_report_dir / "adamova_effectiveness_dissertation_table.md"
    adamova_audit.REPORT_DIR = tmp_report_dir
    adamova_audit.JSON_PATH = adamova_effectiveness.JSON_PATH
    adamova_audit.CSV_PATH = adamova_effectiveness.CSV_PATH
    adamova_audit.MD_PATH = adamova_effectiveness.MD_PATH
    adamova_audit.THESIS_TABLE_PATH = adamova_effectiveness.THESIS_TABLE_PATH
    adamova_audit.AUDIT_JSON_PATH = tmp_report_dir / "adamova_effectiveness_completion_audit.json"
    adamova_audit.AUDIT_MD_PATH = tmp_report_dir / "adamova_effectiveness_completion_audit.md"
    adamova_audit.LIVE_EVIDENCE_ROOT = tmp_path / "missing-live-capture"
    try:
        write_reports(report)
        audit = run_audit()
        assert not audit.complete
        assert not audit.live_android_evidence_present
        assert any(item.requirement == "Live Android evidence" and item.status == "pending" for item in audit.items)

        write_audit(audit)
        assert adamova_audit.AUDIT_JSON_PATH.exists()
        assert adamova_audit.AUDIT_MD_PATH.exists()
    finally:
        adamova_effectiveness.REPORT_DIR = original_effectiveness_paths["REPORT_DIR"]
        adamova_effectiveness.JSON_PATH = original_effectiveness_paths["JSON_PATH"]
        adamova_effectiveness.CSV_PATH = original_effectiveness_paths["CSV_PATH"]
        adamova_effectiveness.MD_PATH = original_effectiveness_paths["MD_PATH"]
        adamova_effectiveness.THESIS_TABLE_PATH = original_effectiveness_paths["THESIS_TABLE_PATH"]
        adamova_audit.REPORT_DIR = original_audit_paths["REPORT_DIR"]
        adamova_audit.JSON_PATH = original_audit_paths["JSON_PATH"]
        adamova_audit.MD_PATH = original_audit_paths["MD_PATH"]
        adamova_audit.CSV_PATH = original_audit_paths["CSV_PATH"]
        adamova_audit.THESIS_TABLE_PATH = original_audit_paths["THESIS_TABLE_PATH"]
        adamova_audit.AUDIT_JSON_PATH = original_audit_paths["AUDIT_JSON_PATH"]
        adamova_audit.AUDIT_MD_PATH = original_audit_paths["AUDIT_MD_PATH"]
        adamova_audit.LIVE_EVIDENCE_ROOT = original_audit_paths["LIVE_EVIDENCE_ROOT"]
