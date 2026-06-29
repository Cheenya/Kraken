from __future__ import annotations

import json
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path


ANDROID_ROOT = Path(__file__).resolve().parents[1]
MATH_ROOT = Path("/Users/cheenya/Projects/disser-messenger-project")
REPORT_DIR = ANDROID_ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "dirty_tree_commit_slices_2026-06-07.json"
MD_PATH = REPORT_DIR / "dirty_tree_commit_slices_2026-06-07.md"
AUDIT_DATE = "2026-06-07"


@dataclass(frozen=True, slots=True)
class Slice:
    name: str
    repository: str
    purpose: str
    files: tuple[str, ...]


SLICES = [
    Slice(
        name="android_qr_issued_invite_metadata",
        repository="android",
        purpose="Salvage useful consumed invite audit metadata without merging the old one-time invite service branch wholesale.",
        files=(
            "app-android/app/src/main/java/com/disser/kraken/invite/InviteModels.kt",
            "app-android/app/src/main/java/com/disser/kraken/invite/IssuedInviteStore.kt",
            "app-android/app/src/main/java/com/disser/kraken/navigation/KrakenAppState.kt",
            "app-android/app/src/test/java/com/disser/kraken/demo/ResearchDemoReleaseDocTest.kt",
            "app-android/app/src/test/java/com/disser/kraken/invite/IssuedInviteRecordTest.kt",
        ),
    ),
    Slice(
        name="android_mesh_ble_manual_evidence_docs",
        repository="android",
        purpose="Update LAN/Wi-Fi and BLE manual evidence boundaries plus capture helper docs/tests.",
        files=(
            "app-android/app/src/test/java/com/disser/kraken/mesh/MeshEvidenceReportTest.kt",
            "docs/codex-implementation-audit.md",
            "docs/kraken-attack-scenarios-evidence.md",
            "docs/kraken-research-demo-v1-release.md",
            "docs/manual-review-guide.md",
            "docs/mesh-transport-hardening.md",
            "docs/multi-transport-mesh-roadmap.md",
            "docs/phase-acceptance-checklist.md",
            "docs/research-mvp-scaffold-v3.md",
            "docs/research-notes-index.md",
            "reports/out/ble_two_device_delivery_evidence_2026-06-06.md",
            "reports/out/dissertation_insert_prompt_ru_2026-05-30.md",
            "reports/out/mesh_metrics_summary.json",
            "reports/out/two_device_delivery_evidence.md",
            "scripts/capture_two_phone_smoke_evidence.sh",
        ),
    ),
    Slice(
        name="android_adamova_effectiveness_evidence_pack",
        repository="android",
        purpose="Keep Adamova admission-gate scripts, tests and portable reports together.",
        files=(
            "reports/out/adamova_effectiveness_completion_audit.json",
            "reports/out/adamova_effectiveness_completion_audit.md",
            "reports/out/adamova_effectiveness_dissertation_table.md",
            "reports/out/adamova_effectiveness_experiment.csv",
            "reports/out/adamova_effectiveness_experiment.json",
            "reports/out/adamova_effectiveness_experiment.md",
            "reports/out/adamova_effectiveness_result_handoff.md",
            "scripts/adamova_effectiveness_experiment.py",
            "scripts/audit_adamova_effectiveness_evidence.py",
            "tests/test_adamova_effectiveness_experiment.py",
        ),
    ),
    Slice(
        name="android_branch_tree_cleanup_and_retention",
        repository="android",
        purpose="Document branch/stash/raw evidence cleanup and verify it read-only.",
        files=(
            "docs/branch-tree-cleanup-audit.md",
            "reports/out/branch_tree_cleanup_verification_2026-06-07.json",
            "reports/out/branch_tree_cleanup_verification_2026-06-07.md",
            "reports/out/cleanup_action_plan_2026-06-07.json",
            "reports/out/cleanup_action_plan_2026-06-07.md",
            "reports/out/cleanup_execution_status_2026-06-07.json",
            "reports/out/cleanup_execution_status_2026-06-07.md",
            "reports/out/commit_slicing_handoff_2026-06-07.json",
            "reports/out/commit_slicing_handoff_2026-06-07.md",
            "reports/out/dirty_tree_commit_slices_2026-06-07.json",
            "reports/out/dirty_tree_commit_slices_2026-06-07.md",
            "reports/out/non_phone_cleanup_status_2026-06-07.json",
            "reports/out/non_phone_cleanup_status_2026-06-07.md",
            "reports/out/non_phone_completion_audit_2026-06-07.md",
            "reports/out/phone_evidence_plan_2026-06-07.md",
            "reports/out/raw_evidence_retention_manifest_2026-06-06.json",
            "reports/out/raw_evidence_retention_manifest_2026-06-06.md",
            "scripts/audit_branch_tree_cleanup_state.py",
            "scripts/build_cleanup_action_plan.py",
            "scripts/build_commit_slicing_handoff.py",
            "scripts/audit_dirty_tree_slices.py",
            "scripts/build_non_phone_cleanup_status.py",
            "scripts/audit_raw_evidence_retention.py",
        ),
    ),
    Slice(
        name="android_june_7_readiness_and_policy_guards",
        repository="android",
        purpose="Make June 7 readiness and 10/10 research-prototype planning the current entrypoint.",
        files=(
            "README.md",
            "reports/out/current_project_readiness_2026-06-06.md",
            "reports/out/current_project_readiness_2026-06-07.md",
            "reports/out/kraken_10_10_readiness_plan_2026-06-07.json",
            "reports/out/kraken_10_10_readiness_plan_2026-06-07.md",
            "tests/test_android_policy_guards.py",
        ),
    ),
    Slice(
        name="math_supervisor_packet_june_7_sync",
        repository="math",
        purpose="Synchronize linked math/supervisor packet with June 7 Android readiness and current test counts.",
        files=(
            ".gitignore",
            "docs/android-research-panel-math-integration-plan.md",
            "docs/cpp-math-core-migration-plan.md",
            "docs/current-project-summary.md",
            "docs/dissertation-experiment-evidence-plan.md",
            "docs/dissertation-figures-and-tables-plan.md",
            "docs/dissertation-results-math-core.md",
            "docs/dissertation-writing-backlog.md",
            "docs/documentation-index.md",
            "docs/drafts/dissertation-results-chapter-draft.md",
            "docs/kraken-technical-implementation-roadmap.md",
            "docs/math-core-reference-validation.md",
            "docs/math-report-android-contract.md",
            "docs/sage-reference-validation-workflow.md",
            "docs/supervisor-review-packet.md",
            "reports/out/local_output_retention_manifest_2026-06-07.json",
            "reports/out/local_output_retention_manifest_2026-06-07.md",
            "reports/out/supervisor_packet/README.md",
            "reports/out/supervisor_packet/android_dissertation_insert_prompt_ru_2026-05-30.md",
            "reports/out/supervisor_packet/dissertation_data_readiness_2026-05-30.md",
            "reports/out/supervisor_packet/handoff.md",
            "reports/out/supervisor_packet/one_page_summary_ru.md",
            "scripts/audit_local_output_retention.py",
            "tests/test_documentation_claim_guard.py",
            "tests/test_supervisor_packet_consistency.py",
        ),
    ),
]


