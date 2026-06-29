# Android Home UX Variants

These Home variants are implemented as isolated Compose demo composables under `ui/screens/experimental/`. They are available through `Settings -> Developer / UI Lab` for local review and do not replace the production Home screen.

## Variant 1: Messenger Hub

Target user:

- A normal user who expects a private messenger first.

Strengths:

- Recent relationships and contacts are the first visible content.
- Identity remains visible but not dominant.
- Quick actions stay compact.
- Best fit once real chats exist.

Weaknesses:

- Early prototype may look empty without demo data.
- Realm/mesh concepts are secondary.

Best demo scenario:

- Showing that Kraken behaves like a messenger with invite-only contact states.

Screenshot/manual testing notes:

- Test with no relationships and with demo data.
- Check state badge readability in contact rows.

## Variant 2: Privacy Onboarding Dashboard

Target user:

- New user before they understand local identity and invite-only contact flow.

Strengths:

- Makes local identity and no-account rules explicit.
- Good before real conversations exist.
- Explains QR/import as a sequence without walls of text.

Weaknesses:

- Less messenger-like after onboarding.
- Can become repetitive for returning users.

Best demo scenario:

- First-run walkthrough: display name, fingerprint, QR handshake, pending import.

Screenshot/manual testing notes:

- Check that "new key means new user" appears in nearby onboarding copy if promoted to production.

## Variant 3: Mesh Operations Dashboard

Target user:

- Technical reviewer or dissertation committee member.

Strengths:

- Surfaces realm, relay, capacity and complaint counts.
- Useful for explaining mesh model and simulator work.
- Strong demo clarity for non-chat subsystems.

Weaknesses:

- Feels like an admin panel if used as default.
- Can distract from "private messenger" positioning.

Best demo scenario:

- Technical dissertation walkthrough after basic identity/invite/chat flow.

Screenshot/manual testing notes:

- Use only as a secondary dashboard or Research/Mesh status support view.

## Variant 4: Liquid Glass Inspired

Target user:

- Product/design review where Kraken needs stronger visual identity.

Strengths:

- Most modern visual direction.
- Works with dark theme and bright teal accent.
- Can pair with Abyss/Kraken geometry icons.

Weaknesses:

- Risk of over-styling a research prototype.
- Needs careful contrast testing on phone screens.
- No real blur is implemented; it approximates glass with layered dark surfaces.

Best demo scenario:

- Screenshots and visual direction discussion.

Screenshot/manual testing notes:

- Test in Pixel 7/8 portrait with normal and large font scale.
- Watch for cards becoming too decorative or hiding practical state.

## Recommendation

Primary direction:

- `Messenger Hub` as the long-term Home structure.
- Add a small `Privacy Onboarding` block only when identity is missing.
- Keep `Mesh Operations Dashboard` content in Mesh Status / Research.
- Use `Liquid Glass Inspired` visual treatment selectively, not as dense dashboard architecture.

This matches the product goal: Kraken should feel like a private messenger with research/mesh capability under the surface, not a raw mesh protocol console.
