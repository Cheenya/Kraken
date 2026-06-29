from __future__ import annotations

import json
from pathlib import Path
from shlex import join


ROOT = Path(__file__).resolve().parents[1]
MATH_ROOT = Path("/Users/cheenya/Projects/disser-messenger-project")
REPORT_DIR = ROOT / "reports" / "out"
DIRTY_TREE_JSON = REPORT_DIR / "dirty_tree_commit_slices_2026-06-07.json"
JSON_PATH = REPORT_DIR / "commit_slicing_handoff_2026-06-07.json"
MD_PATH = REPORT_DIR / "commit_slicing_handoff_2026-06-07.md"
REPORT_DATE = "2026-06-07"


COMMIT_SUBJECTS = {
    "android_qr_issued_invite_metadata": "Salvage issued invite metadata guardrails",
    "android_mesh_ble_manual_evidence_docs": "Refresh LAN and BLE manual evidence docs",
    "android_adamova_effectiveness_evidence_pack": "Add Adamova effectiveness evidence pack",
    "android_branch_tree_cleanup_and_retention": "Document branch cleanup and retention plan",
    "android_june_7_readiness_and_policy_guards": "Refresh June 7 Kraken readiness guards",
    "math_supervisor_packet_june_7_sync": "Sync dissertation packet with June 7 readiness",
}


def main() -> None:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    payload = build_payload()
    JSON_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    MD_PATH.write_text(to_markdown(payload), encoding="utf-8")
    print(f"Wrote {MD_PATH}")
    print(f"Wrote {JSON_PATH}")


def build_payload() -> dict[str, object]:
    dirty = json.loads(DIRTY_TREE_JSON.read_text(encoding="utf-8"))
    assert dirty["verification_passed"] is True

    handoff_slices = []
    for item in dirty["slices"]:
        dirty_files = item["dirty_files"]
        if not dirty_files:
            continue
        repository = item["repository"]
        root = ROOT if repository == "android" else MATH_ROOT
        subject = COMMIT_SUBJECTS[item["name"]]
        handoff_slices.append(
            {
                "name": item["name"],
                "repository": repository,
                "root": str(root),
                "purpose": item["purpose"],
                "dirty_file_count": len(dirty_files),
                "files": dirty_files,
                "stage_command": ["git", "-C", str(root), "add", "--", *dirty_files],
                "review_command": ["git", "-C", str(root), "diff", "--cached", "--stat"],
                "commit_command": ["git", "-C", str(root), "commit", "-m", subject],
                "commit_subject": subject,
            }
        )

    return {
        "report_date": REPORT_DATE,
        "read_only": True,
        "executed": False,
        "stage_commit_push_executed": False,
        "source_dirty_tree": str(DIRTY_TREE_JSON.relative_to(ROOT)),
        "source_verification_passed": dirty["verification_passed"],
        "repositories": dirty["repositories"],
        "slices": handoff_slices,
        "post_commit_checks": [
            ["/Users/cheenya/Projects/disser-messenger-project/.venv/bin/python", "-m", "pytest"],
            ["./gradlew", "test"],
            ["./gradlew", "assembleDebug"],
            ["git", "diff", "--check"],
        ],
    }


def to_markdown(payload: dict[str, object]) -> str:
    lines = [
        "# Commit Slicing Handoff",
        "",
        f"Date: {REPORT_DATE}.",
        "",
        "This is a read-only staging/commit handoff. It does not run staging, commit or push commands.",
        "Use it only after explicit approval to commit the current dirty tree.",
        "",
        f"- Read-only: `{str(payload['read_only']).lower()}`.",
        f"- Executed: `{str(payload['executed']).lower()}`.",
        f"- Stage/commit/push executed: `{str(payload['stage_commit_push_executed']).lower()}`.",
        f"- Source dirty-tree verification passed: `{str(payload['source_verification_passed']).lower()}`.",
        "",
        "## Required Discipline",
        "",
        "- Do not use `git add .`.",
        "- Stage one slice at a time.",
        "- Review `git diff --cached --stat` before each commit.",
        "- Re-run the relevant tests after the final slice.",
        "- Do not push until explicitly approved.",
        "",
        "## Slices",
        "",
    ]

    for item in payload["slices"]:
        assert isinstance(item, dict)
        lines.extend(
            [
                f"### `{item['name']}`",
                "",
                f"- Repository: `{item['repository']}`.",
                f"- Root: `{item['root']}`.",
                f"- Purpose: {item['purpose']}",
                f"- Dirty files: {item['dirty_file_count']}.",
                f"- Commit subject: `{item['commit_subject']}`.",
                "",
                "Files:",
                "",
            ]
        )
        for path in item["files"]:
            lines.append(f"- `{path}`")
        lines.extend(
            [
                "",
                "Commands after explicit commit approval:",
                "",
                f"- `{join(item['stage_command'])}`",
                f"- `{join(item['review_command'])}`",
                f"- `{join(item['commit_command'])}`",
                "",
            ]
        )

    lines.extend(["## Post-Commit Checks", ""])
    for command in payload["post_commit_checks"]:
        assert isinstance(command, list)
        lines.append(f"- `{join(command)}`")

    lines.extend(
        [
            "",
            "## Boundary",
            "",
            "This handoff does not decide branch deletion, stash cleanup, raw artifact",
            "retention, or phone-dependent recapture. Those remain in the cleanup action",
            "plan and non-phone cleanup status reports.",
            "",
        ]
    )
    return "\n".join(lines)


if __name__ == "__main__":
    main()
