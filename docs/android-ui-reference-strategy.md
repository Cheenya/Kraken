# Android UI Reference Strategy

## Purpose

This document defines the reference strategy for the Kraken Android prototype UI. It keeps future UI work aligned with Kraken's product rules while allowing the app to become usable and visually coherent before transport, governance, and production cryptography are added.

The strategy applies to the Android app under `app-android/`. It is not a product marketing document and must not be used to introduce account registration, public discovery, server history, or production security claims.

## Licensing and Reuse Policy

Reference apps may be studied for interaction patterns, density, navigation, accessibility, and information hierarchy only.

Do not copy:

- source code;
- vector paths, bitmap assets, icons, logos, or mascots;
- app names, package names, or branded strings;
- layout files or screen structure verbatim;
- proprietary animations, illustrations, or distinctive visual identity.

Third-party UI dependencies are allowed only when they are minimal, justified, and permissively licensed, such as Apache-2.0, MIT, or BSD. The dependency and license must be documented in the same change that adds it.

## Reference Apps and What to Study

Telegram Android:

- modern bottom navigation and chat-list density;
- compact channel presentation;
- action sheets and bottom sheets;
- clear message list rhythm;
- recent layered, glass-like visual direction.

Signal Android:

- privacy-first onboarding;
- clean conversation screen hierarchy;
- identity and safety-number clarity;
- minimal settings organization;
- readable delivery and read status presentation.

SimpleX Chat:

- no-phone and no-email onboarding language;
- invite/contact-first mental model;
- connection-oriented explanations;
- clear separation between pending and active connections.

Material 3 and Now in Android:

- Compose screen architecture;
- dark theme implementation;
- reusable component structure;
- navigation consistency and accessibility.

## What Must Not Be Copied

Kraken must not copy any Telegram, Signal, SimpleX, or other messenger code or assets. This includes icons, logo shapes, screenshots, exact strings, distinctive chat bubble styling, package names, or complete layouts.

Kraken should use references only to answer practical UX questions:

- which information belongs on the first screen;
- how dense a contact or chat list should be;
- where destructive actions should live;
- how pending, blocked, and active states should read;
- how much explanation is appropriate before it becomes documentation instead of UI.

## Kraken Product Constraints

The UI must preserve these constraints:

- entry is invite-only;
- there is no public discovery;
- there is no phone, email, username, password, social login, or account server registration;
- identity is local, and a new identity key means a new user;
- display name is only a label;
- QR starts a handshake and does not grant membership directly;
- unlink is bilateral for honest clients;
- direct messages are primary;
- channels are preferred over large groups;
- large group chats are not MVP;
- root governance cannot decrypt messages;
- tombstone deletion is best-effort;
- the research panel is diagnostic-only and is not production encryption.

## Target Navigation Model

Daily navigation uses a bottom bar:

- Home
- Contacts
- Realms
- Settings

Secondary screens remain reachable from Home action cards:

- Create Identity
- My QR
- Import Invite
- Pending Approvals
- Chat
- Channels
- Mesh Status
- Research

The top app bar should stay compact with the title `Kraken` and a short subtitle such as `Invite-only prototype`. It must not become a large marketing hero.

## Screen-by-Screen UX Targets

Welcome:

- onboarding-like, not a policy document;
- primary action to create a local identity;
- secondary action to open the dashboard;
- concise invite-only and no-account positioning.

Home:

- messenger dashboard rather than button dump;
- identity summary at the top;
- primary actions for My QR, Import Invite, Contacts, and Realms;
- compact counts for pending invites, relationships, realms, and complaints;
- no long policy walls.

Create Identity:

- simple local onboarding form;
- display name field only;
- clear note that new key means new user;
- show existing identity if already created.

My QR:

- identity summary;
- centered real QR;
- regenerate/revoke actions;
- details disclosure for technical invite metadata;
- warning that QR starts handshake and does not grant membership.

Import Invite:

- scan QR primary action;
- import result card;
- pending handshake result;
- no ACTIVE relationship or membership after import.

Contacts:

- chat/contact-list density;
- state badges;
- peer label and fingerprint;
- clear actions for handshake state transitions;
- empty state with Import Invite action.

