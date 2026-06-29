from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "cleanup_action_plan_2026-06-07.json"
MD_PATH = REPORT_DIR / "cleanup_action_plan_2026-06-07.md"
PLAN_DATE = "2026-06-07"


@dataclass(frozen=True, slots=True)
class CleanupAction:
    name: str
    category: str
    repository: str
    approval_required: bool
    status: str
    rationale: str
    verification_before: tuple[tuple[str, ...], ...]
    commands_after_approval: tuple[tuple[str, ...], ...]
    do_not_touch: tuple[str, ...] = ()


ACTIONS = [
    CleanupAction(
        name="delete_safe_merged_android_archive_branches",
        category="local_branch_cleanup",
        repository="android",
        approval_required=True,
        status="ready_after_user_approval",
        rationale="These local branches are verified ancestors of the active Android branch.",
        verification_before=(
            ("git", "merge-base", "--is-ancestor", "main", "HEAD"),
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/android-identity-invite-relationship-realm",
                "HEAD",
            ),
            ("git", "merge-base", "--is-ancestor", "codex/android-skeleton", "HEAD"),
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/protocol-spec-core-models",
                "HEAD",
            ),
        ),
        commands_after_approval=(
            (
                "git",
                "branch",
                "-d",
                "codex/android-identity-invite-relationship-realm",
                "codex/android-skeleton",
                "codex/protocol-spec-core-models",
            ),
        ),
        do_not_touch=(
            "codex/android-research-panel-report-viewer",
            "codex/math-experiment-evidence-pack",
            "main",
        ),
    ),
    CleanupAction(
        name="delete_audited_math_and_qr_local_archive_branches",
        category="local_branch_cleanup",
        repository="android",
        approval_required=True,
        status="requires_separate_user_approval",
        rationale=(
            "Math branches are ancestors of the active math worktree branch; "
            "the QR branch has one patch-unique commit whose useful metadata was "
            "salvaged without wholesale merging the old service model."
        ),
        verification_before=(
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/dissertation-research-alignment",
                "codex/math-experiment-evidence-pack",
            ),
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/math-core-curve-diagnostics",
                "codex/math-experiment-evidence-pack",
            ),
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/math-core-reference-validation",
                "codex/math-experiment-evidence-pack",
            ),
            (
                "git",
                "merge-base",
                "--is-ancestor",
                "codex/math-research-panel-integration-plan",
                "codex/math-experiment-evidence-pack",
            ),
            ("git", "cherry", "-v", "HEAD", "codex/qr-one-time-invite"),
        ),
        commands_after_approval=(
            (
                "git",
                "branch",
                "-D",
                "codex/dissertation-research-alignment",
                "codex/math-core-curve-diagnostics",
                "codex/math-core-reference-validation",
                "codex/math-research-panel-integration-plan",
                "codex/qr-one-time-invite",
            ),
        ),
        do_not_touch=(
            "codex/android-research-panel-report-viewer",
            "codex/math-experiment-evidence-pack",
            "main",
        ),
    ),
    CleanupAction(
        name="delete_low_risk_remote_scaffold_v2",
        category="remote_branch_cleanup",
        repository="origin",
        approval_required=True,
        status="requires_separate_user_approval",
        rationale="Remote scaffold v2 has no patch-unique commits relative to active Android HEAD.",
        verification_before=(
            ("git", "cherry", "-v", "HEAD", "origin/chore/research-mvp-scaffold-v2"),
        ),
        commands_after_approval=(
            ("git", "push", "origin", "--delete", "chore/research-mvp-scaffold-v2"),
        ),
        do_not_touch=("origin/chore/research-mvp-scaffold",),
    ),
    CleanupAction(
        name="drop_raw_artifact_stashes",
        category="stash_cleanup",
        repository="android",
        approval_required=True,
        status="requires_user_confirmation_after_stat_review",
        rationale="Both stash entries contain raw UI/two-phone artifacts whose summaries now exist in tracked reports.",
        verification_before=(
            ("git", "stash", "show", "--include-untracked", "--stat", "stash@{1}"),
            ("git", "stash", "show", "--include-untracked", "--stat", "stash@{0}"),
        ),
        commands_after_approval=(
            ("git", "stash", "drop", "stash@{1}"),
            ("git", "stash", "drop", "stash@{0}"),
        ),
    ),
    CleanupAction(
        name="review_android_raw_evidence_retention_window",
        category="raw_evidence_retention",
        repository="android",
        approval_required=True,
        status="retention_window_not_chosen",
        rationale=(
            "Android raw evidence directories are ignored/local and classified by "
            "reports/out/raw_evidence_retention_manifest_2026-06-06.md."
        ),
        verification_before=(
            (
                "du",
                "-sh",
                "artifacts/phone-preflight/",
                "artifacts/device-screenshots/",
                "artifacts/live-screenshots/",
                "artifacts/screenshots/",
                "artifacts/ui-ux-device/",
                "artifacts/android-adamova-live/",
                "artifacts/two-phone-test/",
                "app-android/app/build/",
            ),
        ),
        commands_after_approval=(),
        do_not_touch=(
            "artifacts/two-phone-test/ while current reports reference local raw captures",
            "tracked curated screenshots/assets",
        ),
    ),
    CleanupAction(
        name="review_math_local_output_retention_window",
        category="local_output_retention",
        repository="math",
        approval_required=True,
        status="retention_window_not_chosen",
        rationale=(
            "Linked math local outputs and .venv are classified by "
            "reports/out/local_output_retention_manifest_2026-06-07.md."
        ),
        verification_before=(
            (
                "du",
                "-sh",
                ".venv",
                "reports/out/benchmark-runs",
                "reports/out/large-coefficient-benchmark-runs",
                "reports/out/math-core",
                "reports/out/math-core-extended",
                "reports/out/math-core-repeated-smoke",
                "reports/out/torsion-reproduction",
            ),
        ),
        commands_after_approval=(),
        do_not_touch=(
            "tracked aggregate reports",
            "Sage reproducibility packs",
            "supervisor packet files",
        ),
    ),
]


