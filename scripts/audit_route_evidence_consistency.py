from __future__ import annotations

import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
RAW_ROOT = ROOT / "artifacts" / "two-phone-test"
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "route_evidence_consistency_audit_2026-06-08.json"
MD_PATH = REPORT_DIR / "route_evidence_consistency_audit_2026-06-08.md"
AUDIT_DATE = "2026-06-08"

JsonGetter = Callable[[dict[str, Any], Path], object]


@dataclass(frozen=True, slots=True)
class FieldSpec:
    summary_key: str
    json_path: str
    getter: JsonGetter


@dataclass(frozen=True, slots=True)
class FieldMismatch:
    key: str
    json_path: str
    expected: str
    actual: str | None


@dataclass(frozen=True, slots=True)
class PairAudit:
    json_path: str
    summary_path: str | None
    summary_kind: str
    status: str
    mismatches: list[FieldMismatch]


def main() -> None:
    audits = build_audits()
    payload = build_payload(audits)
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    JSON_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MD_PATH.write_text(to_markdown(payload, audits), encoding="utf-8")
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


def build_audits() -> list[PairAudit]:
    json_files = sorted(RAW_ROOT.rglob("route_specific_evidence*.json")) if RAW_ROOT.exists() else []
    audits: list[PairAudit] = []
    for json_file in json_files:
        summary_file = _summary_for(json_file)
        if summary_file is None:
            audits.append(
                PairAudit(
                    json_path=_rel(json_file),
                    summary_path=None,
                    summary_kind=_summary_kind(json_file),
                    status="json_only_raw_export",
                    mismatches=[],
                )
            )
            continue

        data = json.loads(json_file.read_text(encoding="utf-8"))
        summary = _parse_summary(summary_file)
        mismatches = _compare(data, json_file, summary, _specs_for(json_file))
        audits.append(
            PairAudit(
                json_path=_rel(json_file),
                summary_path=_rel(summary_file),
                summary_kind=_summary_kind(json_file),
                status="matches" if not mismatches else "raw_summary_mismatch_quarantined",
                mismatches=mismatches,
            )
        )
    return audits


def build_payload(audits: list[PairAudit]) -> dict[str, object]:
    mismatch_audits = [audit for audit in audits if audit.mismatches]
    json_only = [audit for audit in audits if audit.summary_path is None]
    return {
        "audit_date": AUDIT_DATE,
        "raw_root": _rel(RAW_ROOT),
        "verification_passed": True,
        "status": "passed_with_quarantined_raw_mismatches" if mismatch_audits else "passed",
        "pairs_scanned": len(audits),
        "matching_pairs": sum(1 for audit in audits if audit.status == "matches"),
        "json_only_raw_exports": len(json_only),
        "mismatched_pairs": len(mismatch_audits),
        "mismatch_field_count": sum(len(audit.mismatches) for audit in mismatch_audits),
        "source_hierarchy": [
            "reports/out/two_device_route_specific_smoke_2026-06-08.md",
            "reports/out/two_device_route_specific_smoke_2026-06-08.json",
            "reports/out/mesh_metrics_summary.json",
            "ignored artifacts/two-phone-test JSON",
            "ignored artifacts/two-phone-test markdown summaries only after JSON consistency check",
        ],
        "raw_markdown_summary_policy": (
            "Ignored per-device markdown summaries are not current source-of-truth when "
            "they disagree with sibling JSON; cite consolidated reports/out artifacts first."
        ),
        "audits": [asdict(audit) for audit in audits],
    }


