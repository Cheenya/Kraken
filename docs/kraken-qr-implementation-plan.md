# Kraken QR Implementation Plan

This archived implementation plan describes the QR rendering/scanning work that is now implemented for the local demo while preserving Kraken protocol rules.

## Goals

- Generate QR codes for one-time invite payloads locally.
- Scan/import QR payloads locally.
- Keep manual copy/paste fallback.
- Preserve the rule: QR starts handshake and does not grant membership directly.
- Avoid public discovery, account registration or server dependency.

## Non-Goals

- No public invite directory.
- No server-side invite exchange.
- No account recovery.
- No transparent key rotation.
- No production crypto claim before signed payload review.

## QR Generation Library Options

Preferred criteria:

- Permissive license: Apache-2.0, MIT or BSD.
- Android/Compose friendly.
- No analytics/tracking SDK.
- No network dependency.
- Small dependency footprint.

Candidates to evaluate:

| Option | Notes | Review Items |
| --- | --- | --- |
| ZXing core | Mature QR generation/scanning ecosystem. | License, dependency size, Android camera integration path. |
| AndroidX Camera + ML Kit barcode scanning | Good camera UX, but ML Kit may imply Google dependency. | Avoid if it introduces cloud/tracking assumptions; verify on-device-only mode. |
| Lightweight pure Kotlin QR generator | Good for generation only. | Maintenance, license, QR correctness tests. |
| Platform intent scanner | Minimal implementation. | UX consistency and dependency on external apps. |

Initial recommendation:

- Use a small generation-only library or ZXing core for display.
- Treat scanning as a separate review because it introduces camera permission and device testing.

## QR Scanning Options

Scanning should be implemented only after permission and privacy review.

Options:

1. CameraX local scanner screen.
2. ZXing-based local scanner.
3. External scanner intent fallback.
4. Manual paste remains required even if scanner exists.

Acceptance criteria:

- Camera permission request is scoped to QR scanning only.
- No contacts, location, phone state or nearby discovery permission is added.
- Scanning does not start background discovery.
- Invalid/duplicate/self invites are rejected.

## Payload Size Limits

Current payload is JSON and may grow when signatures/certificates are added.

Near-term limits:

- Keep one-time invite payload compact.
- Avoid embedding membership certificates in contact QR unless explicitly needed.
- Keep invite QR separate from realm membership approval result.

Future work:

- canonical JSON;
- compact field aliases only if documented in protocol spec;
- optional compression;
- binary canonical encoding only after review.

## JSON Compression And Canonicalization

Before signatures are mandatory:

- define canonical JSON ordering;
- define exact string encoding;
- define signature-covered fields;
- reject unknown critical fields.

Compression options:

- no compression for MVP if QR payload fits;
- DEFLATE or CBOR only after dependency/security review;
- always preserve copy/paste fallback.

## One-Time Invite Consumed State

Required behavior:

- generated invite has `inviteId`;
- import creates pending state only;
- successful handshake/approval marks invite consumed locally;
- reused invite ID is rejected or routed to existing pending relationship review;
- consumed state is local until network protocol exists.

Important:

- A QR screenshot leak may still allow an attempt until consumed/expired.
- QR itself does not prove concrete person/device; handshake confirmation is required.

## Duplicate Invite Handling

Reject:

- duplicate `inviteId` already imported;
- duplicate public key already known as an active/pending relationship unless routed to existing relationship;
- malformed payload;
- unsupported type/version.

Do not silently create multiple contacts for the same public key.

## Self-Invite Rejection

If `inviterPublicKeyEncoded` equals local identity public key:

- reject import;
- show clear local error;
- do not create pending import;
- do not create relationship.

## Signature Requirement

Current signature field may be null/placeholder. Future signed invites should:

- sign canonical payload fields;
- include key identifier/fingerprint;
- reject modified signed fields;
- avoid signing display name as trusted identity;
- keep display name as label only;
- define expiry and consumed semantics.

Potential checks:

- signature valid for inviter public key;
- payload fingerprint matches public key;
- `expiresAt` not in the past;
- invite capabilities are allowed by realm/relationship context.

## UX Flow

### Export

1. User opens `My QR`.
2. App requires existing local identity.
3. App creates one-time invite payload.
4. App displays QR code and compact JSON fallback.
5. UI states: QR starts handshake and does not grant membership directly.

### Import

1. User opens `Import Invite`.
2. User scans QR or pastes JSON.
3. App validates payload.
4. App rejects invalid/self/duplicate payloads.
5. Success creates pending import / pending handshake state.
6. Contacts shows pending relationship.
7. Active communication requires placeholder or real handshake.

### Error States

- Invalid QR payload.
- Unsupported invite type.
- Unsupported invite version.
- Missing public key.
- Missing fingerprint.
- Invite expired.
- This is your own key.
- This key is already known.
- This invite was already imported.

## Fallback Copy/Paste

Manual JSON fallback remains mandatory because:

- camera may be unavailable;
- emulator testing is easier;
- screenshots can be reviewed;
- payload validation can be tested without device camera.

Fallback must use the same parser and validation path as QR scan import.

## Testing Matrix

| Test | Expected Result |
| --- | --- |
| Generate QR from local identity | QR encodes valid one-time invite payload. |
| Decode generated QR | Parsed payload matches source JSON. |
| Paste generated JSON | Creates pending import only. |
| Invalid JSON | Rejected. |
| Unsupported type | Rejected. |
| Unsupported version | Rejected. |
| Missing invite ID | Rejected. |
| Missing public key | Rejected. |
| Missing fingerprint | Rejected. |
| Self-invite | Rejected. |
| Duplicate invite ID | Rejected. |
| Duplicate public key | Routed to existing relationship/rejected, no second contact. |
| Expired invite | Rejected. |
| Tampered signed payload | Rejected after signature support exists. |
| Camera permission denied | Manual paste remains usable. |

## Policy Guard Updates

When QR scanning is added, update Android policy guards:

- allow camera permission only if scanner screen exists;
- keep phone/contact/location/device/Bluetooth permissions forbidden;
- keep public discovery UI labels forbidden;
- verify no network/server dependency was introduced.

## Review Checklist

- QR does not create active membership.
- Imported invite requires handshake.
- Realm membership still requires certificate/approval.
- No phone/email/login/account flow exists.
- No public discovery or nearby search exists.
- No cloud/server/API dependency exists.
- Signature requirements are documented before production signing.
