# Dissertation Method Transfer

## Active Research Core

Use these local dissertation files as the source of truth:

- `/Users/cheenya/Documents/Диссертация/Статья Чистяков v2.pdf`
- `/Users/cheenya/Documents/Диссертация/НИР_предзащита_Чистяков Н.А.pdf`
- `/Users/cheenya/Documents/Диссертация/01_АУДИТ_БАЗИСА.md`

The earlier course work and first NIR are intentionally not part of the active
source pool.

## Method Summary

The method investigates rational elliptic curves in short Weierstrass form:

```text
E_{a,b}: y^2 = x^3 + ax + b
```

with integer coefficients and nonsingularity condition:

```text
4a^3 + 27b^2 != 0
```

The pipeline separates quick indicators from reference verification:

1. Stage A: fast arithmetic checks.
2. Stage B: reference torsion computation in SageMath for a selected control set.

The contribution is not a new theoretical torsion criterion. The contribution is
a reproducible implementation and evaluation workflow that applies known criteria,
adds diagnostics, and measures speed/coverage tradeoffs.

## Stage A: Fast Indicators

### 2-Torsion

For:

```text
f(x) = x^3 + ax + b
```

nontrivial points of order 2 have the form:

```text
(alpha, 0)
```

where `alpha` is a rational root of `f`. For a monic cubic with integer
coefficients, rational roots are integer roots. Therefore:

```text
c2 in {0, 1, 3}
```

where `c2` is the number of nontrivial rational 2-torsion points.

### 3-Torsion

For:

```text
E: y^2 = x^3 + ax + b
```

the third division polynomial is:

```text
psi_3(x) = 3x^4 + 6a x^2 + 12b x - a^2
```

A rational point of order 3 must satisfy:

```text
psi_3(x) = 0
y^2 = x^3 + ax + b
y != 0
```

For integral short Weierstrass models over `Q`, Lutz-Nagell motivates checking
integer torsion coordinates. Diagnostic rational candidates such as `x = d/3`
may remain useful for analysis, but the active classification must clearly state
which indicator it uses.

## A1..A6 Classification

The fast stage maps each curve into one of six diagnostic cases:

| Case | 3-torsion indicator | `c2` | Allowed `E(Q)_tors` types |
| --- | --- | --- | --- |
| A1 | yes | 0 | `Z/3Z`, `Z/9Z` |
| A2 | yes | 1 | `Z/6Z`, `Z/12Z` |
| A3 | yes | 3 | `Z/2Z x Z/6Z` |
| A4 | no | 0 | `{O}`, `Z/5Z`, `Z/7Z` |
| A5 | no | 1 | `Z/2Z`, `Z/4Z`, `Z/8Z`, `Z/10Z` |
| A6 | no | 3 | `Z/2Z x Z/2Z`, `Z/2Z x Z/4Z`, `Z/2Z x Z/8Z` |

Use this audited table, not old course-work versions.

## Stage B: Reference Verification

SageMath is used offline or in research tooling to compute:

- torsion subgroup invariants;
- torsion order;
- reference labels for calibration examples;
- disagreement reports between Stage A and Stage B.

Runtime messenger code should not depend on SageMath.

## Calibration Examples Already Checked

Local SageMath verification confirmed:

```text
(a,b) = (5568, 181456)       -> torsion (3,), point (12,500) has order 3
(a,b) = (-4275, 109198)      -> torsion (3,), point (27,116) has order 3
(a,b) = (-372, 2761)         -> torsion (6,), point (12,5) has order 3
(a,b) = (3411, 318069)       -> torsion (3,), point (3,573) has order 3
(a,b) = (-27, 55350)         -> torsion (5,)
(a,b) = (-3915, 113670)      -> torsion (5,)
(a,b) = (2160, 170640)       -> torsion (5,)
(a,b) = (-3483, 121014)      -> torsion (7,)
```

These examples are good initial test fixtures for a research module.

## Implementation Boundary

For this messenger repository, implement the method as a separable module:

```text
research/torsion/
```

Expected responsibilities:

- represent `E_{a,b}`;
- check nonsingularity;
- compute `c2`;
- compute the 3-torsion indicator;
- classify `A1..A6`;
- record candidate counts and filter diagnostics;
- export data for benchmark dashboards;
- optionally compare against SageMath-generated fixtures.

It should not:

- encrypt user messages directly;
- generate production messenger curves;
- silently replace standard ECDH/X25519/etc.;
- claim cryptographic security without a separate protocol specification.
