from __future__ import annotations

import json
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "raw_evidence_retention_manifest_2026-06-06.json"
MD_PATH = REPORT_DIR / "raw_evidence_retention_manifest_2026-06-06.md"
AUDIT_DATE = "2026-06-07"


@dataclass(frozen=True, slots=True)
class EvidenceAreaPolicy:
    path: str
    retention_class: str
    disposition: str
    evidence_summary: str


@dataclass(frozen=True, slots=True)
class EvidenceArea:
    path: str
    exists: bool
    bytes_total: int
    size_mib: float
    file_count: int
    child_directory_count: int
    tracked_file_count: int
    git_classification: str
    retention_class: str
    disposition: str
    evidence_summary: str
    approval_required_before_delete: bool


POLICIES = [
    EvidenceAreaPolicy(
        path="artifacts/phone-preflight",
        retention_class="ignored_raw_device_preflight",
        disposition="keep ignored until a retention window is approved",
        evidence_summary="summarized by docs/branch-tree-cleanup-audit.md; includes many timestamped ADB/device state captures",
    ),
    EvidenceAreaPolicy(
        path="artifacts/device-screenshots",
        retention_class="mixed_curated_tracked_and_ignored_raw_screenshots",
        disposition="keep tracked curated subset; keep ignored raw runs until retention decision",
        evidence_summary="tracked curated visual evidence exists, while ignored timestamped QA runs remain local",
    ),
    EvidenceAreaPolicy(
        path="artifacts/live-screenshots",
        retention_class="ignored_raw_live_screenshots",
        disposition="keep ignored until retention decision",
        evidence_summary="raw live screenshots only; no bulk delete without review",
    ),
    EvidenceAreaPolicy(
        path="artifacts/screenshots",
        retention_class="ignored_raw_screenshot_output",
        disposition="keep ignored until retention decision",
        evidence_summary="manual/raw screenshot output; curate selected images before deletion",
    ),
    EvidenceAreaPolicy(
        path="artifacts/ui-ux-device",
        retention_class="ignored_raw_ui_ux_device_evidence",
        disposition="keep ignored until retention decision",
        evidence_summary="device UI/UX verification runs; summarize before pruning",
    ),
    EvidenceAreaPolicy(
        path="artifacts/ui-ux-concepts",
        retention_class="ignored_raw_ui_concepts",
        disposition="keep ignored until retention decision",
        evidence_summary="generated/local UI concept assets; curate before pruning",
    ),
    EvidenceAreaPolicy(
        path="artifacts/android-adamova-live",
        retention_class="ignored_raw_adamova_android_capture",
        disposition="keep ignored; tracked Adamova reports are the portable evidence pack",
        evidence_summary="referenced by reports/out/adamova_effectiveness_result_handoff.md and completion audit",
    ),
    EvidenceAreaPolicy(
        path="artifacts/two-phone-test",
        retention_class="ignored_raw_two_phone_delivery_evidence",
        disposition="keep ignored; do not bulk-delete while current reports reference raw captures",
        evidence_summary="referenced by reports/out/two_device_delivery_evidence.md and current readiness report",
    ),
    EvidenceAreaPolicy(
        path="app-android/app/build",
        retention_class="generated_gradle_output_local_apk_rebuild_output",
        disposition="keep local debug APK output for rebuild/retest convenience until retention is approved",
        evidence_summary="two-phone reports record capture-time APK path/hash; later local rebuilds can produce a different hash",
    ),
]


def build_manifest() -> list[EvidenceArea]:
    return [_inspect_area(policy) for policy in POLICIES]


def write_manifest(areas: list[EvidenceArea]) -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "audit_date": AUDIT_DATE,
        "approval_required_before_delete": True,
        "areas": [asdict(area) for area in areas],
    }
    JSON_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    MD_PATH.write_text(_to_markdown(areas), encoding="utf-8")


