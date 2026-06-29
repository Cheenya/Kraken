# UI/UX Messenger Baseline Plan

Branch: `codex/ui-ux-messenger-baseline`
Worktree: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`
Status: revised after implementation block 40; local Android UI baseline is implemented and evidenced. Automatic nearby completion after one scan now has Android mesh-packet implementation, unit evidence, and Samsung -> Xiaomi physical phone evidence for QR scan -> pending -> active without manual completion QR.

## 0. Current Revision

This plan is now a living implementation plan, not the original approval draft.

Resolved decisions:

- Bottom nav shape is `Чаты / Контакты / Реалмы / Настройки`.
- Existing user starts through the branded welcome/start flow; the splash is readiness-gated instead of timeout-gated.
- QR actions are not duplicated in `Чаты`; primary QR entry points live in `Контакты` and QR-specific flows.
- Chat list rows should look like normal messenger rows, not framed cards.
- Chat rows show a compact route/nearby status dot on the avatar.
- Message action menu is anchored near the message and should prefer the left side of outgoing bubbles.
- Message selection uses contextual selection mode with visible check state and group actions.
- Settings route mode uses an explicit segmented control, not a passive `Авто` trailing label.
- Bottom nav selected state uses a compact icon pill, not a large rectangular item background.

Completed locally with evidence:

- `Мой QR` is larger on Samsung and uses reduced QR generator margin.
- Normal UI has mesh start/restart controls outside route diagnostics.
- `Исследовательский режим` is reachable as its own research/settings entry, not as a network diagnostics sub-mode.
- Visible diagnostics/research/report labels were localized where they are not raw identifiers.
- The term `алгоритм Адамовой` was removed from visible UI/report copy.
- Long press selects messages; short tap opens the single-message action menu.
- Message action menu dismiss no longer uses the old central dialog flash.
- Reply swipe direction is ownership-aware: incoming -> right, outgoing -> left.
- QR post-decode happy path now moves forward to Contacts/pending confirmation and gates manual QR fallback.
- Chat search was implemented.
- Failed messages have inline retry and debug payloads are summarized away from normal message text.
- `Реалмы` stays in bottom nav for this baseline.
- Full Samsung screenshot audit covers root tabs, QR flow, contact actions, settings diagnostics, chat thread, message menu and selection.
- Xiaomi stock camera evidence showed old custom QR content as plain text. Block 37 supersedes the older `intent://` attempt: generated QR now uses compact `https://kraken.local/qr?...`, while legacy `intent://`, `kraken://`, and raw JSON remain decodable. Fully guaranteed external-camera app handoff still requires verified HTTPS Android App Links with a real domain.
- `showHome` was removed from `ScreenContainer` in Block 30; navigation now uses explicit `showBack` and bottom navigation root jumps.
- Samsung invalid-QR error after scanning Xiaomi manual response QR was diagnosed in Block 31 as stale pending invite state, not QR decode failure. Pending same-peer records can now be replaced by a newer valid response QR.
- Physical Samsung retry after the Block 31 fix is evidenced: Samsung and Xiaomi both reached the same `ACTIVE` relationship, and Samsung shows a delivered outgoing message to Xiaomi.
- User-facing Android copy no longer exposes `ответный QR` / `финальный QR`; fallback paths use neutral `резервный QR` / `завершение сопряжения` wording.
- Phone-to-phone QR evidence harness now writes `manifest.json` from actual non-empty captured files instead of expected filenames.
- Direct-contact nearby completion now has both service packet halves: after scanning an invite, the responder queues `HANDSHAKE_RESPONSE`; after accepting it, the inviter queues `HANDSHAKE_CONFIRMATION`; the responder can become `ACTIVE` without scanning a manual completion QR.
- Physical Samsung -> Xiaomi evidence in Block 40 shows one QR scan advancing both devices to `АКТИВЕН`.
- Block 40 fixed a live bottom-menu regression by opening `Чаты` from the welcome screen and showing the messenger bottom menu on the legacy `Home` root surface.
- Block 40 added visible pending nearby-confirmation status and a progress bar so the handshake wait no longer looks like a frozen UI.

Current cross-platform correction added on 2026-06-29:

