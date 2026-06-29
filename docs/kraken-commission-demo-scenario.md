# Kraken Commission Demo Scenario

## Purpose

This scenario gives the dissertation committee a deterministic way to inspect
Kraken as an Android-first research prototype. It is not a production security
claim and it does not require any account server, public discovery or cloud
message history.

## Preconditions

- The APK is installed locally on demo Android devices.
- Devices do not need phone, email, Google account login or public user search.
- Demo participants understand that QR/import, handshake and delivery are MVP
  flows with local placeholders where the roadmap says so.
- Research diagnostics are shown as dissertation tooling, not production
  encryption.

## Demo Flow

1. Committee installs the APK on one or more Android devices.
2. Each participant opens Kraken and creates a local identity using display name
   only.
3. Kraken shows a fingerprint. Explain that the display name is a label and the
   key/fingerprint is the identity.
4. The project owner opens My QR and shows a one-time invite QR for the demo.
5. A participant scans the QR and enters pending handshake state.
6. The relationship becomes ACTIVE only after the reciprocal offline QR
   handshake.
7. A demo realm exists or is created locally by the owner. It shows capacity,
   policy and membership certificate state.
8. A opens the chat placeholder with Y. The UI shows that sending is permitted
   only for ACTIVE relationships.
9. The local delivery simulator demonstrates A -> B -> Y or A -> B -> C -> Y,
   delivery receipt return and tombstone cleanup on honest relay buffers.
10. The Research screen shows diagnostic-only curve workflow and native core
    scaffold status.
11. Mesh Status shows relay mode, battery policy and local Courier Score summary
    when those models are available.

## What To Say Explicitly

- Kraken is invite-only.
- There is no public discovery.
- There is no phone/email/login registration.
- QR starts a handshake and does not grant membership directly.
- New key means new user.
- Root governance cannot decrypt messages.
- Tombstone deletion is best-effort and applies to honest relay behavior.
- Direct messages are primary; channels are preferred over large groups.
- Small groups are strictly limited.

## What Not To Claim

- Do not claim production security.
- Do not claim the dissertation algorithm already accelerates cryptography.
- Do not claim tombstones guarantee deletion from malicious devices.
- Do not imply any central server or public directory exists.

## Manual Review Checklist

- Create identity with display name only.
- Confirm no phone/email/login fields are present.
- Export invite payload and confirm it says handshake, not membership.
- Import invite and confirm relationship is pending before handshake.
- Accept placeholder handshake and confirm chat composer becomes available.
- Unlink relationship and confirm sending is disabled.
- Create demo realm and inspect membership certificate/capacity policy.
- Create demo channel and small group; confirm no public search is offered.
- Open Research and confirm diagnostic-only wording.
- Open Mesh Status and confirm local-only relay/Courier Score wording.
