# Next Steps

Use this as the continuation point for the next work session.

## Immediate Decisions

1. Choose the prototype stack:
   - CLI research harness first;
   - desktop app;
   - web app;
   - mobile-first messenger.
2. Choose the safe-mode crypto baseline for actual message/session keys.
3. Decide whether the first repository milestone should be:
   - torsion research module;
   - messenger skeleton;
   - benchmark harness;
   - documentation-to-code scaffold.

## First Implementation Slice

Recommended first slice:

1. Create `src/research/torsion`.
2. Implement a small pure runtime model for:
   - curve parameters `a,b`;
   - nonsingularity check;
   - 2-torsion root count;
   - 3-torsion division-polynomial indicator;
   - `A1..A6` classification.
3. Add fixtures from the verified calibration examples.
4. Add a benchmark runner that emits JSON Lines.
5. Keep SageMath only as an offline fixture generator/checker.

## First Messenger Slice

Recommended second slice:

1. Create a local peer/session model without networking.
2. Add session key formation with safe standard primitives.
3. Instrument:
   - keypair generation;
   - remote key validation;
   - key agreement;
   - KDF;
   - message key derivation.
4. Export metrics in the same JSON Lines style as the torsion benchmarks.

## Dissertation Output Slice

Once the harness exists, generate:

1. a table comparing Stage A and reference checks;
2. timing summaries for key formation by scope;
3. a small explanation of why the torsion method is useful as validation/diagnostics
   instrumentation in a messenger-like setting.

## Guardrails

- Do not use custom experimental curves for actual message encryption.
- Do not put SageMath in runtime messenger dependencies.
- Do not claim production security from timing improvements.
- Keep "research mode" separate from "safe prototype mode".