- Android information architecture remains the source of truth: `Чаты / Контакты / Реалмы / Настройки`.
- Do not restore `Главная` as a bottom-nav tab. If a dashboard/welcome surface exists, it is reached from launch/start context, not from the main messenger tabs.
- Android bottom nav should visually move toward the liked iOS floating capsule style while keeping the Android tab order and existing destinations.
- Android chat composer should replace the current inline emoji strip with a normal messenger emoji panel opened from a smile icon on the left side of the input.
- iOS `Чаты` must become a real chat list, not a dashboard/home screen with the `Чаты` label.
- iOS and macOS navigation should align to the Android messenger model: `Чаты`, `Контакты`, `Реалмы`; iOS also keeps `Настройки` in the tab order, while macOS may keep Settings in its desktop-style location.

Remaining external evidence gaps:

- Physical camera-to-phone QR scan is evidenced in Block 27, retry to `ACTIVE` is evidenced in Block 32, packet-level nearby one-scan response/confirmation is implemented in Blocks 35 and 38, and Samsung -> Xiaomi one-scan physical activation is evidenced in Block 40.
- Reverse Xiaomi -> Samsung fresh-pairing evidence remains optional unless symmetric physical evidence is required.
- The user-reported macOS scanner issue is outside this isolated Android UI/UX worktree and should be handled in the main/macOS tree only with explicit approval.
- Incoming-message right-swipe is now evidenced on Samsung in Block 23; outgoing left-swipe was already evidenced earlier.

## 1. Цель

Сделать Kraken похожим на нормальный мессенджер по базовым interaction standards, не убивая специфику dissertation/debug/research приложения.

Не цель:

- не копировать Telegram визуально;
- не прятать research/evidence полностью;
- не ломать текущую transport/debug работу в основной ветке;
- не менять domain-модели глубже, чем нужно для UI.

Цель:

- основной пользовательский путь читается как messenger flow;
- QR-сопряжение понятно и быстрое;
- Bluetooth/nearby подтверждение становится основным post-scan flow;
- ручные `ответный QR` / `финальный QR` уходят в fallback/debug;
- debug/evidence остаются доступны, но не засоряют обычный messenger UX;
- навигация `Back / Close / Home` ведёт туда, куда пользователь ожидает.

## 2. Рабочая изоляция

Все UI/UX изменения делать только в отдельной ветке:

