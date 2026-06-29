# Development Quick Start

## Local setup

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e .[dev]
pytest
python benchmarks/run_torsion_stage_a.py
```

On Windows PowerShell:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .[dev]
pytest
python benchmarks/run_torsion_stage_a.py
```

## Current MVP

The current executable slice contains:

- a rational curve model;
- fast Stage A torsion diagnostics;
- A1..A6 diagnostic classification;
- verified fixture examples;
- JSONL benchmark export;
- local mesh key graph metrics.

## Output

The benchmark script writes JSONL rows to:

```text
benchmarks/results/torsion_stage_a.jsonl
```

Generated benchmark files are ignored by Git.
