# Route Benchmark Runner

Date: 2026-06-10.

Script: `scripts/run_route_benchmark_trials.sh`.

## Purpose

The runner prepares repeatable N-run route evidence collection. It repeatedly
calls `scripts/capture_debug_route_evidence.sh`, then aggregates all captured
`route_specific_evidence_latest.json` files through
`scripts/build_route_benchmark_summary.py`.

This addresses the process gap behind the current route benchmark blocker:
manual smoke samples exist, but the project still needs repeated comparable
runs before it can claim latency/loss/retry reliability.

## Default Contract

- Runner requires at least two ADB devices connected at the same time.
- Default trials: `10`.
- Default minimum sample gate per route: `10`.
- Outputs are written under ignored `artifacts/route-benchmark/`.
- The summary remains descriptive until every claimed route passes the minimum
  sample gate.

Example command for the next two-device pass:

`scripts/run_route_benchmark_trials.sh --device R5CY22X6MSB --device d948ffd0 --label route-benchmark-after-wifi-direct-diagnostics --transport-profile all --trials 10 --min-samples-per-route 10`

## Current Status

Runner exists and has now been executed on two ADB devices. The latest preflight
is recorded in `reports/out/route_benchmark_preflight_2026-06-11.md`.

That preflight does not close reliability evidence: it produced zero delivered
latency samples and the summary status remained
`insufficient_n_for_reliability_claim`.

## Claim Boundary

This runner does not prove reliability by itself. It only makes the benchmark
capture repeatable. It does not close reliability evidence until fresh
two-device captures show each claimed route passing the configured minimum
sample gate.