- branch: `codex/ui-ux-messenger-baseline`
- worktree: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux`

Основной worktree `/Users/cheenya/Projects/kraken-android-research-panel` не трогать для UI-экспериментов, потому что там есть Wi-Fi Direct/debug-hint WIP.

## 3. Evidence / Reference

Kraken UI audit:

- `/Users/cheenya/Projects/kraken-android-research-panel/artifacts/ui-ux-audit-samsung/20260614-030318/ui_ux_audit_report.md`

Telegram reference audit:

- `/Users/cheenya/Projects/kraken-android-research-panel/artifacts/ui-ux-telegram-reference-samsung/20260614-034205/telegram_reference_ui_ux_notes.md`

Fresh Samsung Telegram reference:

- `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-detail-pass-samsung/20260614-042041/00-current-samsung.png`

Implementation screenshot evidence:

- Block 1 baseline navigation/copy/QR/settings/actions: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-1-samsung/20260614-045239/`
- Block 2 QR size, chat header, selection/favorites: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-2-samsung/20260614-051743/`
- Block 3 compact message menu: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-3-samsung/20260614-052410/`
- Block 4 anchored message menu: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-4-samsung/20260614-052955/`
- Block 5 denser message menu positioning: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-5-samsung/20260614-053445/`
- Block 6 reaction strip and menu icons: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-6-samsung/20260614-053848/`
- Block 7 settings route mode control: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-7-samsung/20260614-054341/`
- Block 8 chat row/status/menu-left/selection fixes: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-8-samsung/20260614-054749/`
- Block 9 bottom nav selected state: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-9-samsung/20260614-055032/`
- Block 10 larger QR rendering: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-10-samsung/20260614-160549/`
- Block 11 normal mesh control and research entry placement: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-11-samsung/20260614-161146/`
- Block 12 message long-press selection, no-ripple menu dismiss, directed reply swipe: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-12-samsung/20260614-161419/`
- Block 13 research/diagnostics localization and prohibited terminology cleanup: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-13-samsung/20260614-162237/`
- Block 14 QR fallback flow terminology and response-QR blocker investigation: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-14-samsung/20260614-qr-flow-labels/`
- Block 15 chat list search: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-15-samsung/20260614-chat-search/`
- Block 16 failed-message inline retry and debug payload presentation: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-16-samsung/20260614-failed-debug-message/`
- Block 17 `Реалмы` bottom navigation decision: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-17-samsung/20260614-realms-nav-decision/`
- Block 18 QR deep-link and density pass: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-18-samsung/20260614-qr-deeplink-density/`
- Block 19 completion screenshot audit and root title consistency: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-19-samsung/20260614-completion-audit/`
- Block 20 contact actions bottom sheet and local pairing cancellation: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-20-samsung/20260614-contact-actions-cancel-pairing/`
- Block 21 QR happy-path fallback gating: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-21-samsung/20260614-qr-happy-path-fallback-gating/`
- Block 22 plan sync, completion audit and remaining localization cleanup: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-22-samsung/20260614-plan-sync-completion-audit/`
- Block 23 incoming-message right-swipe evidence: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-23-samsung/20260614-incoming-reply-swipe-evidence/`
- Block 24 active/stale relationship local forget path: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-24-samsung/20260614-forget-relationship/`
- Block 25 local Android final audit: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-final-audit/20260614-local-android-baseline/`
- Block 26 phone-to-phone QR manual evidence harness: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/scripts/capture_phone_to_phone_qr_evidence.sh`
- Block 27 physical phone-to-phone QR scan evidence: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-phone-to-phone-qr/20260614-193923-samsung-to-xiaomi-physical-qr/`
- Block 28 remaining gap handoff: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-final-audit/20260614-local-android-baseline/remaining_gap_handoff.md`
- Block 29 Xiaomi external camera QR handling: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-phone-to-phone-qr/20260614-xiaomi-camera-qr-issue/`
- Block 30 navigation API cleanup: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-30-navigation-api-cleanup/navigation_api_cleanup.md`
- Block 31 stale pending invite response fix: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-phone-to-phone-qr/20260614-samsung-invalid-xiaomi-qr/stale_pending_invite_response_fix.md`
- Block 32 physical retry success after stale pending fix: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-phone-to-phone-qr/20260614-samsung-current-after-xiaomi-scan/samsung_scan_success_after_stale_pending_fix.md`
- Block 33 fallback QR copy cleanup: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-33-fallback-qr-copy-cleanup/fallback_qr_copy_cleanup.md`
- Block 34 evidence harness manifest integrity: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-34-evidence-harness-manifest-integrity/evidence_harness_manifest_integrity.md`
- Block 35 nearby handshake confirmation packet: `/Users/cheenya/Projects/kraken-android-research-panel-ui-ux/artifacts/ui-ux-block-35-nearby-handshake-confirmation/nearby_handshake_confirmation.md`

Privacy note: Telegram screenshots contain private data. Use them only as visual references for layout and interaction patterns.

Useful Telegram reference patterns:

- compact QR icon in top-left profile context;
- chat list is the primary messenger surface;
- message selection uses contextual action mode, not a central explanatory modal;
- top/bottom navigation has clear selected state;
- settings use controls that look like controls.

## 4. Product Model

### 4.1 Normal user model

User thinks in terms of:

- chats;
- contacts;
- QR pairing;
- local delivery status;
- realms/groups if needed.

User should not need to think in terms of:

- `EXPIRED`;
- `финальный QR`;
- `ответный QR`;
- `foreground service`;
- `LAN/BLE route internals`;
- `evidence` unless they enter diagnostics.

### 4.2 Debug/research model

Debug/research/evidence are allowed because the app is for dissertation work. But they need their own surface:

- `Диагностика`
- `Исследование`
- `Диссертация`
- `Evidence`

They should not be primary cards on the default messenger path.

## 5. Naming Decisions

### 5.1 Replace `Связь рядом`

`Связь рядом` is awkward Russian product copy.

Approved proposal unless changed:

- normal UI: `Локальная связь`
- device discovery lists: `Устройства рядом`
- debug screen: `Диагностика связи`
- avoid `Связь рядом`

Examples:

- Home section: `Локальная связь`
- Mesh/debug screen title: `Диагностика связи`
- Status: `Ищем устройства рядом`

### 5.2 Rename `Рилмы`

Use `Реалмы` in user-facing copy.

Mapping:

- `Рилмы` -> `Реалмы`
- `Рилм` -> `Реалм`
- `рилм` -> `реалм`

Technical identifiers/classes can stay unchanged in this slice.

### 5.3 Replace `локальная личность`

Current copy duplicates itself: `локальная личность` plus `ЛОКАЛЬНО`.

Use:

- `Профиль на этом устройстве`
- optional badge: `На устройстве`
- fingerprint only in `Технические детали`

Avoid:

- `локальная личность`
- duplicate `локально` labels in the same card.

## 6. Navigation Model

### 6.1 Important correction

`showHome=false` is not inherently wrong. In deep flows it may be exactly right: user should go one step back, not jump to Home.

The actual problem is unclear navigation semantics.

### 6.2 Separate intents

Use explicit concepts:

- `Back`: previous screen in stack.
- `Close`: exit current flow to the parent/root section.
- `Home`: explicit jump to main/root screen.

Do not overload one flag like `showHome` to mean all three.

### 6.3 Top app bar rules

Root screens:

- no back button;
- clear title;
- optional actions on the right.

Deep screens:

- top-left `Back`;
- if space allows, icon + text `Назад`;
- specific title;
- actions on the right.

Flow screens:

- top-left `Назад` if step-by-step;
- `Отмена` or close icon if abandoning the flow;
- no accidental jump to Home.

Examples:

- `Мой QR`: back to previous screen, QR action/details on right.
- `Сканировать QR`: back/cancel to previous flow.
- `Подтверждение рядом`: back or cancel, not Home.
- `Профиль контакта`: back to chat/list.
- `Диагностика связи`: back to settings/diagnostics.

### 6.4 API cleanup

Implemented in Block 30:

- the stale `showHome` parameter was removed from `ScreenContainer`;
- root screens explicitly pass `showBack = false`;
- deep screens keep default stack back behavior through `showBack = true`;
- no component flag now pretends that Back, Close and Home are the same action.

## 7. Main App Structure

### 7.1 Bottom nav candidate

Chosen messenger-oriented bottom nav:

- `Чаты`
- `Контакты`
- `Реалмы`
- `Настройки`

Visual rule:

- selected tab uses a compact pill behind the icon;
- no large rectangular selected background;
- tap feedback must not leave a full-width item rectangle in screenshots.

Current Android visual target:

- use a floating dark capsule close to the liked iOS reference;
- keep a subtle border, elevation/shadow and teal accent;
- active tab uses a compact rounded pill inside the capsule;
- icons are large enough to read at phone distance;
- labels stay short and stable;
- the component must not reserve too much vertical height or cover the last chat row/message composer;
- this is visual polish only, not a navigation-model change.

### 7.2 First screen after launch

For existing user, do not repeatedly show the branded welcome.

Preferred:

- start at `Чаты`;
- if no active chats, show empty state with action to `Контакты`.

Acceptable fallback:

- compact Home/dashboard, but it must behave like a messenger hub, not a landing/research page.

### 7.3 Branded context

Chat list should show app context:

- title: `Kraken` or `Kraken Messenger`
- transport status pills on the right, if they are compact and truthful
- chat rows
- empty state can route to `Контакты`, not duplicate QR controls

Deferred:

- search field under title, unless it can be added without crowding the root screen.

Inside a chat:

- contact name is primary;
- subtitle is concise delivery/relationship status;
- app brand does not need to dominate the thread header.

## 8. QR Pairing Flow

### 8.1 Current problem

The former scanner/contact flow exposed manual fallback handshake terms as primary:

- manual response QR
- manual completion QR
- `К сопряжению`

This conflicts with the intended model: one QR scan confirms local presence, then Bluetooth/nearby completes the exchange.

### 8.2 Primary flow

1. User A opens `Мой QR`.
2. Fresh QR is generated automatically.
3. QR is visible immediately.
4. User B scans QR.
5. Scanner automatically navigates to `Подтверждение рядом`.
6. App starts Bluetooth/nearby confirmation automatically.
7. If confirmation succeeds, relationship becomes active on both sides.
8. User lands on contact/chat, not on a static scanner result.

### 8.3 Fallback flow

Manual QR response/final QR stays available only if Bluetooth/nearby fails.

Copy:

- `Не получилось через Bluetooth?`
- `Завершить вручную через QR`

Avoid showing response/final QR in the happy path.

### 8.4 `Мой QR`

Must always satisfy its promise:

- if no QR: create one;
- if QR expired/revoked/consumed: create a fresh one before rendering the main state;
- main state shows QR;
- QR should be large enough for phone-to-phone scanning:
  - minimize unused white field around the code;
  - use available card width/height instead of leaving a large blank area;
  - preserve the required quiet zone, but do not let the quiet zone dominate the card;
- secondary actions: refresh, revoke, details.

### 8.5 QR placement

Updated placement:

- `Контакты` is the normal place for `Мой QR` and `Сканировать QR`;
- QR-specific screens keep clear actions: `Показать мой QR`, `Сканировать QR`;
- `Чаты` must not duplicate `Мой QR` / `Сканировать QR` in the header;
- compact QR icon is acceptable in profile/contact/QR context, similar to Telegram profile reference.

## 9. Chat UX

### 9.1 Chat list

Required:

- title `Kraken` / `Kraken Messenger`;
- chat rows with avatar, name, last message, timestamp/status;
- no framed/card row around every chat;
- route/nearby status dot on the avatar:
  - fresh direct/mesh route: green;
  - no fresh route: muted gray / gray-green;
- empty state with:
  - `Пока нет активных чатов`
  - action to `Контакты`, where QR actions live

Deferred:

- search.

Debug/evidence entry can exist, but not as the main card.

### 9.2 Chat thread

Required:

- contact header with avatar/name;
- subtitle: concise relationship/route status;
- normal message bodies first;
- debug harness payloads should not look like normal user messages;
- failed outgoing messages show inline state.

Failed message pattern:

- `Не отправлено · Повторить`
- optional `Подробнее` for debug details

Avoid:

- long `directed Wi-Fi Direct route trial...` labels as ordinary chat content;
- repeating `ошибка` without action.

### 9.3 Message composer emoji panel

Current problem:

- emoji are shown as a simple horizontal strip above the input;
- this looks like a debug shortcut, not a messenger composer;
- the strip competes with message content and does not scale to a real emoji set.

Required behavior:

- the composer has a smile icon on the left side of the input;
- tapping the icon opens a bottom emoji panel above the composer, visually similar to common messenger emoji pickers;
- tapping the icon again closes the panel;
- focusing the text field closes the emoji panel and returns the keyboard;
- Android Back closes the emoji panel before leaving the chat;
- selected emoji is inserted into the current cursor position;
- send action remains on the right side of the composer;
- opening/closing the panel must not jump the message list unpredictably or hide the current draft.

Panel shape:

- dark rounded panel aligned to the bottom of the chat;
- first implementation uses a fixed emoji category/recent row at the top, even if only the recent/common category is populated;
- search row is optional for the first implementation;
- bottom tabs may show `Эмодзи / Стикеры / GIF`, but only `Эмодзи` has to be active in this slice;
- stickers and GIF must not be fake-enabled if the app cannot send them yet.

Implementation boundary:

- this slice changes composer UI and local draft insertion only;
- it does not add sticker transport, GIF transport or new message payload types.

### 9.4 Message actions

Replace central `AlertDialog`.

Use:

- anchored contextual menu for single-message actions;
- contextual selection mode for multi-message actions.

Gesture rules:

- short tap opens the single-message action menu;
- long press enters selection mode and selects the pressed message;
- long press must not behave the same as short tap;
- dismissing the action menu should not produce a full-screen flash or abrupt overlay flicker.

Positioning:

- for outgoing bubbles, prefer placing the action menu to the left of the message;
- avoid sticking the menu to the right screen edge;
- keep the composer visible.

Actions:

- `Ответить`
- `Повторить` if failed
- `Копировать`
- `В избранное`
- `Выбрать`
- `Удалить`
- optional later: `Подробнее` for diagnostics

No explanatory paragraph in the primary message menu.

Selection mode:

- selected messages have a clear row highlight;
- selected and unselected states show circular indicators;
- bottom selection actions include `В избранное`, group delete, cancel.

Reply / quote gesture:

- incoming message: swipe right to reply;
- outgoing own message: swipe left to reply;
- opposite-direction swipe should not trigger reply;
- animation should ease back smoothly and avoid the current jerky snap.

## 10. Contacts UX

### 10.1 Contact profile

Primary:

- avatar;
- name;
- status;
- `Открыть чат`;
- compact technical trust state.

Secondary:

- QR details;
- technical details;
- debug diagnostics.

### 10.2 Contact actions

Do not use a large accordion as the primary action surface.

Use bottom sheet:

- `Открыть чат`
- `Отключить уведомления`
- `Очистить переписку`
- separator
- `Удалить контакт`
- optional `Пожаловаться / заблокировать`

Reasons for destructive action appear after selecting the destructive action.

## 11. Transport / Diagnostics UX

### 11.1 Normal UI copy

Normal user sees:

- `Локальная связь выключена`
- `Ищем устройства рядом`
- `Xiaomi рядом не найден`
- `Не отправлено`
- `Повторить`
- an obvious control to start/restart local mesh/network when it is stopped

Normal user should not see:

- raw epoch;
- `EXPIRED`;
- `foreground service`;
- `LAN: не активен` unless inside diagnostics.

Mesh control rule:

- stopping mesh from an active notification/push must not strand the user;
- normal UI needs an intuitive `Включить сеть` / `Перезапустить связь` action outside diagnostics;
- diagnostics can still show detailed transport controls, but basic recovery belongs on the normal path.

### 11.2 Diagnostics

Diagnostics screen can show:

- LAN/BLE/Wi-Fi Direct state;
- route attempts;
- evidence;
- raw errors;
- timestamps;
- transport internals.

But title should be `Диагностика связи`, not `Связь рядом`.

Localization rule:

- diagnostics may be technical, but visible labels and reports should be Russian by default;
- English transport IDs may remain in technical details only when they are identifiers;
- full English sentences in UI, diagnostics, and generated reports must be translated or isolated as raw technical payloads.

### 11.3 Route mode setting

Resolved: passive `Авто` trailing text is not acceptable.

Use a real control:

- segmented `Обычно | Диагностика`.

Current implementation treats it as diagnostics visibility, not a transport-routing behavior switch.

## 12. Realms UX

### 12.1 Naming

Rename visible copy to `Реалмы`.

### 12.2 Visibility

Open decision:

- keep `Реалмы` in bottom nav; or
- move it below contacts/settings until realm flow is mature.

If kept in bottom nav:

- empty state must explain why user needs realms;
- creation action should be obvious and fully clickable;
- no hidden click-target mismatch.

## 13. Click Targets

Known issue: Home overview rows visually look clickable, but only the right side was clickable.

Rule:

- every list row/card that looks actionable must be fully clickable;
- ripple/pressed state should cover the full visual row;
- chevron is indicator, not the only touch target.

Apply to:

- Home overview rows;
- Realms empty/action rows;
- settings rows;
- contact rows;
- QR/action rows.

## 14. Debug / Research / Evidence IA

Allowed, but separated.

Proposed:

- Settings has a section `Диагностика`;
- debug build can show `Диссертация` / `Исследование`;
- Home/Chats do not lead with research cards;
- evidence export remains accessible from diagnostics/checklists.

Research mode is not removed; it is moved away from the normal messenger path.

Important correction:

- `Исследовательский режим` must not be hidden inside network diagnostics or route checking;
- research/dissertation tools should be a separate Settings entry or a dedicated research surface;
- network diagnostics remains for mesh/transport state, not for all research functions.

Terminology rule:

- do not use `алгоритм Адамовой` in user-facing UI, diagnostics, reports, or dissertation-facing app copy;
- if the underlying concept is needed, use neutral project terminology approved for the dissertation context.

## 15. Implementation Slices

### Slice 1 - Copy, naming, navigation baseline

Status: implemented and audited in Blocks 1, 13 and 19.

Scope:

- replace user-facing `Связь рядом`;
- replace user-facing `Рилмы`;
- replace `локальная личность`;
- normalize top app bar/back wording where low risk;
- add chat-list app title if missing.

Checks:

- `./gradlew test`
- screenshot sanity pass on Samsung

### Slice 2 - QR pairing baseline

Status: implemented and evidenced with one external-camera caveat. QR size, scanner copy, app deep-link routing, post-decode fallback gating and physical phone-to-phone camera scan have Samsung evidence. Block 37 now emits compact `https://kraken.local/qr?...` QR content and keeps legacy decode support. Block 38 proves local service-level one-scan response/confirmation, but physical BLE/LAN/Wi-Fi Direct evidence is still paused until phones are used again.

