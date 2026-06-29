# Android Visual QA Report

Date: 2026-05-23

## Scope

Reviewed the current installed Kraken Android build on a physical phone after the branded launch, QR-only UX and Russian copy cleanup.

This report is about product UI readiness only. It does not claim protocol, P2P or production crypto readiness.

## Device

```text
R5CY22X6MSB / SM-S938B
```

## Latest Captures

```text
artifacts/phone-screens/kraken_after_install_20260523_175722.png
artifacts/phone-screens/kraken_after_install_settled_20260523_175818.png
artifacts/phone-screens/kraken_post_qa_cleanup_20260523_182157.png
artifacts/phone-screens/kraken_post_qa_cleanup_after_attack_20260523_182450.png
```

The capture directory is ignored because it contains local QA churn. Force-add selected images only if they are needed as review evidence.

## Findings

### Splash

Current status:

- full-screen branded art is displayed;
- startup research attack runs locally and saves a log for Research mode;
- progress bar is visible.

Fix applied after QA:

- removed debug-looking `Без validation`, `Validation gate` and `BIGINTEGER ECDLP` splash overlay wording;
- replaced it with neutral Russian research-preparation copy;
- retained the startup run and saved log.

Remaining manual review:

- confirm the lockup size against the Kraken Core reference;
- decide whether status/navigation bars should be hidden for a true immersive launch.
- decide whether the startup research run duration is acceptable for everyday testing; it can take around a minute on device.

### Start Screen

Current status:

- full-screen branded background is used;
- Kraken Core mark is centered visually enough for the current build;
- Russian product copy is now the default;
- primary action is messenger-first.

Fix applied after QA:

- primary CTA for existing identity changed from `МОЙ QR-КОД` to `ОТКРЫТЬ ЧАТЫ`;
- QR moved to the secondary action;
- `P2P` wording removed from bottom principles;
- English tagline replaced with Russian privacy/offline/local wording.

Remaining manual review:

- compare against the reference image on device brightness;
- judge button size and vertical spacing by eye.

### Navigation

Current status:

- bottom navigation is messenger-first: Chats, Contacts, Realms, Settings;
- Research mode is under Settings;
- top-right text `Back` is not present in production `ScreenContainer`.

Remaining manual review:

- verify that app start -> Chats is the right default after identity exists;
- verify that QR is still reachable quickly enough from start/home/settings.

### Research Mode

Current status:

- Research mode remains diagnostic-only;
- native C++ benchmark card exists;
- startup research attack log is available after launch.

Remaining manual review:

- keep Research mode out of primary navigation;
- reduce text density only after messenger flows are stable.

## No-Go Claims

Do not use screenshots to imply:

- production encryption;
- production P2P;
- serverless delivery is complete;
- curve diagnostics prove message security;
- JSON exchange is normal user workflow.

## Recommended Next Visual Pass

Capture and review:

1. splash;
2. start screen;
3. chats without active contacts;
4. create identity;
5. My QR;
6. scanner;
7. contacts pending/active;
8. realm list;
9. settings;
10. Research mode.
