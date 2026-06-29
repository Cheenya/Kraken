# Android Report Exporter Usage

## Purpose

The Android report exporter converts Python math-core curve diagnostics into the JSON contract consumed later by the Android Research panel.

It is an offline bridge:

- Python generates the report on a workstation;
- Android reads a bundled asset or manually imported JSON;
- Android does not execute Python;
- no server, cloud, networking, account, or production cryptography is introduced.

## Contract

Current report version:

```text
kraken.math.curve_diagnostic.android.v1
```

Contract documentation:

```text
docs/math-report-android-contract.md
```

Example report:

```text
reports/examples/sample_curve_diagnostic_report.json
```

## Generate a report from coefficients

```bash
cd /Users/cheenya/Projects/disser-messenger-project
.venv/bin/python -m disser_messenger.cli export-android-curve-report \
  --a -1 \
  --b 0 \
  --output reports/out/android_curve_report.json
```

The coefficients are parsed with exact rational arithmetic through `Fraction`.
For rational values:

```bash
.venv/bin/python -m disser_messenger.cli export-android-curve-report \
  --a -1/4 \
  --b 0 \
  --output reports/out/android_fractional_curve_report.json
```

## Generate a report from a fixture

```bash
cd /Users/cheenya/Projects/disser-messenger-project
.venv/bin/python -m disser_messenger.cli export-android-curve-report \
  --fixture tests/fixtures/elliptic_curves.json \
  --curve-id full_two_torsion_x3_minus_x \
  --output reports/out/android_curve_report.json
```

## Python API

```python
from pathlib import Path

from disser_messenger.math_core import RationalEllipticCurve
from disser_messenger.math_core.android_export import (
    build_android_curve_report,
    write_android_curve_report,
)

report = build_android_curve_report(RationalEllipticCurve(a=2, b=3))
write_android_curve_report(report, Path("reports/out/android_curve_report.json"))
```

## How Android will consume it later

After the Android branch is merged, selected reports can be bundled under:

```text
app-android/app/src/main/assets/research/
```

The Android Research screen should:

- parse the JSON as data, not executable logic;
- show curve equation, invariants, diagnostics, warnings, unsupported cases, and benchmark timing;
- show the diagnostic-only warning prominently;
- reject or clearly mark unsupported `report_version` values;
- avoid production security claims.

## Limitations

- The report is diagnostic-only.
- The report is not message encryption.
- The report does not prove Kraken production security.
- Lutz-Nagell output is a candidate diagnostic, not a full torsion proof.
- Torsion probing is bounded and can return unknown.
- Android-side interactive diagnostics require a future C++/JNI migration with parity tests.

## Validation

Run:

```bash
python3 -m compileall .
.venv/bin/python -m pytest
git diff --check
```