def main() -> None:
    payload = build_payload()
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    JSON_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    MD_PATH.write_text(_to_markdown(payload), encoding="utf-8")
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


def build_payload() -> dict[str, object]:
    dirty = {
        "android": _dirty_files(ANDROID_ROOT),
        "math": _dirty_files(MATH_ROOT),
    }
    assignments = _assign_files(dirty)
    unassigned = {
        repo: [path for path in files if path not in assignments[repo]]
        for repo, files in dirty.items()
    }
    duplicate_assignments = _duplicate_assignments()
    missing_slice_files = _missing_slice_files(dirty)
    return {
        "audit_date": AUDIT_DATE,
        "verification_passed": not any(unassigned.values())
        and duplicate_assignments == []
        and not any(missing_slice_files.values()),
        "read_only": True,
        "repositories": {
            repo: {
                "dirty_file_count": len(files),
                "unassigned": unassigned[repo],
                "missing_slice_files": missing_slice_files[repo],
            }
            for repo, files in dirty.items()
        },
        "duplicate_assignments": duplicate_assignments,
        "slices": [
            {
                **asdict(slice_),
                "dirty_files": [
                    path
                    for path in slice_.files
                    if path in dirty[slice_.repository]
                ],
            }
            for slice_ in SLICES
        ],
    }


