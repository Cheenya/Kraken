from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / "reports" / "out"
JSON_PATH = REPORT_DIR / "non_phone_cleanup_status_2026-06-07.json"
MD_PATH = REPORT_DIR / "non_phone_cleanup_status_2026-06-07.md"
REPORT_DATE = "2026-06-07"

BRANCH_VERIFICATION = REPORT_DIR / "branch_tree_cleanup_verification_2026-06-07.json"
DIRTY_TREE = REPORT_DIR / "dirty_tree_commit_slices_2026-06-07.json"
CLEANUP_PLAN = REPORT_DIR / "cleanup_action_plan_2026-06-07.json"
RAW_RETENTION = REPORT_DIR / "raw_evidence_retention_manifest_2026-06-06.json"
MATH_RETENTION = Path(
    "/Users/cheenya/Projects/disser-messenger-project/reports/out/"
    "local_output_retention_manifest_2026-06-07.json"
)


NON_PHONE_COMPLETED_ITEMS = (
    {
        "name": "branch_tree_audit",
        "status": "complete",
        "evidence": "reports/out/branch_tree_cleanup_verification_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "dirty_tree_commit_slices",
        "status": "complete",
        "evidence": "reports/out/dirty_tree_commit_slices_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "cleanup_action_plan",
        "status": "complete_read_only",
        "evidence": "reports/out/cleanup_action_plan_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "commit_slicing_handoff",
        "status": "complete_read_only",
        "evidence": "reports/out/commit_slicing_handoff_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "android_raw_evidence_retention_manifest",
        "status": "complete_read_only",
        "evidence": "reports/out/raw_evidence_retention_manifest_2026-06-06.md",
        "phone_required": False,
    },
    {
        "name": "math_local_output_retention_manifest",
        "status": "complete_read_only",
        "evidence": "math reports/out/local_output_retention_manifest_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "active_documentation_sync",
        "status": "complete_for_current_non_phone_state",
        "evidence": "math docs/documentation-index.md and tests/test_documentation_claim_guard.py",
        "phone_required": False,
    },
    {
        "name": "cross_tree_non_phone_completion_audit",
        "status": "complete_read_only",
        "evidence": "reports/out/non_phone_completion_audit_2026-06-07.md",
        "phone_required": False,
    },
    {
        "name": "separate_phone_evidence_plan",
        "status": "complete_plan_only",
        "evidence": "reports/out/phone_evidence_plan_2026-06-07.md",
        "phone_required": False,
    },
)

PHONE_DEPENDENT_REMAINDER = (
    {
        "name": "fresh_route_specific_delivery_capture",
        "reason": "ADB visibility and Bluetooth-direct open-chat UI evidence were captured on 2026-06-07; the remaining gate is orchestrated fresh-send/counter evidence",
        "evidence_to_collect": "fresh orchestrated sends, route labels, receipt UI, rejection cases, packet counters",
    },
    {
        "name": "route_specific_lan_ble_counter_smoke",
        "reason": "requires live devices on LAN/BLE routes",
        "evidence_to_collect": "authoritative packet counters, queue/latency metrics, rejection/counter cases",
    },
    {
        "name": "fresh_research_ui_visual_pass",
        "reason": "requires emulator or physical device rendering",
        "evidence_to_collect": "Research screen screenshots with diagnostic-only wording",
    },
)


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
    branch = _read_json(BRANCH_VERIFICATION)
    dirty = _read_json(DIRTY_TREE)
    cleanup = _read_json(CLEANUP_PLAN)
    raw_retention = _read_json(RAW_RETENTION)
    math_retention = _read_json(MATH_RETENTION)

    cleanup_actions = cleanup["actions"]
    assert isinstance(cleanup_actions, list)

    dirty_repositories = dirty["repositories"]
    assert isinstance(dirty_repositories, dict)

    approval_only_remainder = [
        {
            "name": action["name"],
            "category": action["category"],
            "repository": action["repository"],
            "status": action["status"],
            "approval_required": action["approval_required"],
        }
        for action in cleanup_actions
        if action["approval_required"]
    ]

    non_phone_verification_passed = (
        branch["verification_passed"] is True
        and dirty["verification_passed"] is True
        and cleanup["read_only"] is True
        and cleanup["executed"] is False
        and cleanup["approval_required_before_any_action"] is True
        and raw_retention["approval_required_before_delete"] is True
        and math_retention["approval_required_before_delete"] is True
    )

    return {
        "report_date": REPORT_DATE,
        "read_only": True,
        "phone_required_for_this_report": False,
        "non_phone_verification_passed": non_phone_verification_passed,
        "destructive_actions_executed": False,
        "completed_without_phones": list(NON_PHONE_COMPLETED_ITEMS),
        "dirty_tree_summary": {
            repo: {
                "dirty_file_count": info["dirty_file_count"],
                "unassigned_count": len(info["unassigned"]),
                "missing_slice_files_count": len(info["missing_slice_files"]),
            }
            for repo, info in dirty_repositories.items()
        },
        "commit_slices": [
            {
                "name": item["name"],
                "repository": item["repository"],
                "dirty_file_count": len(item["dirty_files"]),
                "purpose": item["purpose"],
            }
            for item in dirty["slices"]
        ],
        "approval_only_remainder": approval_only_remainder,
        "phone_dependent_remainder": list(PHONE_DEPENDENT_REMAINDER),
    }


