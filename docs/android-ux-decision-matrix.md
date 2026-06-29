# Android UX Decision Matrix

This matrix compares design exploration directions without replacing the current production UI. Scores use `1` low to `5` high.

## Evaluation Criteria

- Messenger feel: how much the direction feels like a private messenger rather than a protocol console.
- Privacy clarity: how clearly it explains local identity, invite-only entry and relationship state.
- Dissertation demo clarity: how well it supports committee walkthroughs.
- Implementation complexity: higher score means easier to implement and maintain.
- Visual originality: how distinct it feels for Kraken.
- Maintainability: how likely the UI is to stay coherent as features grow.
- Risk of misleading users: higher score means lower risk.
- Kraken alignment: fit with no account, no public discovery, direct messages primary and research mode under the surface.

## Icon Sets

| Icon Set | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Minimal Line | 5 | 4 | 4 | 5 | 3 | 5 | 5 | 5 | Best for production bottom navigation and dense UI. |
| Glass Glyph | 4 | 4 | 4 | 4 | 4 | 4 | 4 | 4 | Good for Home actions and empty states. |
| Abyss / Kraken Geometry | 3 | 4 | 5 | 3 | 5 | 3 | 4 | 5 | Strongest identity; needs readability QA before nav use. |

Recommendation:

- Default: `Minimal Line` for navigation.
- Accent: `Abyss / Kraken Geometry` for brand-forward cards.
- Fallback: `Glass Glyph` for Home primary action cards.

## Home Variants

| Home Variant | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Messenger Hub | 5 | 4 | 4 | 4 | 3 | 5 | 5 | 5 | Best production target once demo data/relationships exist. |
| Privacy Onboarding Dashboard | 3 | 5 | 4 | 5 | 3 | 4 | 5 | 5 | Best first-run/empty-state layer, not long-term dashboard. |
| Mesh Operations Dashboard | 2 | 4 | 5 | 4 | 3 | 4 | 4 | 4 | Useful for Mesh Status/Research, not default Home. |
| Liquid Glass Inspired | 4 | 4 | 4 | 3 | 5 | 3 | 4 | 5 | Strong visual direction, needs contrast and density QA. |

Recommendation:

- Primary: `Messenger Hub`.
- First-run layer: `Privacy Onboarding Dashboard`.
- Visual treatment: selective `Liquid Glass Inspired`.
- Move `Mesh Operations Dashboard` ideas to Mesh Status/Research.

## Onboarding Variants

| Onboarding Variant | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Fast Start | 5 | 3 | 3 | 5 | 3 | 5 | 4 | 4 | Good once user understands Kraken. |
| Privacy-first Explanation | 4 | 5 | 5 | 4 | 4 | 5 | 5 | 5 | Best default onboarding for current prototype. |
| Invite-first Mental Model | 4 | 5 | 4 | 4 | 4 | 4 | 5 | 5 | Useful after identity creation, especially QR/import split. |

Recommendation:

- Default: `Privacy-first Explanation`.
- Follow-up action split: `Invite-first Mental Model`.

## Chat Variants

| Chat Variant | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Classic Messenger | 5 | 4 | 4 | 4 | 3 | 5 | 4 | 5 | Best production base. |
| Privacy State First | 3 | 5 | 5 | 4 | 4 | 4 | 5 | 5 | Best for pending/unlinked/blocked states. |
| Mesh Debug Hybrid | 2 | 4 | 5 | 3 | 4 | 3 | 4 | 4 | Keep behind demo/research surfaces. |

Recommendation:

- Production: `Classic Messenger` with a `Privacy State First` header.
- Demo: keep `Mesh Debug Hybrid` in UI Lab / Mesh Status / Research.

## Realm Variants

| Realm Variant | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Realm List | 3 | 5 | 4 | 5 | 2 | 5 | 5 | 5 | Conservative and easy to ship. |
| Realm Cards | 4 | 5 | 4 | 4 | 3 | 4 | 5 | 5 | Good production direction with capacity/state badges. |
| Fido-like Network Board | 3 | 4 | 5 | 3 | 5 | 3 | 4 | 5 | Best explanatory metaphor for dissertation demo. |

Recommendation:

- Production: `Realm Cards`.
- Demo/explanation: `Fido-like Network Board`.

## Settings Variants

| Settings Variant | Messenger Feel | Privacy Clarity | Demo Clarity | Complexity | Originality | Maintainability | Low Misleading Risk | Kraken Alignment | Notes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Simple User Settings | 5 | 4 | 3 | 5 | 2 | 5 | 5 | 5 | Good default for normal users. |
| Privacy Control Center | 4 | 5 | 5 | 4 | 4 | 5 | 5 | 5 | Best fit for Kraken identity/fingerprint model. |
| Developer Demo Settings | 2 | 4 | 5 | 4 | 3 | 4 | 4 | 4 | Keep clearly separated under Developer/UI Lab. |

Recommendation:

- Default: `Privacy Control Center`.
- Separate `Developer / UI Lab` section for demo data, UI variants and debug transport notes.

## Primary Direction

Recommended Kraken direction:

- Visual: `Liquid Glass Inspired` selectively, not everywhere.
- Icons: `Minimal Line` for navigation, `Abyss / Kraken Geometry` for identity/brand moments.
- Home: `Messenger Hub`.
- Onboarding: `Privacy-first Explanation`.
- Chat: `Classic Messenger` with `Privacy State First` header.
- Realms: `Fido-like Network Board` as demo metaphor, `Realm Cards` for production list.
- Settings: `Privacy Control Center` with separate Developer section.

This aligns with the principle: Kraken should feel like a private messenger with research and mesh systems underneath, not like a raw mesh-protocol admin panel.

## Fallback Conservative Direction

If visual risk or timeline is tight:

- Icons: `Minimal Line` only.
- Home: current production Home evolved toward `Messenger Hub`.
- Chat: current production Chat refined with `Privacy State First` state card.
- Realms: `Realm List`.
- Settings: `Simple User Settings`.

## What To Implement Next

1. Convert production Home to `Messenger Hub` structure with first-run privacy onboarding block.
2. Update production Chat to `Classic Messenger` plus `Privacy State First` header for non-active states.
3. Keep `UI Lab` as review-only and add screenshots before replacing more production screens.
4. Move mesh/debug content out of Home and into Mesh Status / Research.
5. Iterate icon tint/size on a real Pixel-class portrait screen before switching production icon style.
