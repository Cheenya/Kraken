# Android Chat UX Variants

These chat variants are design exploration composables under `ui/screens/experimental/`. They are reachable through `Settings -> Developer / UI Lab` and do not replace the production Chat screen.

## Variant 1: Classic Messenger

Target:

- Make Kraken feel like a private messenger first.

Strengths:

- Familiar message bubble layout.
- Compact relationship header.
- Composer placement matches user expectations.
- Status legend stays visible without dominating the screen.

Weaknesses:

- Mesh/delivery research state is less prominent.
- Needs careful handling before real sending exists.

Rules preserved:

- Composer is enabled only for `ACTIVE`.
- Placeholder copy says real sending is not implemented.

Recommendation:

- Use this as the base production chat direction.
- Add a privacy state header from the second variant.

## Variant 2: Privacy State First

Target:

- Make relationship state, fingerprint and disabled/rejoin behavior impossible to miss.

Strengths:

- Strong privacy clarity.
- Good for unlink, blocked-by-peer and rejoin-required states.
- Reduces risk of misleading users before active relationship.

Weaknesses:

- Less conversational and more warning-heavy.
- May feel too defensive as the default chat screen.

Rules preserved:

- Non-active relationships clearly disable composer.
- Rejoin/new invite requirement remains visible.

Recommendation:

- Use its header pattern for pending/unlinked/blocked states.

## Variant 3: Mesh Debug Hybrid

Target:

- Dissertation/demo mode showing packet/delivery concepts beside chat UI.

Strengths:

- Shows encrypted payload placeholder, copy budget, TTL and tombstone concepts.
- Useful for explaining simulator and transit buffer behavior.

Weaknesses:

- Too technical for normal users.
- Should not become the default messenger UI.

Rules preserved:

- No real sending.
- No production delivery claim.
- Tombstone copy says best-effort honest relay cleanup.

Recommendation:

- Keep as Research/Mesh demo support, not production chat default.

## Overall Recommendation

Use `Classic Messenger` as the primary chat layout, with a `Privacy State First` relationship header for non-active and risky states. Keep `Mesh Debug Hybrid` behind UI Lab, Research or Mesh Status demo surfaces.

Manual review should cover:

- empty relationships;
- pending handshake;
- active relationship;
- unlinked relationship;
- blocked by peer;
- large font scale;
- Pixel 7/8 portrait.