Scope:

- auto-generate/refresh `Мой QR`;
- enlarge QR on real phone and reduce unused white field while preserving scan-safe quiet zone;
- scanner auto-navigates after successful QR scan;
- create `Подтверждение рядом` progress/result surface;
- hide response/final QR under manual fallback;
- add compact QR icon action in profile/top contexts where appropriate.

Checks:

- unit tests around QR lifecycle if present/feasible;
- Samsung screenshots:
  - `Мой QR` fresh visible QR;
  - scanner result navigates forward;
  - fallback is not in happy path.
- physical QR evidence command when phones can be positioned:
  - `scripts/capture_phone_to_phone_qr_evidence.sh --source-device R5CY22X6MSB --target-device d948ffd0 --label samsung-to-xiaomi-physical-qr`

### Slice 3 - Messenger interactions

Status: implemented and audited in Blocks 3-6, 8, 12, 15, 16 and 23.

Scope:

- replace message action central dialog;
- make long press enter message selection;
- keep short tap for single-message action menu;
- remove action-menu dismiss flash;
- constrain reply swipe direction by message ownership and smooth the animation;
- add failed-message inline action;
- keep transport error in details/debug;
- improve chat empty state and search if low risk.

Checks:

- screenshot long-press menu;
- screenshot failed message;
- `./gradlew test`

