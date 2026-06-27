# Adamova Effectiveness Experiment

Experiment id: `adamova_effectiveness_experiment_20260605`.
Seed: `20260605`.
Git SHA: `e542ea0`.
Profile policy version: `1`.
Gate backend: `host_cpp_native_core_cli_from_android_source`.
Native backend version: `Kraken native C++ research core: Adamova Stage A diagnostics available for signed 128-bit and smooth arbitrary-size coefficients.`.
Native CLI path: `build/adamova-host/adamova_native_cli`.
Native CLI retention: `generated_on_demand_build_output`.

The native CLI is rebuilt on demand under root `build/`; source files and the
saved reports in this repository are the retained materials.

## Scope

Controlled profile-substitution experiment over rational curve parameters. It
measures admission/precheck behavior for Kraken experimental profiles.

## Thesis Statement

In the controlled experimental-profile substitution model, Adamova admission gate reduced accepted weak/invalid profiles from 8/10 without precheck and 6/10 with discriminant-only precheck to 0/10, while routing size/unsupported cases to reference validation before session or message use.

## Metrics

| Metric | Value |
| --- | ---: |
| `profiles_total` | 20 |
| `weak_or_invalid_total` | 10 |
| `accepted_control_total` | 10 |
| `constructed_reference_cases` | 10 |
| `sage_fixture_controls` | 3 |
| `generated_control_profiles` | 7 |
| `malformed_total` | 2 |
| `size_guarded_total` | 1 |
| `downgrade_or_mismatch_total` | 2 |
| `accepted_without_precheck_weak` | 8 |
| `accepted_by_discriminant_only_weak` | 6 |
| `accepted_by_adamova_gate_weak` | 0 |
| `rejected_or_blocked_by_adamova_gate_weak` | 10 |
| `accepted_controls_by_adamova_gate` | 10 |
| `needs_reference_validation` | 2 |
| `size_guarded` | 1 |
| `median_gate_latency_ms` | 10.632542 |
| `p95_gate_latency_ms` | 15.835666 |

## Results

| Scenario | Family | Risk | Reference | No precheck | Discriminant only | Adamova decision | Adamova accepted |
| --- | --- | ---: | --- | ---: | ---: | --- | ---: |
| `singular_zero_zero` | singular | True | constructed_singular | True | False | `REJECT_SINGULAR` | False |
| `singular_minus3_2` | singular | True | constructed_singular | True | False | `REJECT_SINGULAR` | False |
| `two_torsion_minus1_0` | small_torsion | True | constructed_rational_2_torsion | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `two_torsion_minus4_0` | small_torsion | True | constructed_rational_2_torsion | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `three_torsion_0_1` | small_torsion | True | constructed_3_torsion_indicator | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `malformed_a` | malformed | True | not_applicable_malformed | False | False | `REFERENCE_VALIDATION_REQUIRED` | False |
| `malformed_b` | malformed | True | not_applicable_malformed | False | False | `REFERENCE_VALIDATION_REQUIRED` | False |
| `size_guarded_128ish` | size_guarded | True | requires_reference_validation | True | True | `SIZE_GUARDED` | False |
| `downgrade_to_two_torsion` | downgrade | True | constructed_downgrade_attack | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `packet_profile_mismatch_three_torsion` | packet_mismatch | True | constructed_packet_profile_mismatch | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `sage_accept_control_sage_checked_5_001` | accepted_control | False | SageMath torsion Z/5Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `sage_accept_control_sage_checked_5_003` | accepted_control | False | SageMath torsion Z/5Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `sage_accept_control_sage_checked_7_001` | accepted_control | False | SageMath torsion Z/7Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `generated_accept_control_01` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_02` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_03` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_04` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_05` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_06` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_07` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |

## Interpretation

- `no_precheck` models accepting any syntactically usable experimental profile.
- `discriminant_only` rejects singular curves but misses rational 2/3-torsion indicators.
- `adamova_gate` rejects singular and small-torsion-risk profiles and blocks malformed/size-guarded cases from automatic admission.
- This experiment records profile admission behavior for the tested corpus.
