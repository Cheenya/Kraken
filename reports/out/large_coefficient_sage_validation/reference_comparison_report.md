# SageMath Reference Comparison Report

This report compares local math-core Android-compatible diagnostic reports against SageMath reference output when it is available.
It does not claim production encryption or production cryptographic security.

## Status

- Reference validation status: `available`
- Local reports: `reports/out/large_coefficient_curve_reports`
- Sage results: `reports/out/large_coefficient_sage_validation/sage_reference_results.json`
- Compared curves: 20
- Status counts: `{"match": 20}`

## Comparison Table

| curve_id | status | mismatches | notes |
| --- | --- | --- | --- |
| `lc128_controlled_full_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc128_large_a_small_b_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc128_large_pair_no_obvious_root` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc128_negative_mixed_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc128_scaled_order_four` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc32_controlled_full_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc32_controlled_one_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc32_mersenne_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc32_mixed_sign_one_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc32_prime_offsets_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc64_controlled_full_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc64_mixed_large_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc64_near_signed_limit_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc64_prime_offsets_no_two_torsion` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lc64_scaled_order_four` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lcstruct_full_two_torsion_large_three_roots` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lcstruct_lutz_large_discriminant_stress` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lcstruct_no_obvious_root_decimal_stress` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lcstruct_one_two_torsion_large_root` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |
| `lcstruct_scaled_order_four_medium` | `match` | - | Sage torsion order is reference data; local bounded probing is not a full torsion proof.; Local Lutz-Nagell candidate enumeration was skipped by size guard; directly comparable invariants and 2-torsion fields were still checked. |

## Interpretation

- `match` means all directly comparable fields matched.
- `mismatch` requires manual review before dissertation claims.
- `missing_reference` means SageMath output is absent for that curve.
- `unsupported_local` or `unsupported_reference` must be reported as a limitation.
- `needs_manual_review` means the comparison cannot be reduced to a direct field match.