def main() -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    payload = build_payload()
    JSON_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    MD_PATH.write_text(_to_markdown(payload), encoding="utf-8")
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


def build_payload() -> dict[str, object]:
    return {
        "plan_date": PLAN_DATE,
        "read_only": True,
        "executed": False,
        "approval_required_before_any_action": True,
        "actions": [asdict(action) for action in ACTIONS],
    }


def _to_markdown(payload: dict[str, object]) -> str:
    actions = payload["actions"]
    assert isinstance(actions, list)
    lines = [
        "# Cleanup Action Plan",
        "",
        f"Date: {PLAN_DATE}.",
        "",
        "This is a read-only approval plan. It does not execute any cleanup.",
        "Every action below requires explicit user approval before running.",
        "",
        f"- Read-only: `{str(payload['read_only']).lower()}`.",
        f"- Executed: `{str(payload['executed']).lower()}`.",
        f"- Approval required before any action: `{str(payload['approval_required_before_any_action']).lower()}`.",
        "",
        "## Actions",
        "",
    ]
    for action in actions:
        assert isinstance(action, dict)
        lines.extend(
            [
                f"### `{action['name']}`",
                "",
                f"- Category: `{action['category']}`.",
                f"- Repository: `{action['repository']}`.",
                f"- Status: `{action['status']}`.",
                f"- Approval required: `{str(action['approval_required']).lower()}`.",
                f"- Rationale: {action['rationale']}",
                "",
                "Verification before approval/run:",
                "",
            ]
        )
        for command in action["verification_before"]:
            lines.append(f"- `{' '.join(command)}`")
        if action["commands_after_approval"]:
            lines.extend(["", "Commands after explicit approval:", ""])
            for command in action["commands_after_approval"]:
                lines.append(f"- `{' '.join(command)}`")
        else:
            lines.extend(["", "Commands after explicit approval: not written yet; choose retention window first."])
        if action["do_not_touch"]:
            lines.extend(["", "Do not touch:", ""])
            for item in action["do_not_touch"]:
                lines.append(f"- `{item}`")
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    main()