def _dirty_files(root: Path) -> list[str]:
    modified = _git(root, ["diff", "--name-only"]).splitlines()
    untracked = _git(root, ["ls-files", "--others", "--exclude-standard"]).splitlines()
    return sorted(set(modified + untracked))


def _assign_files(dirty: dict[str, list[str]]) -> dict[str, dict[str, str]]:
    assignments: dict[str, dict[str, str]] = {"android": {}, "math": {}}
    for slice_ in SLICES:
        for path in slice_.files:
            if path in dirty[slice_.repository]:
                assignments[slice_.repository][path] = slice_.name
    return assignments


def _duplicate_assignments() -> list[dict[str, str]]:
    seen: dict[tuple[str, str], str] = {}
    duplicates: list[dict[str, str]] = []
    for slice_ in SLICES:
        for path in slice_.files:
            key = (slice_.repository, path)
            if key in seen:
                duplicates.append(
                    {
                        "repository": slice_.repository,
                        "path": path,
                        "first_slice": seen[key],
                        "second_slice": slice_.name,
                    }
                )
            seen[key] = slice_.name
    return duplicates


def _missing_slice_files(dirty: dict[str, list[str]]) -> dict[str, list[str]]:
    missing = {"android": [], "math": []}
    roots = {"android": ANDROID_ROOT, "math": MATH_ROOT}
    for slice_ in SLICES:
        for path in slice_.files:
            if not (roots[slice_.repository] / path).exists():
                missing[slice_.repository].append(path)
    return missing


def _git(root: Path, args: list[str]) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _to_markdown(payload: dict[str, object]) -> str:
    repositories = payload["repositories"]
    assert isinstance(repositories, dict)
    slices = payload["slices"]
    assert isinstance(slices, list)
    lines = [
        "# Dirty Tree Commit Slices",
        "",
        f"Date: {AUDIT_DATE}.",
        "",
        "This read-only report maps every current modified or untracked file in",
        "the Android and linked math worktrees to an intended commit slice. It",
        "does not stage, commit, push, delete branches, drop stash entries or",
        "prune raw evidence.",
        "",
        f"Overall status: `{'passed' if payload['verification_passed'] else 'failed'}`.",
        "",
        "| Repository | Dirty files | Unassigned | Missing expected slice files |",
        "| --- | ---: | ---: | ---: |",
    ]
    for repo, info in repositories.items():
        assert isinstance(info, dict)
        lines.append(
            f"| `{repo}` | {info['dirty_file_count']} | "
            f"{len(info['unassigned'])} | {len(info['missing_slice_files'])} |"
        )
    lines.extend(["", "## Slices", ""])
    for item in slices:
        assert isinstance(item, dict)
        dirty_files = item["dirty_files"]
        assert isinstance(dirty_files, list)
        lines.extend(
            [
                f"### `{item['name']}`",
                "",
                f"- Repository: `{item['repository']}`.",
                f"- Purpose: {item['purpose']}",
                f"- Dirty files: {len(dirty_files)}.",
                "",
            ]
        )
        for path in dirty_files:
            lines.append(f"  - `{path}`")
        lines.append("")
    if lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


if __name__ == "__main__":
    main()
