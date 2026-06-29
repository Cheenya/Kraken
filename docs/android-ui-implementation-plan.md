# Android UI Implementation Plan

This plan turns the design exploration results into a controlled production UI path. Experimental variants stay in UI Lab until a direction is explicitly selected and migrated screen by screen.

## Selected Direction

Recommended direction:

- Visual baseline: dark-first messenger UI with selective glass-like surfaces.
- Icons: Minimal Line for production navigation, Abyss / Kraken Geometry for brand moments and demo cards.
- Home: Messenger Hub with a first-run privacy onboarding block.
- Onboarding: Privacy-first Explanation followed by invite/import actions.
- Chat: Classic Messenger layout with Privacy State First relationship header.
- Realms: Realm Cards for production, Fido-like Network Board as a demo explanation layer.
- Settings: Privacy Control Center with a separate Developer / UI Lab section.

This keeps Kraken feeling like a private messenger while preserving visible research and mesh concepts for dissertation demonstration.

## Non-Negotiable Product Constraints

- No phone, email, login, password or account registration UI.
- No public discovery, nearby realm search, global realm search or public user directory.
- QR starts a handshake and does not grant membership directly.
- Sending remains allowed only in `ACTIVE` relationships.
- New key means new user.
- Display name is only a local label.
- Tombstone deletion is best-effort.
- Research surfaces must remain diagnostic-only and must not claim production encryption.
- Debug LAN and simulator views must stay clearly separated from ordinary user flows.

## Screen Implementation Order

1. Home
   - Move production Home toward Messenger Hub.
   - Keep identity summary at the top.
   - Show quick actions for My QR, Import Invite, Contacts and Realms.
   - Show compact counts for pending invites, active relationships, realms and complaints.
   - Show first-run privacy onboarding only when identity is missing.

2. Chat
   - Use Classic Messenger structure.
   - Add a clear relationship header with state, fingerprint and send eligibility.
   - Keep composer disabled unless relationship is `ACTIVE`.
   - Keep mesh/debug delivery panels out of the primary chat surface.

3. Create Identity and Welcome
   - Use Privacy-first Explanation.
   - Keep display name as the only input.
   - Make fingerprint visibility part of the success state.
   - Keep key compromise/recreate as warning-only until the actual lifecycle is designed.

4. My QR and Import Invite
   - Keep real QR rendering as the primary user-facing exchange path.
   - Keep payload JSON internal/debug-only, not a normal manual exchange UX.
   - Emphasize pending handshake result after import.

5. Contacts
   - Use chat-list density.
   - Promote peer display name, fingerprint and relationship state.
   - Keep handshake actions clear and local.

6. Realms
   - Convert production cards toward Realm Cards.
   - Keep capacity and local state visible.
   - No public join/search affordances.
   - Add optional Fido-like explanation in demo/help surface, not as default production list if it becomes visually heavy.

7. Settings
   - Reorganize into Identity, Privacy, Relay, Demo/Developer sections.
   - Keep UI Lab and demo data clearly marked as local developer tools.
   - Avoid mixing user settings with simulator/debug transport controls.

8. Research
   - Keep warning visible.
   - Present curve diagnostics, benchmark sample and export placeholder as research tools.
   - Do not imply production cryptography.

## Component Migration Plan

1. Stabilize shared components:
   - `KrakenScaffold`
   - `KrakenTopBar`
   - `KrakenBottomBar`
   - `ScreenContainer`
   - `HeroCard`
   - `ActionCard`
   - `InfoCard`
   - `WarningCard`
   - `StateBadge`
   - `EmptyState`
   - `LabeledValue`
   - `CopyableTextBlock`

2. Add production-ready domain cards:
   - `IdentitySummaryCard`
   - `RelationshipCard`
   - `RealmCard`
   - `InviteQrCard`
   - `QrScannerStateCard`
   - `MessageStatusLegend`

3. Keep experimental variants isolated under:
   - `ui/screens/experimental/`
   - `ui/icons/experimental/`

4. When a variant is selected:
   - Move only the reusable pieces into production components.
   - Keep the experimental original for comparison until manual screenshot review is complete.
   - Remove or archive stale variants only in a separate cleanup commit.

## Implementation Risks

- Visual density may drift toward a technical dashboard if mesh metrics dominate Home.
- Glass-like styling can reduce readability if contrast is not checked on a physical phone.
- Abyss / Kraken Geometry icons may be too abstract for bottom navigation without labels.
- Demo data can mislead if it is not clearly marked as local sample state.
- UI Lab can become a second app if experimental routes are not kept review-only.
- Debug LAN transport must not look like public discovery.

## Acceptance Criteria

- App opens in dark theme by default.
- Bottom navigation uses real icons and remains readable.
- Home reads as a messenger dashboard, not a policy document.
- Empty first-run state clearly points to local identity creation.
- My QR and Import Invite preserve pending-handshake semantics.
- Contacts show relationship state without raw dumps.
- Chat composer is disabled unless the relationship is `ACTIVE`.
- Realms show capacity and local state without any public discovery UI.
- Settings clearly separates user privacy controls from Developer / UI Lab.
- Research screen says diagnostic-only and not production encryption.
- UI compiles with `./gradlew test` and `./gradlew assembleDebug`.

## Screenshot Checklist

Capture portrait screenshots on a Pixel-class emulator or phone:

- Welcome without identity.
- Home without identity.
- Home with demo identity and demo relationships.
- Create Identity with empty field and with existing identity.
- My QR with real invite QR and lifecycle controls.
- QR Scanner with permission, invalid QR and successful pending import states.
- Import Invite as QR-first entry point.
- Contacts with pending, active and blocked relationships.
- Chat for active relationship.
- Chat for blocked or unlinked relationship.
- Realms with demo realm and capacity badge.
- Pending Approvals with empty and sample request states.
- Channels empty/demo state.
- Mesh Status with simulator and relay summaries.
- Settings with Developer / UI Lab section.
- Research diagnostic-only screen.
- UI Lab icon concepts.
- UI Lab Home variants.
- UI Lab Chat variants.

## Emulator And Device Testing Checklist

1. Build:
   - `cd app-android && ./gradlew test`
   - `cd app-android && ./gradlew assembleDebug`

2. Install:
   - `cd app-android && ./gradlew installDebug`

3. Device checks:
   - `adb devices -l`
   - confirm screen density and font scaling do not break cards.
   - confirm bottom navigation labels do not truncate.
   - confirm text fields remain usable with keyboard open.
   - confirm dark theme contrast in low brightness.

4. Flow checks:
   - create local identity.
   - show My QR on the first app state/device.
   - scan invite QR from a clean second app state/device.
   - verify imported invite is pending, not active.
   - complete response QR and final confirmation QR.
   - verify chat composer becomes enabled only after `ACTIVE`.
   - end relationship and verify composer disables.
   - create demo realm and verify capacity/local state.
   - load and reset demo data from UI Lab.

## Review Notes For Project Owner

- Recommended production direction: Messenger Hub + Classic Messenger chat with privacy state header.
- Recommended visual identity: dark teal glass-like surfaces with Minimal Line navigation icons and Abyss/Kraken Geometry accents.
- Keep Mesh Operations, Mesh Debug Hybrid and Fido-like Network Board as demo/research surfaces unless manual review shows they work for default users.
- Before replacing production screens broadly, choose one screen at a time and compare screenshots against the current UI Lab variants.
