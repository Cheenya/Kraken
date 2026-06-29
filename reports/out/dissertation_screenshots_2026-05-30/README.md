# Kraken Android dissertation screenshots

Date: 2026-05-30

Device:

```text
Samsung SM-S938B / R5CY22X6MSB
```

Android branch baseline before this screenshot pack:

```text
codex/android-research-panel-report-viewer
f4a03cf Merge contacts into chat navigation
```

## Screenshot Index

| File | Purpose |
| --- | --- |
| `01_start_screen_after_reinstall.png` | Branded Kraken start screen after reinstall. |
| `02_overview.png` | Messenger-first overview: local identity, state counters, QR actions. |
| `03_chats_contacts_merged.png` | Unified chats/contacts screen with pending, blocked and active relationship states. |
| `04_settings.png` | Settings screen: prototype tools, Russian default copy, prototype boundary. |
| `05_mesh_status.png` | Mesh diagnostics: local transport status, LAN QR endpoint, QR-trust caveat. |
| `06_research_panel_top.png` | Research Panel top: offline diagnostics, validated corpus, controlled research attack result. |
| `07_research_benchmark_examples.png` | Research examples: Kotlin/C++ benchmark entry and large-coefficient corpus summary. |
| `08_research_benchmark_result.png` | Fresh in-app Kotlin vs C++ benchmark result. |
| `09_my_qr.png` | One-time invite QR with explicit handshake requirement. |
| `10_realms.png` | Local realms UX: invite-only realm and hidden technical details. |

## Claim Boundary

These screenshots demonstrate the Android research prototype UI. They do not
claim production encryption, audited secure messaging or completed two-device
LAN delivery.

The `05_mesh_status.png` screenshot shows a local LAN endpoint QR. This is a
transport hint only. It does not create trust and does not replace the offline
mutual QR handshake.

## Not Included

The raw local QA folder `reports/out/android_live_audit/` is intentionally not
part of this curated screenshot pack because it may contain accidental or
private captures made during live debugging.