def _inspect_area(policy: EvidenceAreaPolicy) -> EvidenceArea:
    path = ROOT / policy.path
    tracked_files = _tracked_files(policy.path)
    exists = path.exists()
    bytes_total, file_count, child_directory_count = _measure(path) if exists else (0, 0, 0)
    ignored_entries = _has_ignored_entries(policy.path)
    ignored_path = _is_ignored(policy.path)
    if (ignored_entries or ignored_path) and tracked_files:
        git_classification = "mixed_tracked_curated_and_ignored"
    elif ignored_entries or ignored_path:
        git_classification = "ignored"
    elif tracked_files:
        git_classification = "tracked"
    else:
        git_classification = "untracked_visible_or_missing"
    return EvidenceArea(
        path=policy.path,
        exists=exists,
        bytes_total=bytes_total,
        size_mib=round(bytes_total / (1024 * 1024), 3),
        file_count=file_count,
        child_directory_count=child_directory_count,
        tracked_file_count=len(tracked_files),
        git_classification=git_classification,
        retention_class=policy.retention_class,
        disposition=policy.disposition,
        evidence_summary=policy.evidence_summary,
        approval_required_before_delete=True,
    )


def _measure(path: Path) -> tuple[int, int, int]:
    if path.is_file():
        return path.stat().st_size, 1, 0
    bytes_total = 0
    file_count = 0
    child_directory_count = 0
    for child in path.iterdir():
        if child.is_dir():
            child_directory_count += 1
    for entry in path.rglob("*"):
        if entry.is_file():
            file_count += 1
            bytes_total += entry.stat().st_size
    return bytes_total, file_count, child_directory_count


def _tracked_files(path: str) -> list[str]:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "ls-files", "--", path],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line for line in result.stdout.splitlines() if line]


def _is_ignored(path: str) -> bool:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "check-ignore", "-q", path],
        check=False,
    )
    return result.returncode == 0


def _has_ignored_entries(path: str) -> bool:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "status", "--ignored", "--short", "--", path],
        check=True,
        capture_output=True,
        text=True,
    )
    return any(line.startswith("!! ") for line in result.stdout.splitlines())


def _to_markdown(areas: list[EvidenceArea]) -> str:
    lines = [
        "# Raw Evidence Retention Manifest",
        "",
        f"Date: {AUDIT_DATE}.",
        "",
        "Purpose: classify ignored/local evidence and generated APK output without",
        "deleting anything. Every listed area requires explicit user approval before",
        "deletion or pruning.",
        "",
        "| Path | Git class | Size MiB | Files | Child dirs | Tracked files | Disposition |",
        "| --- | --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for area in areas:
        lines.append(
            f"| `{area.path}` | `{area.git_classification}` | {area.size_mib:.3f} | "
            f"{area.file_count} | {area.child_directory_count} | "
            f"{area.tracked_file_count} | {area.disposition} |"
        )
    lines.extend(
        [
            "",
            "## Retention Rules",
            "",
            "- Do not bulk-delete raw evidence directories while tracked reports reference them.",
            "- Prefer promoting small curated summaries/screenshots over committing raw PNG/XML/device dumps.",
            "- `app-android/app/build` is generated output. Two-phone reports record",
            "  capture-time APK path/hash; later local rebuilds can produce a different",
            "  hash, so do not overwrite capture metadata without a fresh recapture.",
            "- Stash entries and branches remain separate approval-only cleanup items in",
            "  `docs/branch-tree-cleanup-audit.md`.",
            "",
            "## Area Notes",
            "",
        ]
    )
    for area in areas:
        lines.extend(
            [
                f"### `{area.path}`",
                "",
                f"- Retention class: `{area.retention_class}`.",
                f"- Evidence summary: {area.evidence_summary}.",
                f"- Approval required before delete: `{str(area.approval_required_before_delete).lower()}`.",
                "",
            ]
        )
    return "\n".join(lines)


def main() -> None:
    areas = build_manifest()
    write_manifest(areas)
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


if __name__ == "__main__":
    main()
