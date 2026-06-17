from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

from disser_messenger.research.torsion import RationalCurve, classify_a1_a6


def benchmark_one(item: dict[str, Any]) -> dict[str, Any]:
    curve = RationalCurve(a=int(item["a"]), b=int(item["b"]))
    wall_start = time.perf_counter_ns()
    cpu_start = time.process_time_ns()
    result = classify_a1_a6(curve)
    cpu_end = time.process_time_ns()
    wall_end = time.perf_counter_ns()

    expected_case = item.get("expected_case")
    agreement_status = "unknown" if expected_case is None else "match"
    disagreement_reason = None
    if expected_case is not None and expected_case != result.case:
        agreement_status = "disagreement"
        disagreement_reason = f"expected {expected_case}, got {result.case}"

    return {
        "curve_id": item.get("curve_id", curve.short_name),
        "a": curve.a,
        "b": curve.b,
        "discriminant_nonzero": result.discriminant_nonzero,
        "c2": result.c2,
        "has_3_torsion_indicator": result.has_3_torsion_indicator,
        "classification_case": result.case,
        "candidate_count_2": len(result.diagnostics["two_torsion_roots"]),
        "candidate_count_3": len(result.diagnostics["three_torsion_candidates"]),
        "stage_a_wall_time_ns": wall_end - wall_start,
        "stage_a_cpu_time_ns": cpu_end - cpu_start,
        "stage_b_torsion_type": item.get("sage_torsion"),
        "agreement_status": agreement_status,
        "disagreement_reason": disagreement_reason,
    }


def run_jsonl(fixtures: Path, output: Path) -> None:
    data = json.loads(fixtures.read_text(encoding="utf-8"))
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as stream:
        for item in data:
            row = benchmark_one(item)
            stream.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")
