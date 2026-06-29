# Mesh Messenger Integration Plan

## Intended Architecture

Separate the messenger into three layers:

1. Messaging layer
   - peers, contacts, routing, queues, delivery receipts;
   - message/session lifecycle.

2. Cryptographic runtime layer
   - production-safe key agreement and symmetric encryption;
   - key rotation and session state;
   - serialization and validation.

3. Research/metrics layer
   - torsion-acceleration experiments;
   - key-formation timing;
   - validation overhead;
   - dashboards and exportable benchmark data.

The dissertation method belongs to layer 3 until a separate cryptographic protocol
is designed and audited.

## Research Module Placement

Proposed future tree:

```text
src/
  messenger/
    peer/
    mesh/
    sessions/
    messages/
  crypto/
    runtime/
    validation/
    metrics/
  research/
    torsion/
      curve.ts
      stage-a.ts
      classifier.ts
      fixtures/
      benchmarks/
docs/
```

Language and framework are not chosen yet. The module boundaries are intentionally
language-neutral.

## Key Formation Scopes To Measure

### Message Key

Per-message key material or nonce-related derivation.

Metrics:

- generation time;
- derivation time;
- serialization size;
- failure/retry count;
- entropy source timing if applicable.

### Pair Session Key

Key material established between two peers.

Metrics:

- local ephemeral key generation time;
- remote key validation time;
- ECDH/key-agreement time;
- KDF time;
- total session establishment time;
- bytes exchanged before first encrypted message.

### Mesh/Group Session Key

Group or route-level session material.

Metrics:

- number of peers;
- number of pairwise agreements;
- total fanout time;
- rekey time when a peer joins/leaves;
- per-peer validation cost;
- aggregate message overhead.

## Where Torsion Diagnostics Fit

The torsion module can support:

- offline parameter experiments;
- research dashboards comparing fast filters against reference verification;
- validation-cost visualization;
- demos showing how extra arithmetic checks affect setup time;
- dissertation figures/tables generated from messenger-like scenarios.

It should not be in the hot path of real message encryption unless we explicitly
create a research mode.

## Runtime Modes

### Safe Prototype Mode

Default mode. Uses standard, well-reviewed primitives for message/session crypto.
The torsion module only records research metrics separately.

### Research Mode

Allows experimental curve-parameter checks and torsion classification. Must be
visibly marked as non-production.

### Benchmark Mode

Runs repeatable key-formation and torsion-classification benchmarks without
sending real messages.

## Integration Milestones

1. Create a standalone torsion research module with fixtures from the dissertation.
2. Add benchmark harness for Stage A classification.
3. Add key-formation metrics model independent of cryptographic implementation.
4. Implement a minimal local messenger session handshake using safe primitives.
5. Add instrumentation around session/message key formation.
6. Add a dashboard or report view for benchmark results.
7. Connect dissertation experiments to messenger-style scenarios.