### Slice 4 - Contacts/settings cleanup

Status: implemented and audited in Blocks 7, 11, 19, 20 and 21.

Scope:

- contact actions bottom sheet;
- explicit `Забыть устройство` action for stale active pairing / failed Mac or desktop pairing reset;
- route mode segmented control / debug visibility switch;
- add normal-UI mesh start/restart control outside diagnostics;
- settings text cleanup;
- ensure all actionable rows are fully clickable.

Checks:

- screenshot contact actions;
- screenshot settings route control;
- click-target smoke on Samsung.

### Slice 5 - Debug/research IA

Status: implemented and audited in Blocks 11, 13, 16 and 19.

Scope:

- move research/debug entries away from primary Home if approved;
- do not place `Исследовательский режим` inside network diagnostics;
- add explicit diagnostics entry in Settings;
- localize diagnostics/report UI to Russian by default;
- remove `алгоритм Адамовой` terminology from visible copy/reports;
- keep dissertation/evidence accessible.

Checks:

- screenshot normal home/chat path;
- screenshot diagnostics path;
- no loss of debug/evidence entry points.

### Slice 6 - Cross-platform messenger parity and Android composer polish

Status: planned after iOS/Android screenshot comparison on 2026-06-29.

Scope:

- keep Android tab order as `Чаты / Контакты / Реалмы / Настройки`;
- keep `Главная` out of the Android bottom navigation;
- restyle Android bottom navigation as a floating dark capsule with a compact active pill, without changing destinations;
- replace Android inline emoji strip with the messenger-style emoji panel described in section 9.3;
- make iOS `Чаты` a real chat-list screen instead of a dashboard/home screen;
- align iOS `Контакты` with Android: QR actions plus contact list are primary, manual/debug import details are secondary;
- align iOS `Реалмы` with Android: list first, selected realm details/actions deeper in the flow;
- align iOS `Настройки` with Android: profile/app settings first, diagnostics/evidence lower or behind a research/diagnostics entry;
- align macOS main section order to `Чаты / Контакты / Реалмы` while preserving a desktop-appropriate Settings placement.

