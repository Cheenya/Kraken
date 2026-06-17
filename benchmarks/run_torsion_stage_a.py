from __future__ import annotations

import argparse
from pathlib import Path

from disser_messenger.research.torsion.benchmark import run_jsonl


def main() -> None:
    parser = argparse.ArgumentParser(description="Run Stage A torsion diagnostics benchmark")
    parser.add_argument("--fixtures", type=Path, default=Path("tests/fixtures/torsion_examples.json"))
    parser.add_argument("--output", type=Path, default=Path("benchmarks/results/torsion_stage_a.jsonl"))
    args = parser.parse_args()
    run_jsonl(fixtures=args.fixtures, output=args.output)
    print(f"Wrote benchmark results to {args.output}")


if __name__ == "__main__":
    main()
