import json
from pathlib import Path

from disser_messenger.research.torsion import RationalCurve, classify_a1_a6
from disser_messenger.research.torsion.classifier import count_rational_2_torsion


FIXTURES = Path(__file__).parent / "fixtures" / "torsion_examples.json"


def test_singular_curve_is_detected() -> None:
    curve = RationalCurve(a=0, b=0)
    assert not curve.is_nonsingular()


def test_three_roots_case_for_two_torsion() -> None:
    curve = RationalCurve(a=-1, b=0)
    assert count_rational_2_torsion(curve) == 3


def test_verified_fixtures_match_expected_cases() -> None:
    fixtures = json.loads(FIXTURES.read_text(encoding="utf-8"))
    for fixture in fixtures:
        curve = RationalCurve(a=fixture["a"], b=fixture["b"])
        result = classify_a1_a6(curve)
        assert result.discriminant_nonzero, fixture["curve_id"]
        assert result.case == fixture["expected_case"], fixture["curve_id"]


def test_known_order_three_points_are_detected() -> None:
    fixtures = json.loads(FIXTURES.read_text(encoding="utf-8"))
    selected = [f for f in fixtures if f.get("known_point_order") == 3]
    assert selected
    for fixture in selected:
        curve = RationalCurve(a=fixture["a"], b=fixture["b"])
        result = classify_a1_a6(curve)
        assert result.has_3_torsion_indicator, fixture["curve_id"]
