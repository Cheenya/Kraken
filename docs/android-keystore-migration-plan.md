# Android Keystore Migration Plan

Status: migration plan for moving identity key references into Android Keystore.

## Problem

SharedPreferences-backed compatibility identities are a transition layer for
the current Android research build; Android Keystore becomes the target storage.

## Target

- private signing/agreement keys are non-exportable;
- public identity material remains shareable via QR;
- old compatibility identities are clearly marked;
- no silent upgrade to trusted Keystore identity;
- user sees when a new Keystore identity must be created.

## Migration Steps

1. Add `AndroidKeystoreIdentityKeyProvider`.
2. Add identity metadata field for key provider type.
3. Mark existing compatibility identities explicitly.
4. Add explicit migration/reset UI.
5. Sign QR payloads with Keystore-backed key.
6. Verify signatures before activating contacts.
7. Block release/prod build if compatibility identity provider is active.

## Risks

- device backup/restore behavior;
- biometric/lockscreen availability;
- lost-device recovery;
- key rotation;
- relationship re-verification after identity reset.

## Non-Goals

- no account recovery server;
- no phone/email login;
- no cloud key backup;
- финальные security-claims фиксируются после review.
