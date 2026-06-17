#!/usr/bin/env sage
import json
import sys
from pathlib import Path

try:
    from sage.env import SAGE_VERSION
except Exception:
    SAGE_VERSION = "unknown"


def _safe_str(value):
    return None if value is None else str(value)


def _curve_row(item):
    curve_id = item["curve_id"]
    a = QQ(item["a"])
    b = QQ(item["b"])
    discriminant = -16 * (4 * a**3 + 27 * b**2)
    singular = discriminant == 0
    row = {
        "curve_id": curve_id,
        "a": str(a),
        "b": str(b),
        "discriminant": str(discriminant),
        "singular": bool(singular),
        "j_invariant": None,
        "rational_two_torsion_points": [],
        "two_torsion_count": None,
        "torsion_order": None,
        "torsion_subgroup": None,
        "torsion_gens": [],
        "rank": None,
        "rank_status": "not_computed",
        "warnings": [],
        "unsupported_cases": [],
    }

    x = polygen(QQ, "x")
    roots = (x**3 + a * x + b).roots(ring=QQ, multiplicities=False)
    row["rational_two_torsion_points"] = [{"x": str(root), "y": "0"} for root in roots]
    row["two_torsion_count"] = len(roots)

    if singular:
        row["unsupported_cases"].append("singular curve; torsion subgroup is not computed")
        return row

    curve = EllipticCurve(QQ, [0, 0, 0, a, b])
    row["j_invariant"] = str(curve.j_invariant())
    try:
        torsion = curve.torsion_subgroup()
        row["torsion_order"] = int(torsion.order())
        row["torsion_subgroup"] = str(torsion)
        row["torsion_gens"] = [str(point) for point in torsion.gens()]
    except Exception as exc:
        row["warnings"].append("torsion subgroup computation failed: " + repr(exc))

    row["rank_status"] = "not_computed"
    row["warnings"].append("rank computation intentionally skipped by generated validation script")

    return row


def main(input_path, output_path):
    payload = json.loads(Path(input_path).read_text(encoding="utf-8"))
    rows = [_curve_row(item) for item in payload["curves"]]
    output = {
        "format": "sage-torsion-reference-output",
        "reference_system": "SageMath",
        "reference_version": str(SAGE_VERSION),
        "input_path": str(input_path),
        "rows": rows,
        "research_warning": "SageMath reference validation only; not production cryptography",
    }
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)
    Path(output_path).write_text(json.dumps(output, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: sage validate_torsion_reference.sage input_curves.json sage_reference_results.json")
    main(sys.argv[1], sys.argv[2])
