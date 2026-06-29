from __future__ import annotations

import json
import subprocess
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "branch_tree_cleanup_verification_2026-06-07.json"
MD_PATH = REPORT_DIR / "branch_tree_cleanup_verification_2026-06-07.md"
AUDIT_DATE = "2026-06-07"


@dataclass(frozen=True, slots=True)
class Check:
    name: str
    passed: bool
    detail: str


def main() -> None:
    checks = build_checks()
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "audit_date": AUDIT_DATE,
        "verification_passed": all(check.passed for check in checks),
        "read_only": True,
        "checks": [asdict(check) for check in checks],
    }
    JSON_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    MD_PATH.write_text(_to_markdown(checks), encoding="utf-8")
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


def build_checks() -> list[Check]:
    current_branch = _git(["rev-parse", "--abbrev-ref", "HEAD"])
    current_tip = _git(["rev-parse", "--short", "HEAD"])
    upstream = _git(["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"])
    upstream_tip = _git(["rev-parse", "--short", upstream])
    worktree_output = _git(["worktree", "list", "--porcelain"])
    stash_lines = _git(["stash", "list"]).splitlines()
    qr_cherry = _cherry("HEAD", "codex/qr-one-time-invite")
    remote_v2_cherry = _cherry("HEAD", "origin/chore/research-mvp-scaffold-v2")
    remote_v1_cherry = _cherry("HEAD", "origin/chore/research-mvp-scaffold")

    checks = [
        Check(
            name="active_android_branch",
            passed=current_branch == "codex/android-research-panel-report-viewer",
            detail=f"{current_branch}@{current_tip}",
        ),
        Check(
            name="android_upstream_synced",
            passed=upstream == "origin/codex/android-research-panel-report-viewer"
            and current_tip == upstream_tip,
            detail=f"{upstream}@{upstream_tip}",
        ),
        Check(
            name="linked_math_worktree_present",
            passed="branch refs/heads/codex/math-experiment-evidence-pack"
            in worktree_output
            and "/Users/cheenya/Projects/disser-messenger-project" in worktree_output,
            detail="math worktree listed by git worktree list --porcelain",
        ),
    ]

    checks.extend(_ancestor_checks())
    checks.extend(
        [
            Check(
                name="qr_branch_patch_unique_but_not_wholesale_merge_candidate",
                passed=len(qr_cherry) == 1 and "Add one-time QR invite lifecycle" in qr_cherry[0],
                detail="; ".join(qr_cherry),
            ),
            Check(
                name="remote_scaffold_v2_has_no_patch_unique_commits",
                passed=remote_v2_cherry == [],
                detail=f"patch_unique_count={len(remote_v2_cherry)}",
            ),
            Check(
                name="remote_scaffold_still_needs_manual_review",
                passed=any(line.startswith("+ ") for line in remote_v1_cherry),
                detail=f"patch_unique_count={sum(line.startswith('+ ') for line in remote_v1_cherry)}",
            ),
            Check(
                name="stash_inventory_still_indexed",
                passed=len(stash_lines) == 2
                and "two phone qa artifacts before clean rebuild" in "\n".join(stash_lines)
                and "local xiaomi ui audit artifacts before clean build"
                in "\n".join(stash_lines),
                detail=" | ".join(stash_lines),
            ),
            Check(
                name="cleanup_audit_keeps_destructive_actions_approval_only",
                passed=_audit_text_contains_approval_boundaries(),
                detail="branch, stash, remote and raw evidence cleanup remain documented as approval-only",
            ),
        ]
    )
    return checks


def _ancestor_checks() -> list[Check]:
    android_ancestors = [
        "main",
        "codex/android-identity-invite-relationship-realm",
        "codex/android-skeleton",
        "codex/protocol-spec-core-models",
    ]
    math_ancestors = [
        "codex/dissertation-research-alignment",
        "codex/math-core-curve-diagnostics",
        "codex/math-core-reference-validation",
        "codex/math-research-panel-integration-plan",
    ]
    checks = [
        Check(
            name=f"android_ancestor_{branch}",
            passed=_is_ancestor(branch, "HEAD"),
            detail=f"{branch} ancestor of Android HEAD",
        )
        for branch in android_ancestors
    ]
    checks.extend(
        Check(
            name=f"math_ancestor_{branch}",
            passed=_is_ancestor(branch, "codex/math-experiment-evidence-pack"),
            detail=f"{branch} ancestor of codex/math-experiment-evidence-pack",
        )
        for branch in math_ancestors
    )
    return checks


def _audit_text_contains_approval_boundaries() -> bool:
    text = (ROOT / "docs" / "branch-tree-cleanup-audit.md").read_text(encoding="utf-8")
    required = [
        "Approval-Only Cleanup Commands",
        "No staging, commit or push was performed",
        "Do not mix branch/stash deletion",
        "git branch -d",
        "git branch -D",
        "git stash drop",
        "git push origin --delete",
        "Deletion commands should be written only after the retention window is chosen.",
    ]
    return all(item in text for item in required)


def _cherry(base: str, target: str) -> list[str]:
    output = _git(["cherry", "-v", base, target])
    return [line for line in output.splitlines() if line]


def _is_ancestor(candidate: str, target: str) -> bool:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "merge-base", "--is-ancestor", candidate, target],
        check=False,
    )
    return result.returncode == 0


def _git(args: list[str]) -> str:
    result = subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def _to_markdown(checks: list[Check]) -> str:
    lines = [
        "# Branch Tree Cleanup Verification",
        "",
        f"Date: {AUDIT_DATE}.",
        "",
        "This is a read-only verification of the branch/tree cleanup audit. It",
        "runs Git inspection commands only and does not delete branches, drop",
        "stash entries, stage files, commit, push or prune raw evidence.",
        "",
        f"Overall status: `{'passed' if all(check.passed for check in checks) else 'failed'}`.",
        "",
        "| Check | Status | Detail |",
        "| --- | --- | --- |",
    ]
    for check in checks:
        status = "passed" if check.passed else "failed"
        detail = check.detail.replace("|", "\\|")
        lines.append(f"| `{check.name}` | `{status}` | {detail} |")
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    main()
