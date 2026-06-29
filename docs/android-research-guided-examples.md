# Android Research Guided Examples

## Purpose

The Research Panel now separates curve examples into tiers so the demo does not imply that small hand-checkable curves represent cryptographic-scale complexity.

The panel is diagnostic-only. It visualizes outputs from the research math-core and local Android MVP checks. It is not production encryption and does not prove Kraken message security.

## Example Tiers

### Teaching

Teaching examples explain mechanics on simple curves:

- `y^2 = x^3 - x` demonstrates full rational 2-torsion.
- `y^2 = x^3` demonstrates singular-curve rejection.

These examples are marked `teaching_only=true`. They are useful for explanation and sanity checks, but they must not be presented as cryptographic-scale examples.

### Validation

Validation examples are backed by bundled math-core JSON reports and SageMath comparison status from the evidence pack:

- no rational 2-torsion example;
- Lutz-Nagell candidate / bounded probing example;
- direct-match examples where the current evidence pack reports SageMath agreement.

The UI uses `SageMath direct match` only for examples with bundled reports selected from the validated fixture corpus.

### Research-scale

Research-scale examples use larger integer coefficients to stress Android-side display and local diagnostic flow. They are closer to research/benchmark input shape, but they are still curves over `Q`.

The current Android Research-scale tier uses selected reports from the
large-coefficient evidence corpus:

- `lc32_prime_offsets_no_two_torsion`;
- `lc64_scaled_order_four`;
- `lc128_large_a_small_b_no_two_torsion`;
- `lc128_scaled_order_four`;
- `lcstruct_lutz_large_discriminant_stress`.

Each selected example is bundled as Android-compatible JSON and marked:

- `validation_status=SageMath direct match`;
- `production_crypto_claim=false`;
- `teaching_only=false`;
- caveat: rational diagnostic over `Q`, not production finite-field crypto.

The previous single pending example `y^2 = x^3 + 65537x + 104729` is now backed
by the validated `lc32_prime_offsets_no_two_torsion` report instead of being a
local-only pending sample.

## Large Coefficient Corpus Summary

The bundled Research-scale examples are drawn from a dedicated evidence corpus:

| Metric | Value |
| --- | ---: |
| Large-coefficient fixtures | 20 |
| Curves compared with SageMath | 20 |
| Direct matches | 20 |
| Mismatches | 0 |
| Benchmark runs | 5 |
| Median total runtime, ms | 22.8673 |
| P95 total runtime, ms | 24.1665 |

This summary belongs in the Research Panel as a compact context card. It should
be read as a reproducible diagnostic workflow result, not as a production
cryptography claim.

## Why Small Curves Are Teaching-Only

Curves such as `y^2 = x^3 - x` have obvious rational roots and are easy to verify by hand. They are valuable for showing the pipeline, but they do not demonstrate difficult input size or cryptographic workflow impact.

The UI therefore labels them as teaching examples and keeps research-scale examples in a separate tier.

## Why Large Coefficients Matter

Larger coefficients help test:

- exact integer parsing and display;
- discriminant and diagnostic stability;
- report framing for benchmark-style inputs;
- future fixture expansion toward larger input families.

They do not, by themselves, prove cryptographic usefulness or production
security. The current corpus is deterministic and curated; it is not a
statistical sample of all rational curves.

## Rational Diagnostics Over Q vs Production Cryptography

The current math-core works with rational elliptic curves in short Weierstrass form over `Q`.

Production elliptic-curve cryptography usually works over finite fields and requires reviewed cryptographic libraries, protocol analysis, side-channel review, and key-management design. The Research Panel must not blur these two contexts.

Safe wording:

> Large-coefficient diagnostic stress example over Q; not a production finite-field cryptographic curve.

## Adding More Research-Scale Examples

Preferred source:

1. Add fixtures in the math-core evidence branch.
2. Generate Android-compatible reports with the Python exporter.
3. Run SageMath reference comparison when possible.
4. Copy only selected generated JSON reports into:
   `app-android/app/src/main/assets/research/examples/`
5. Update:
   `app-android/app/src/main/assets/research/examples/manifest.json`
6. Update the `large_coefficient_validation` summary if the corpus numbers
   change.

Each manifest row must keep:

- `production_crypto_claim=false`;
- `validation_status` accurate;
- `teaching_only=true` for small explanation examples;
- no production security claims.
