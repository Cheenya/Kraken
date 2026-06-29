# Production Secure Messenger Scope Note

Date: 2026-06-12.

This note records the product/security scope decision discussed after the
current Kraken `10/10` blocker audit.

## Short Answer

Turning Kraken from a dissertation research prototype into a production secure
messenger is a separate major roadmap, not a small extension of the current
Adamova product path.

The practical development impact is high:

| Target | Meaning | Relative effort |
| --- | --- | --- |
| Research prototype | Adamova participates in experimental profile policy with explicit claim boundaries. | current path |
| Secure-by-design prototype | Add reviewed E2EE architecture, key lifecycle, secure storage and threat model. | about 2x-3x |
| Production secure messenger | Signal-like protocol properties, multi-device/recovery policy, hardening, abuse model and security reviews. | about 5x-10x |
| Production secure messenger with a custom algorithm as the primary security basis | Add formal cryptographic review, independent audit, misuse analysis and standards comparison for the custom algorithm. | 10x+ and high risk |

## Decision Boundary

Kraken can evolve toward a production secure messenger, but the current
research prototype must not be described as one.

For dissertation and near-term prototype work:

- Adamova remains an experimental crypto-profile admission/policy layer.
- Adamova can block weak experimental profiles before session or message use.
- Adamova can appear in QR, relationship, packet and session-policy metadata.
- Adamova must not be presented as production message encryption.
- Adamova must not be presented as proof of production cryptographic security.

For a future production secure messenger:

- use a reviewed E2EE baseline for message confidentiality and integrity;
- define identity keys, signed handshake, session keys, key rotation and replay
  handling independently of Adamova;
- use platform secure storage for private key material;
- separate research/debug builds from release/prod builds technically, not only
  in wording;
- require threat-model, crypto-design, Android-security and transport-abuse
  review gates before any production security claim.

## Why This Matters

The hard part is not "adding encryption" to packets. The hard part is the full
security system around it:

- safe first-device and peer binding;
- MITM resistance during QR/handshake;
- forward secrecy and post-compromise security;
- key reset, lost device and reinstall behavior;
- replay, duplicate, tamper and downgrade handling;
- Android secure storage and backup policy;
- multi-device and recovery model;
- independent review before public security claims.

## Recommended Path

1. Close the current `10/10` research-prototype evidence gates honestly:
   Wi-Fi Direct, physical relay/attack evidence and route benchmark coverage.
2. Use the dissertation wording: Kraken is a research prototype with an
   experimental Adamova crypto-policy/admission path.
3. Create a separate `Kraken Secure Architecture` roadmap for production E2EE.
4. In that roadmap, keep Adamova as an additional experimental/admission layer
   unless and until it passes formal cryptographic review and independent audit.

## Wording To Preserve

Acceptable:

> Kraken is a research prototype demonstrating local/offline messaging flows
> and an experimental Adamova admission policy for crypto-profile metadata.

Not acceptable:

> Kraken is a production secure messenger.

Not acceptable:

> Adamova proves production message security.

Not acceptable:

> The custom algorithm replaces reviewed E2EE protocol design.
