# Android Keystore Migration Plan

Status: plan only. The current app still uses placeholder identity key references.

## Problem

SharedPreferences-backed placeholder identities are acceptable for the research MVP,
but they are not production key storage.

## Target

- private signing/agreement keys are non-exportable;
- public identity material remains shareable via QR;
- old placeholder identities are clearly marked as prototype identities;
- no silent upgrade to trusted production identity;
- user sees when a new production identity must be created.

## Migration Steps

1. Add `AndroidKeystoreIdentityKeyProvider`.
2. Add identity metadata field for key provider type.
3. Mark existing placeholder identities as `prototype`.
4. Add explicit migration/reset UI.
5. Sign QR payloads with Keystore-backed key.
6. Verify signatures before activating contacts.
7. Block release/prod build if placeholder identity provider is active.

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
- no production security claim before review.