Chat:

- messenger-like placeholder;
- relationship card at top;
- message area placeholder;
- composer enabled only for ACTIVE relationships;
- compact status legend;
- unlink actions in a danger section.

Realms:

- local realm cards;
- state and capacity badges;
- compact policy summary;
- create demo realm action;
- no search, public discovery, or nearby discovery UI.

Pending Approvals:

- clean queue;
- request cards with realm, inviter, invitee, and approval status;
- empty state when there are no local requests.

Settings:

- grouped identity, relay, privacy, and prototype sections;
- disabled controls are acceptable when the underlying feature is not implemented;
- no account, recovery, or cloud sync settings.

Research:

- diagnostic module layout;
- explicit warning that it is not production encryption;
- future actions for curve diagnostics, benchmark sample, and export report.

## Component Inventory

The app should keep reusable components under `com.disser.kraken.ui.components`:

- screen container and app scaffold;
- bottom navigation;
- hero card;
- info card;
- warning card;
- action card;
- compact action card;
- state badge;
- empty state;
- section header;
- labeled value;
- form fields and compact buttons.

The app should keep dark theme definitions under `com.disser.kraken.ui.theme` and screen implementations under `com.disser.kraken.ui.screens`. Navigation belongs under `com.disser.kraken.navigation`.

## Visual Style Baseline

Kraken defaults to a dark theme. The visual baseline should be quiet, readable, and messenger-like:

- near-black or dark teal background;
- dark desaturated teal or gray surfaces;
- bright teal primary actions;
- muted gold secondary accents;
- warm red for warnings and destructive actions;
- rounded cards and panels;
- strong text contrast;
- compact typography;
- thumb-friendly actions.

Glass-like direction can be approximated with dark elevated surfaces, subtle alpha panels, outlines, and restrained gradients. Do not introduce expensive visual effects if they weaken build reliability or readability.

## Empty States and Error States

Empty states should tell the user what is missing and what the next local action is. They should not repeat the full protocol policy.

Examples:

- no identity: create local identity;
- no contacts: import invite;
- no realms: create demo realm;
- no pending approvals: wait for invite-based requests;
- no active relationship: complete handshake before chat.

Error states should be specific and recoverable. For example, invalid QR should say whether the issue is unsupported type, unsupported version, missing key, self-invite, duplicate invite, wrong recipient, or already known key.

## Copywriting Rules

Use short UI copy. Put long explanations in docs or expandable cards.

Required wording principles:

- say `invite-only`, not discoverable;
- say `local identity`, not account;
- say `new key means new user`;
- say `QR starts a handshake`;
- say `membership requires certificate`;
- say `research/diagnostic only`, not production encryption.

Avoid:

- production-secure claims;
- account or registration language;
- public discovery language;
- guaranteed deletion language;
- transparent key rotation language;
- server history or cloud sync language.

## Future Codex UI Task Template

Use this template for UI-only changes:

```text
Task:
Improve <screen/component> UI only.

Constraints:
- preserve invite-only model;
- do not add account registration;
- do not add public discovery;
- do not add networking or production crypto;
- do not change protocol state transitions.

Reference direction:
- use Telegram/Signal/SimpleX/Material 3 as UX references only;
- do not copy code, assets, icons, strings, or package names.

Expected changes:
- list screens/components;
- list reusable components touched;
- run ./gradlew test and ./gradlew assembleDebug.
```

## Review Checklist

Before accepting an Android UI change, check:

- bottom navigation still uses Home, Contacts, Realms, Settings;
- secondary screens remain reachable from Home;
- dark theme is still default;
- there are no phone, email, login, password, account, recovery, or cloud sync fields;
- there is no public realm, nearby network, global search, channel search, or user directory UI;
- QR import still creates pending state only;
- message sending remains gated by ACTIVE relationship;
- destructive unlink actions are clearly separated;
- research screen does not claim production encryption;
- no copied third-party icons, strings, assets, layouts, or package names were added;
- `./gradlew test` and `./gradlew assembleDebug` pass, or any environment limitation is documented.
