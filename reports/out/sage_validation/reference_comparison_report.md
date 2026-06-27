# SageMath Reference Comparison Report

This report compares local math-core Android-compatible diagnostic reports
against SageMath reference output when it is available.

## Status

- Reference validation status: `available`
- Local reports: `reports/out/android_curve_reports`
- Sage results: `reports/out/sage_validation/sage_reference_results.json`
- Compared curves: 15
- Status counts: `{"match": 11, "unsupported_local": 4}`

## Comparison Table

| curve_id | status | mismatches | notes |
| --- | --- | --- | --- |
| `fractional_full_two_torsion` | `unsupported_local` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `fractional_one_two_torsion` | `unsupported_local` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `full_two_torsion_scaled` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `full_two_torsion_three_roots` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `full_two_torsion_x3_minus_x` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `no_two_torsion_x3_plus_2x_plus_1` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `no_two_torsion_x3_plus_x_plus_1` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `nonsingular_negative_discriminant` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `one_two_torsion_plus_order_four` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `one_two_torsion_x3_minus_2x_plus_1` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `one_two_torsion_x3_minus_one` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `one_two_torsion_x3_plus_2x_plus_3` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `order_three_x3_plus_1` | `match` | - | Sage torsion order is reference data; local bounded probing records comparable diagnostic fields. |
| `singular_double_root` | `unsupported_local` | - | Singular curve: rational roots are not treated as elliptic-curve torsion points. |
| `singular_zero_curve` | `unsupported_local` | - | Singular curve: rational roots are not treated as elliptic-curve torsion points. |

## Interpretation

- `match` means all directly comparable fields matched.
- `mismatch` requires review before citing in dissertation text.
- `missing_reference` means SageMath output is absent for that curve.
- `unsupported_local` or `unsupported_reference` records the current comparison scope.
- `needs_manual_review` means the comparison cannot be reduced to a direct field match.