def to_markdown(payload: dict[str, object], audits: list[PairAudit]) -> str:
    mismatch_audits = [audit for audit in audits if audit.mismatches]
    lines = [
        "# Route Evidence Consistency Audit",
        "",
        f"Date: {AUDIT_DATE}.",
        "",
        "Purpose: prevent ignored raw per-device markdown summaries from being used as",
        "current source-of-truth when they disagree with sibling JSON exports.",
        "",
        f"Status: `{payload['status']}`.",
        "",
        "| Metric | Count |",
        "| --- | ---: |",
        f"| Pairs scanned | {payload['pairs_scanned']} |",
        f"| Matching pairs | {payload['matching_pairs']} |",
        f"| JSON-only raw exports | {payload['json_only_raw_exports']} |",
        f"| Mismatched pairs | {payload['mismatched_pairs']} |",
        f"| Mismatched fields | {payload['mismatch_field_count']} |",
        "",
        "## Source Hierarchy",
        "",
    ]
    source_hierarchy = payload["source_hierarchy"]
    if not isinstance(source_hierarchy, list):
        raise TypeError("source_hierarchy must be a list")
    lines.extend(f"{index}. `{path}`" for index, path in enumerate(source_hierarchy, start=1))
    lines.extend(
        [
            "",
            "## Policy",
            "",
            str(payload["raw_markdown_summary_policy"]),
            "",
            "## Mismatches",
            "",
        ]
    )
    if not mismatch_audits:
        lines.append("No JSON-vs-markdown mismatches found.")
    for audit in mismatch_audits:
        lines.extend(
            [
                f"### `{audit.summary_path}`",
                "",
                f"- Sibling JSON: `{audit.json_path}`",
                "- Status: `raw_summary_mismatch_quarantined`",
                "",
                "| Field | JSON path | JSON value | Markdown value |",
                "| --- | --- | --- | --- |",
            ]
        )
        for mismatch in audit.mismatches:
            lines.append(
                f"| `{mismatch.key}` | `{mismatch.json_path}` | "
                f"`{mismatch.expected}` | `{mismatch.actual}` |"
            )
        lines.append("")
    lines.extend(
        [
            "## JSON-only Raw Exports",
            "",
            "These exports have no sibling markdown summary to compare. That is acceptable",
            "for raw export folders; use the JSON directly or the consolidated tracked report.",
            "",
        ]
    )
    lines.extend(f"- `{audit.json_path}`" for audit in audits if audit.summary_path is None)
    lines.append("")
    return "\n".join(lines)


def _summary_for(json_file: Path) -> Path | None:
    if json_file.name == "route_specific_evidence_latest.json":
        summary_file = json_file.with_name("route_specific_evidence_summary_latest.md")
    else:
        summary_file = json_file.with_name("route_specific_evidence_summary.md")
    return summary_file if summary_file.exists() else None


def _summary_kind(json_file: Path) -> str:
    return "in_app_latest" if json_file.name.endswith("_latest.json") else "capture_bundle"


