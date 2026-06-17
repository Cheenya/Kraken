# Publication Manifest

Source snapshots:

```text
kraken-android-research-panel: 74065b8d8e49b6a84bcc9543400a442d48a2472a
disser-messenger-project: b7ece69aca9fbe837682a4487fca99f693a64d50
```

Publication date:

```text
2026-06-17
```

## Included

- `app-android/`: Android source, Gradle wrapper, Kotlin/C++ sources, Android tests and bundled research assets.
- `app/src/main/assets/research/`: dissertation-path mirror for Android-compatible research assets.
- `app-macos/`: SwiftPM macOS desktop harness source and build script.
- `src/`: Python research and mesh support modules.
- `benchmarks/`: benchmark entrypoint for torsion stage A.
- `docs/`: technical boundary documents required by Android doc-contract tests.
- `protocol-spec/`: protocol notes and schema documents.
- `scripts/adamova_effectiveness_experiment.py`: reproducible effectiveness report generator.
- `scripts/audit_adamova_effectiveness_evidence.py`: report consistency audit for the effectiveness experiment.
- `scripts/kraken_desktop_relay_preflight.py`: local desktop relay decision preflight used by the macOS harness.
- `tests/`: Python tests for the published Python modules and selected report generator.
- `reports/examples/`: sample diagnostic JSON report.
- `reports/out/`: selected technical reports listed in `README.md`.
- `reports/out/sage_validation/`: SageMath validation package from the dissertation source tree.
- `reports/out/large_coefficient_sage_validation/`: large-coefficient SageMath validation package from the dissertation source tree.
- `artifacts/research_backend_benchmark/`: Kotlin/C++ benchmark report.
- `downloads/`: packaged APK, macOS app archive and checksums.

## Excluded

- local service files;
- branch-maintenance notes;
- broad internal audit logs not needed for the published technical package;
- raw phone capture directories;
- generated build directories;
- local environment metadata;
- personal dissertation PDFs and local source documents.

## Path Root

All published paths are relative to the root of this repository.

The dissertation source document referenced paths such as `reports/out/...`, `artifacts/...` and `app/src/...` without naming the root directory. This package makes that root explicit by placing the selected materials under the same relative paths inside the public repository.
