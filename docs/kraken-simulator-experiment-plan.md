# Kraken Simulator Experiment Plan

This plan defines dissertation-friendly local experiments for the Kraken store-carry-forward simulator. It is not a production network benchmark.

## Purpose

Evaluate how bounded gossip / Spray-and-Wait-inspired parameters affect local delivery, overhead, storage pressure and cleanup in an accountless invite-only messenger model.

## Baseline Scenario

Nodes:

- `A`: sender.
- `Y`: intended recipient.
- `B`, `C`: honest relays.

Flow:

1. `A` creates a packet for `Y`.
2. `A` meets `B`.
3. `B` meets `C`.
4. `C` meets `Y`.
5. `Y` receives packet.
6. `Y` creates delivery receipt.
7. Receipt returns to `A`.
8. `A` creates tombstone.
9. Tombstone clears packet copies from honest relay buffers.

## Controlled Parameters

| Parameter | Values |
| --- | --- |
| Copy budget | 2, 4, 8, 16 |
| TTL | 1h, 6h, 24h |
| Reserve copy | off, on |
| Relay score | off, on |
| Encounter probability | low, medium, high |
| Battery limits | off, on |
| Communication shape | direct, channel, small group |
| Tombstone | disabled, enabled |

## Metrics

| Metric | Definition |
| --- | --- |
| Delivery rate | Delivered packets divided by created packets. |
| Delivery latency | Time from packet creation to recipient delivery. |
| Copy overhead | Total packet copies created and forwarded per delivered packet. |
| Storage pressure | Peak and average transit buffer occupancy per node. |
| Relay load | Forwarding events and bytes per relay node. |
| Battery cost estimate | Forwarding windows, scan windows and byte budget consumed. |
| Tombstone cleanup effectiveness | Honest relay copies removed after tombstone propagation. |
| Expiration waste | Packets expired before delivery. |
| Duplicate suppression rate | Duplicate packets ignored by transit buffers. |

## Experiment 1: Copy Budget Sweep

Goal: compare delivery and overhead for copy budgets 2, 4, 8 and 16.

Fixed settings:

- TTL: 6h.
- Reserve copy: on.
- Relay score: off.
- Encounter probability: medium.
- Battery limits: off.
- Tombstone: enabled.

Acceptance criteria:

- Report delivery rate, latency and copy overhead per copy budget.
- Identify diminishing returns point, if visible.

## Experiment 2: TTL Sweep

Goal: estimate the effect of packet lifetime.

Values:

- 1h.
- 6h.
- 24h.

Fixed settings:

- Copy budget: 8.
- Reserve copy: on.
- Encounter probability: medium.
- Tombstone: enabled.

Acceptance criteria:

- Report delivery rate vs storage pressure.
- Report expiration waste.

## Experiment 3: Reserve Copy Policy

Goal: test whether holding the last reserve copy protects delivery.

Values:

- reserve copy off.
- reserve copy on.

Fixed settings:

- Copy budget: 4 and 8.
- TTL: 6h.
- Encounter probability: low and medium.

Acceptance criteria:

- Compare delivery rate under sparse encounters.
- Report whether reserve copy increases latency or reduces relay load.

## Experiment 4: Relay Score

Goal: compare forwarding with and without local relay score.

Values:

- relay score off.
- relay score on.

Fixed settings:

- Copy budget: 8.
- TTL: 6h.
- Reserve copy: on.
- Encounter probability: medium.

Acceptance criteria:

- Report useful relay confirmation rate.
- Verify that low-score relay does not receive last reserve copy.
- Confirm no route/recipient details are exposed in Courier Score outputs.

## Experiment 5: Encounter Probability

Goal: model sparse, normal and dense contact patterns.

Values:

- low.
- medium.
- high.

Fixed settings:

- Copy budget: 8.
- TTL: 6h and 24h.
- Reserve copy: on.
- Battery limits: off.

Acceptance criteria:

- Report delivery rate and latency distribution.
- Report copy overhead under high encounter density.

## Experiment 6: Battery Limits

Goal: estimate user-visible relay mode tradeoffs.

Values:

- battery limits off.
- battery limits on.

Policy variants:

- only my messages.
- help a little.
- help on charging/Wi-Fi.
- active courier.
- research mode.

Acceptance criteria:

- Report forwarding events suppressed by battery policy.
- Report battery cost estimate proxy.
- Confirm only-my-messages mode does not transit-forward third-party packets.

## Experiment 7: Channels Vs Small Groups

Goal: compare wider communication through channel-like latest-N delivery against limited small groups.

Values:

- channel latest-N: 10, 25, 50.
- small group max members: 5, 10.

Fixed settings:

- Copy budget: 8.
- TTL: 6h.
- Encounter probability: medium.

Acceptance criteria:

- Report storage pressure and relay load.
- Confirm small group member cap is enforced.
- Confirm no public discovery assumptions are introduced.

## Experiment 8: Tombstone Cleanup

Goal: measure cleanup effectiveness for honest relays and model malicious ignored tombstones separately.

Values:

- tombstone disabled.
- tombstone enabled, all honest relays.
- tombstone enabled, one non-honest relay.

Fixed settings:

- Copy budget: 8.
- TTL: 24h.
- Encounter probability: medium.

Acceptance criteria:

- Report honest buffer cleanup rate.
- Report remaining copies on non-honest relay separately.
- State that deletion is best-effort, not guaranteed.

## Output Format

Each run should emit a JSON result and a compact Markdown/CSV summary:

```json
{
  "experiment_id": "copy-budget-sweep",
  "parameters": {
    "copy_budget": 8,
    "ttl_hours": 6,
    "reserve_copy": true,
    "relay_score": false,
    "encounter_probability": "medium",
    "battery_limits": false,
    "tombstone": true
  },
  "metrics": {
    "delivery_rate": 0.0,
    "median_latency_seconds": 0,
    "copy_overhead": 0.0,
    "peak_storage_pressure": 0,
    "relay_load_events": 0,
    "battery_cost_estimate": 0.0,
    "tombstone_cleanup_effectiveness": 0.0
  }
}
```

## Reproducibility Rules

- Use deterministic random seeds per run.
- Store seed, parameter set and simulator version with each result.
- Keep experiments local and dependency-light.
- Do not use real personal data.
- Do not imply that simulated performance equals real-world deployment performance.

## Dissertation Notes

Useful report framing:

- The simulator explores parameter tradeoffs in a research prototype.
- Delivery, latency and overhead are model outputs under controlled assumptions.
- Security/privacy claims require separate cryptographic and transport review.
- Tombstone cleanup should always be reported as honest-node best-effort cleanup.