def to_markdown(payload: dict[str, object]) -> str:
    lines = [
        "# Non-Phone Cleanup Status",
        "",
        f"Date: {REPORT_DATE}.",
        "",
        "This report closes the cleanup work that can be verified without phones.",
        "It does not delete branches, drop stash entries, prune raw evidence, stage, commit or push.",
        "",
        f"- Read-only: `{str(payload['read_only']).lower()}`.",
        f"- Phone required for this report: `{str(payload['phone_required_for_this_report']).lower()}`.",
        f"- Non-phone verification passed: `{str(payload['non_phone_verification_passed']).lower()}`.",
        f"- Destructive actions executed: `{str(payload['destructive_actions_executed']).lower()}`.",
        "",
        "## Completed Without Phones",
        "",
    ]

    for item in payload["completed_without_phones"]:
        assert isinstance(item, dict)
        lines.append(
            f"- `{item['name']}`: `{item['status']}`; evidence: `{item['evidence']}`."
        )

    lines.extend(["", "## Dirty Tree Summary", ""])
    dirty_summary = payload["dirty_tree_summary"]
    assert isinstance(dirty_summary, dict)
    lines.extend(
        [
            "| Repository | Dirty files | Unassigned | Missing slice files |",
            "| --- | ---: | ---: | ---: |",
        ]
    )
    for repo, info in dirty_summary.items():
        assert isinstance(info, dict)
        lines.append(
            f"| `{repo}` | {info['dirty_file_count']} | "
            f"{info['unassigned_count']} | {info['missing_slice_files_count']} |"
        )

    lines.extend(["", "## Commit Slices", ""])
    for item in payload["commit_slices"]:
        assert isinstance(item, dict)
        lines.append(
            f"- `{item['name']}` (`{item['repository']}`, "
            f"{item['dirty_file_count']} files): {item['purpose']}"
        )

    lines.extend(["", "## Approval-Only Remainder", ""])
    for item in payload["approval_only_remainder"]:
        assert isinstance(item, dict)
        lines.append(
            f"- `{item['name']}` (`{item['category']}`, `{item['repository']}`): "
            f"`{item['status']}`."
        )

    lines.extend(["", "## Phone-Dependent Remainder", ""])
    for item in payload["phone_dependent_remainder"]:
        assert isinstance(item, dict)
        lines.extend(
            [
                f"### `{item['name']}`",
                "",
                f"- Reason: {item['reason']}.",
                f"- Evidence to collect: {item['evidence_to_collect']}.",
                "",
            ]
        )

    lines.extend(
        [
            "## Boundary",
            "",
            "The non-phone cleanup state is verifiable from Git metadata, generated manifests,",
            "documentation guards and pytest. The 2026-06-07 connected-phone pass captured",
            "ADB/open-chat UI evidence; orchestrated route-specific counter capture remains a separate plan.",
            "",
        ]
    )
    return "\n".join(lines)


def _read_json(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