def _specs_for(json_file: Path) -> tuple[FieldSpec, ...]:
    common = (
        FieldSpec(
            "Selected route",
            "transport.selected_route",
            lambda data, _path: _nested(data, "transport", "selected_route"),
        ),
        FieldSpec(
            "Recent route attempts",
            "transport.recent_route_attempts.count",
            lambda data, _path: len(_nested(data, "transport", "recent_route_attempts", default=[])),
        ),
        FieldSpec("Queue size", "queue_size", lambda data, _path: _queue_size(data)),
        FieldSpec("Last packet status", "last_packet_status", lambda data, _path: data.get("last_packet_status", "n/a")),
        FieldSpec("App version", "app_version_name", lambda data, _path: data.get("app_version_name", "n/a")),
        FieldSpec("Build type", "app_build_type", lambda data, _path: data.get("app_build_type", "n/a")),
        FieldSpec("Git SHA", "git_sha", lambda data, _path: data.get("git_sha", "n/a")),
        FieldSpec("Device model", "device_model", lambda data, _path: data.get("device_model", "n/a")),
        FieldSpec("packetsSent", "metrics.packets_sent", lambda data, _path: _nested(data, "metrics", "packets_sent")),
        FieldSpec("packetsReceived", "metrics.packets_received", lambda data, _path: _nested(data, "metrics", "packets_received")),
        FieldSpec("receiptsReceived", "metrics.receipts_received", lambda data, _path: _nested(data, "metrics", "receipts_received")),
        FieldSpec("duplicatesDropped", "metrics.duplicates_dropped", lambda data, _path: _nested(data, "metrics", "duplicates_dropped")),
        FieldSpec("expiredDropped", "metrics.expired_dropped", lambda data, _path: _nested(data, "metrics", "expired_dropped")),
        FieldSpec("unknownPeerRejected", "metrics.unknown_peer_rejected", lambda data, _path: _nested(data, "metrics", "unknown_peer_rejected")),
        FieldSpec("wrongRecipientRejected", "metrics.wrong_recipient_rejected", lambda data, _path: _nested(data, "metrics", "wrong_recipient_rejected")),
        FieldSpec("lastDeliveryLatencyMs", "metrics.last_delivery_latency_ms", lambda data, _path: _nested(data, "metrics", "last_delivery_latency_ms")),
    )
    if json_file.name != "route_specific_evidence_latest.json":
        return (FieldSpec("Source JSON", "summary.source_json", lambda _data, path: path.name),) + common
    return common + (
        FieldSpec("evidenceMode", "debug_smoke.evidence_mode", lambda data, _path: _nested(data, "debug_smoke", "evidence_mode")),
        FieldSpec("unknownPeerInjected", "debug_smoke.unknown_peer_injected", lambda data, _path: _nested(data, "debug_smoke", "unknown_peer_injected")),
        FieldSpec("wrongRecipientInjected", "debug_smoke.wrong_recipient_injected", lambda data, _path: _nested(data, "debug_smoke", "wrong_recipient_injected")),
        FieldSpec("duplicateInjected", "debug_smoke.duplicate_injected", lambda data, _path: _nested(data, "debug_smoke", "duplicate_injected")),
        FieldSpec("queuedBeforeTransportRestart", "debug_smoke.queued_before_transport_restart", lambda data, _path: _nested(data, "debug_smoke", "queued_before_transport_restart")),
        FieldSpec("queueSizeBeforeRestart", "debug_smoke.queue_size_before_restart", lambda data, _path: _nested(data, "debug_smoke", "queue_size_before_restart")),
        FieldSpec("sentAfterTransportRestart", "debug_smoke.sent_after_transport_restart", lambda data, _path: _nested(data, "debug_smoke", "sent_after_transport_restart")),
        FieldSpec("deliveredAfterTransportRestart", "debug_smoke.delivered_after_transport_restart", lambda data, _path: _nested(data, "debug_smoke", "delivered_after_transport_restart")),
        FieldSpec("queueSizeAfterRestart", "debug_smoke.queue_size_after_restart", lambda data, _path: _nested(data, "debug_smoke", "queue_size_after_restart")),
        FieldSpec("messageStatusAfterRestart", "debug_smoke.message_status_after_restart", lambda data, _path: _nested(data, "debug_smoke", "message_status_after_restart")),
    )


def _parse_summary(path: Path) -> dict[str, str]:
    parsed: dict[str, str] = {}
    line_pattern = re.compile(r"^- ([^:]+): `([^`]*)`$")
    for line in path.read_text(encoding="utf-8").splitlines():
        match = line_pattern.match(line.strip())
        if match:
            parsed[match.group(1)] = match.group(2)
    return parsed


def _compare(
    data: dict[str, Any],
    json_file: Path,
    summary: dict[str, str],
    specs: tuple[FieldSpec, ...],
) -> list[FieldMismatch]:
    mismatches: list[FieldMismatch] = []
    for spec in specs:
        expected = _normalize(spec.getter(data, json_file))
        actual = summary.get(spec.summary_key)
        if actual != expected:
            mismatches.append(
                FieldMismatch(
                    key=spec.summary_key,
                    json_path=spec.json_path,
                    expected=expected,
                    actual=actual,
                )
            )
    return mismatches


def _queue_size(data: dict[str, Any]) -> object:
    return data.get("queue_size", data.get("queued_packets", "n/a"))


def _nested(data: dict[str, Any], *keys: str, default: object = "n/a") -> object:
    current: object = data
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return default
        current = current[key]
    return current if current is not None else default


def _normalize(value: object) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, bool):
        return str(value).lower()
    return str(value)


def _rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


if __name__ == "__main__":
    main()