Files to inspect before implementation:

- Android chat/root navigation: `app-android/app/src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt`
- Android bottom navigation/shared shell: search for `BottomNavigation`, `NavigationBar`, `ScreenContainer`, and `KrakenBottom`
- Android composer: search for message input/composer state in `app-android/app/src/main/java/com/disser/kraken/ui/screens/ChatScreen.kt`
- iOS root and tab content: `app-ios/KrakenIOS/Views/KrakenIOSRootView.swift`
- macOS sidebar/sections: `app-macos/Sources/KrakenDesktop/Support/DesktopSection.swift`, `app-macos/Sources/KrakenDesktop/Views/SidebarView.swift`

Checks:

- Android unit/compile check: `cd app-android && ./gradlew test assembleDebug`
- iOS portable core check: `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test --package-path app-ios`
- iOS Simulator build check:
  `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild build -project app-ios/KrakenIOS.xcodeproj -scheme KrakenIOS -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5' -derivedDataPath app-ios/DerivedData`
- macOS verification: `cd app-macos && ./script/build_and_run.sh --verify`
- screenshot comparison:
  - Android `Чаты` and iOS `Чаты` both show chat-list semantics;
  - Android `Контакты` and iOS `Контакты` both expose QR/contact semantics;
  - Android `Реалмы` and iOS `Реалмы` both start with realms list semantics;
  - Android composer shows a left smile button and a bottom emoji panel, not a horizontal strip.

