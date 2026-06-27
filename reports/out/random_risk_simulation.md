# Random Parameter Risk Simulation

Цель: проверить diagnostic gate на детерминированном random/injected корпусе параметров рациональных кривых над `Q`.

## Короткий вывод

- Seed: `20260524`.
- Curve count: 90.
- Group counts: `{'injected_full_two_torsion': 10, 'injected_one_two_torsion': 20, 'injected_singular': 10, 'large_discriminant_guarded': 10, 'random_nonsingular_no_obvious_risk': 40}`.
- Risk labels: `{'risky': 50, 'safe': 40}`.
- No precheck false accepts: 50 (100.00%).
- Discriminant-only false accepts: 40 (80.00%).
- Kraken precheck false accepts: 0 (0.00%).
- Kraken precheck false rejects: 0 (0.00%).
- Kraken needs reference validation: 10.

## Baseline Comparison

| baseline | decisions | false accepts | false rejects | needs reference |
| --- | --- | ---: | ---: | ---: |
| no_precheck | `{'pass': 90}` | 50 | 0 | 0 |
| discriminant_only | `{'pass': 80, 'reject': 10}` | 40 | 0 | 0 |
| kraken_precheck | `{'needs_reference_validation': 10, 'pass': 40, 'reject': 40}` | 0 | 0 | 10 |

## Methodology

- Safe generation: Deterministic pseudo-random small integral coefficients filtered to nonsingular curves with no rational 2-torsion and no local Lutz-Nagell size guard.
- Injected risk groups:
  - singular curves constructed as a=-3*t^2, b=2*t^3
  - curves with one constructed rational 2-torsion root
  - curves with three constructed rational 2-torsion roots
  - large-discriminant guarded curves requiring reference validation

## Interpretation

1. `no_precheck` accepts every construction-labeled risky candidate.
2. `discriminant_only` removes singular curves but still accepts rational 2-torsion and guarded cases.
3. `kraken_precheck` rejects singular/rational-2-torsion cases and quarantines large guarded cases as `needs_reference_validation`.
4. The simulation records how the diagnostic workflow handles injected risk groups.

## Notes

- Use this report for the diagnostic gate, random/injected corpus and workflow
  evidence.
- Message encryption, signatures and finite-field ECC hardness are outside this
  simulation.
