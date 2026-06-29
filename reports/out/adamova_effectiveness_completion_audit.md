# Adamova Effectiveness Completion Audit

Complete: `true`.
ADB device connected: `true`.
Live Android evidence present: `true`.

| Requirement | Status | Evidence |
| --- | --- | --- |
| Markdown report | `complete` | /Users/cheenya/Projects/kraken-android-research-panel/reports/out/adamova_effectiveness_experiment.md |
| JSON report | `complete` | /Users/cheenya/Projects/kraken-android-research-panel/reports/out/adamova_effectiveness_experiment.json |
| CSV report | `complete` | /Users/cheenya/Projects/kraken-android-research-panel/reports/out/adamova_effectiveness_experiment.csv |
| Dissertation table | `complete` | /Users/cheenya/Projects/kraken-android-research-panel/reports/out/adamova_effectiveness_dissertation_table.md |
| C++ Adamova gate backend | `complete` | gate_backend=host_cpp_native_core_cli_from_android_source; native_cli_path=/Users/cheenya/Projects/kraken-android-research-panel/build/adamova-host/adamova_native_cli; native_cli_retention=generated_on_demand_disposable_build_output |
| Required corpus families | `complete` | accepted_control, downgrade, malformed, packet_mismatch, singular, size_guarded, small_torsion |
| Sage/reference controls | `complete` | sage_fixture_controls=3; generated_controls=7 |
| Weak profile acceptance reduced to zero | `complete` | no_precheck=8/10; discriminant_only=6/10; adamova=0/10 |
| Size/unsupported cases routed away from admission | `complete` | needs_reference_validation=2; size_guarded=1 |
| Thesis statement present | `complete` | In the controlled experimental-profile substitution model, Adamova admission gate reduced accepted weak/invalid profiles from 8/10 without precheck and 6/10 with discriminant-only precheck to 0/10, while routing size/unsupported cases to reference validation before session or message use. |
| Live Android evidence | `complete` | adb_device_connected=True; live_evidence_root=/Users/cheenya/Projects/kraken-android-research-panel/artifacts/android-adamova-live |

## Boundary

This audit checks whether the requested Adamova effectiveness evidence pack is present.
It does not turn the experiment into a production cryptographic security proof.