## 16. Acceptance Criteria

UI/UX branch is ready when:

- existing user can open app and understand where chats are;
- `Мой QR` immediately shows a QR;
- after scanning QR, user is moved to the next pairing step automatically;
- normal path does not mention `ответный QR` / `финальный QR`;
- fallback still exists for no-Bluetooth/no-nearby case;
- message long-press no longer opens a large central explanatory modal;
- failed messages have inline retry;
- visible `Рилмы` are now `Реалмы`;
- `Связь рядом` no longer appears in normal UI;
- `Back`, `Close`, and `Home` behavior are not conflated;
- debug/research/evidence are still reachable;
- chat root does not duplicate `Мой QR` / `Сканировать QR`;
- chat list rows are unframed messenger rows;
- chat list shows a compact route/nearby dot;
- message action menu prefers the left side of outgoing messages;
- bottom nav selected state is not a large rectangle;
- active or stale paired contacts can be forgotten locally without clearing the whole app;
- QR is large enough on the phone and does not waste most of the card as blank white field;
- stopping mesh from the active notification/push leaves an obvious normal-UI way to start/restart the network;
- `Исследовательский режим` is not hidden inside network diagnostics;
- visible diagnostics and reports are Russian by default, except raw identifiers where unavoidable;
- `алгоритм Адамовой` is absent from visible UI/report copy;
- long press selects messages;
- action menu dismissal does not flash the whole screen;
- reply swipe direction depends on message ownership and animates smoothly.
- Android bottom navigation keeps `Чаты / Контакты / Реалмы / Настройки`, has no `Главная` tab, and visually reads as a floating capsule rather than a blocky row.
- Android emoji picker opens from a smile icon on the left side of the composer as a bottom panel; the old inline emoji strip is gone.
- iOS `Чаты` is a real chat-list screen, not a dashboard renamed to chats.
- iOS/macOS section order and screen responsibilities match the Android messenger model unless a platform-specific desktop/mobile convention is explicitly documented.

