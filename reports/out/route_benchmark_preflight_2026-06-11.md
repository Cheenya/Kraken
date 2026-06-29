# Route Benchmark Preflight

Date: `2026-06-11`.

Devices:

- Samsung `R5CY22X6MSB` / `SM_S938B`
- Xiaomi `d948ffd0` / `2201122G`

Artifact:

- `artifacts/route-benchmark/20260611-002802-route-benchmark-preflight-2026-06-11/manifest.json`

Command:

```bash
scripts/run_route_benchmark_trials.sh \
  --device R5CY22X6MSB \
  --device d948ffd0 \
  --trials 3 \
  --label route-benchmark-preflight-2026-06-11 \
  --transport-profile all \
  --sync-attempts 3 \
  --sleep-between 1 \
  --min-samples-per-route 3
```

## Result

The runner executed on both phones, but did not close the benchmark gate:

| Route | Latency samples | Delivered records | Route attempt success/failure | Status |
| --- | ---: | ---: | ---: | --- |
| `lan-nsd-tcp` | 0 | 0 | 0/3 | `insufficient_n_for_reliability_claim` |

Overall status: `insufficient_n_for_reliability_claim`.

## Interpretation

This removes the earlier "runner not executed because only one phone was
visible" explanation. The current blocker is functional: the benchmark harness
can run on both phones, but the current route path does not produce delivered
latency samples.

This keeps the full `10/10` route reliability gate open. It also means there is
no basis for repeatable reliability claims from the latest benchmark preflight.

## Claim Boundary

This is a debug preflight for the benchmark harness. It does not prove route
reliability, production network reliability or production security.
