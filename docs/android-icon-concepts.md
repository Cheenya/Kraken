# Android Icon Concepts

These icon concepts are experimental. They are isolated under `ui/icons/experimental/` and are not used in production bottom navigation unless explicitly selected later.

## Licensing And Reuse

- Icons are original Compose `ImageVector` definitions created for Kraken.
- No Telegram, Signal, SimpleX, Element or other messenger icons/assets were copied.
- If a future external icon dependency is considered, it must be permissively licensed and documented before adoption.

## Concept 1: Minimal Line

Visual intent:

- Thin rounded strokes.
- Clean messenger-like readability.
- Works well in bottom navigation and compact settings rows.
- Not playful.

Pros:

- Most conservative and easiest to read.
- Lowest visual noise.
- Good for production navigation.

Cons:

- Less distinctive.
- Marine/Kraken identity is subtle.

Recommended use cases:

- Bottom navigation.
- Dense contact/chat lists.
- Secondary actions.

Known limitations:

- Needs visual QA at small Android sizes, especially for QR and mesh icons.

## Concept 2: Glass Glyph

Visual intent:

- Filled glyphs with translucent-looking backing shape.
- Dark-theme friendly.
- Fits modern glass-like mobile surfaces.
- Better for primary actions than tiny navigation.

Pros:

- Stronger presence on dark UI.
- Reads well on action cards.
- Pairs with Liquid Glass-inspired panels.

Cons:

- Can feel heavy in bottom navigation.
- Needs careful tint/alpha tuning.

Recommended use cases:

- Home quick actions.
- Empty states.
- Hero/feature cards.

Known limitations:

- Current implementation approximates glass with alpha surfaces; it does not use real blur.

## Concept 3: Abyss / Kraken Geometry

Visual intent:

- Geometric marine feel.
- Subtle shield/coil structure.
- No mascot, no childish octopus, no copied logos.
- Most distinctive Kraken direction.

Pros:

- Strongest original identity.
- Fits dark teal/gold prototype style.
- Good for splash/hero/action surfaces.

Cons:

- Some symbols are less immediately conventional.
- Requires manual review for bottom navigation readability.

Recommended use cases:

- Brand-forward Home hero.
- Research/demo surfaces.
- Selected primary action icons after QA.

Known limitations:

- Should not be overused in dense lists until small-size readability is checked.

## Default Recommendation

Use `Minimal Line` for production navigation and dense UI, with `Abyss / Kraken Geometry` reserved for brand-forward action cards and demo surfaces.

If the project owner wants a stronger visual identity, the fallback recommendation is:

- Bottom nav: Minimal Line.
- Home hero and My QR: Abyss / Kraken Geometry.
- Primary quick actions: Glass Glyph.

## Manual Review Notes

Review on:

- Pixel 7/8 portrait.
- Dark theme.
- Bottom navigation at normal Android font scale.
- Home quick action cards.
- Empty states.

Watch for:

- tiny QR/import symbols collapsing;
- warning/lock ambiguity;
- mesh status icon becoming too abstract;
- Abyss icons feeling too decorative in production navigation.