## 17. Open Decisions

Resolved:

1. `Связь рядом` -> `Локальная связь` / `Диагностика связи` depending on context.
2. Visible `Рилмы` -> `Реалмы`.
3. Bottom nav final shape for this slice: `Чаты / Контакты / Реалмы / Настройки`.
4. Existing user start destination: `Чаты`.
5. Message actions: anchored contextual menu plus contextual selection mode.
6. `Реалмы` stays in bottom nav for this baseline.
7. Chat search is implemented in this branch.
8. Debug payloads are summarized in normal chat surfaces, with details kept in diagnostics/details.
9. Settings route mode remains local UI state for this slice.
10. Normal mesh recovery lives in normal Settings/root UI, with detailed controls still in diagnostics.
11. Research/dissertation tools are a separate Settings/research surface, not a network diagnostics sub-mode.
12. Android bottom navigation target order remains `Чаты / Контакты / Реалмы / Настройки`; the new iOS-like capsule is a visual treatment, not a semantic change.
13. Android emoji entry belongs in the composer as a left smile icon that opens a bottom panel; the inline emoji strip is not the target design.

Still open:

1. macOS scanner fix/evidence outside this isolated Android UI/UX worktree.
2. Physical two-phone evidence that direct-contact nearby confirmation advances from pending contact to active contact without manual QR fallback over the real device transports.
