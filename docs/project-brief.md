# Project Brief

## Goal

Build a future mesh messenger prototype that is connected to the dissertation
research on accelerated investigation of torsion structures of rational elliptic
curves.

The messenger should eventually support:

1. peer-to-peer message exchange;
2. session establishment between peers;
3. mesh/group routing experiments;
4. instrumentation for key-formation speed;
5. research views for comparing cryptographic parameter checks and torsion-related
   diagnostics.

## Dissertation Connection

The dissertation is centered on the method from `Статья Чистяков v2.pdf`:

> a reproducible two-stage computational pipeline for fast preliminary checking
> of small-order torsion indicators on rational elliptic curves, with reference
> verification in SageMath.

The messenger project should reuse this work as a research and diagnostics layer,
not as an unaudited replacement for standard production cryptography.

## Product Direction

The intended application is a mesh messenger where the user can inspect metrics
around:

- key generation for an individual message;
- session key establishment between two peers;
- group/session key lifecycle;
- cryptographic validation stages;
- timing impact of extra validation or filtering;
- failure/skip reasons when curve or key material is rejected.

## Non-Goals For Now

- Do not invent a production-grade custom cryptosystem.
- Do not use experimental rational-curve torsion checks as the sole security
  mechanism for real message encryption.
- Do not optimize before the measurement model is stable.
- Do not treat SageMath output as a runtime dependency for a mobile/desktop
  messenger. SageMath is a reference/offline verification tool.

## Working Assumption

The first useful version should be a local research prototype:

- standard messenger cryptographic primitives for actual message/session keys;
- a separate torsion-research module for experiments and parameter exploration;
- built-in telemetry around key formation and validation costs.
