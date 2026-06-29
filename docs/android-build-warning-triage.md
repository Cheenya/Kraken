# Android Build Warning Triage

Date: 2026-05-18

Scope: document current Gradle and Android build warnings without changing Android Gradle Plugin, Kotlin, Compose or NDK versions.

## Current Build Status

Current command-line status:

- `./gradlew test` passes.
- `./gradlew assembleDebug` passes.
- Native placeholder library builds for configured ABIs.
- 16 KB page-size compatibility mitigation is present in CMake through `-Wl,-z,max-page-size=16384`.

## Warning Summary

| Warning | Risk | Blocks Release | Fix Now? |
| --- | --- | --- | --- |
| `android.builtInKotlin=false` deprecated | Medium | Not for debug prototype | Later |
| `android.newDsl=false` deprecated | Medium | Not for debug prototype | Later |
| Legacy variant API warnings | Medium | Not for debug prototype | Later |
| Configuration cache suggestion | Low | No | Later |
| 16 KB native page-size warning | Medium if it reappears | Could block newer devices later | Monitor now |
| Dependency constraint flags | Low/Medium | No current block | Later |

## Deprecated `android.builtInKotlin=false`

Current message summary:

```text
WARNING: The option setting 'android.builtInKotlin=false' is deprecated.
The current default is 'true'.
It will be removed in version 10.0 of the Android Gradle plugin.
```

Risk level: medium.

Blocks release: not currently. It is a forward-compatibility warning.

Recommended fix:

- Investigate why the flag was needed in this project.
- Remove the flag only in a dedicated build-cleanup branch.
- Run full Gradle test and assemble after removal.

Fix now or later: later. It should not be mixed with UI or protocol stabilization.

## Deprecated `android.newDsl=false`

Current message summary:

```text
WARNING: The option setting 'android.newDsl=false' is deprecated.
The current default is 'true'.
It will be removed in version 10.0 of the Android Gradle plugin.
```

Risk level: medium.

Blocks release: not currently. It may block future AGP 10 migration.

Recommended fix:

- Use `./gradlew assembleDebug -Pandroid.debug.obsoleteApi=true` to identify the caller.
- Migrate whatever uses legacy variant APIs to Android Components Extension.
- Remove `android.newDsl=false` only after the caller is fixed.

Fix now or later: later, unless AGP upgrade becomes urgent.

## Obsolete Legacy Variant APIs

Current message summary:

```text
WARNING: API 'applicationVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
WARNING: API 'testVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
WARNING: API 'unitTestVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
```

Risk level: medium.

Blocks release: not for the current debug prototype.

Recommended fix:

- Run with `-Pandroid.debug.obsoleteApi=true`.
- Identify whether AGP internals, a plugin or local build script is triggering the legacy API.
- If local scripts trigger it, migrate to `androidComponents`.
- If a dependency/plugin triggers it, pin or upgrade the plugin deliberately.

Fix now or later: later. Keep build green until the UI/protocol review stabilizes.

## Configuration Cache Suggestion

Current message summary:

```text
Consider enabling configuration cache to speed up this build.
```

Risk level: low.

Blocks release: no.

Recommended fix:

- Test with `./gradlew test --configuration-cache`.
- Enable only if all build tasks are compatible.

Fix now or later: later. This is a performance improvement, not correctness.

## 16 KB Native Page-Size Status

Current status:

- The app contains native research library `libkraken_native_placeholder.so`.
- CMake sets:

```cmake
target_link_options(
    kraken_native_placeholder
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)
```

Risk level: medium if Android Studio still reports the warning.

Blocks release: potentially for newer 16 KB page-size Android devices, but current command-line debug build succeeds.

Recommended fix:

- Re-run Android Studio APK Analyzer or Play/Studio compatibility check after each native build change.
- Keep the linker flag unless the NDK/toolchain provides a newer recommended configuration.
- Do not remove the native library just to silence the warning; it is the current research C++ boundary and benchmark target.

Fix now or later: monitor now, revisit before any public APK distribution.

## Dependency Constraint And AGP Flags

Current `gradle.properties` includes compatibility flags:

```properties
android.dependency.useConstraints=false
android.r8.strictFullModeForKeepRules=false
android.uniquePackageNames=false
```

Risk level: low to medium.

Blocks release: not currently.

Recommended fix:

- Change one flag at a time in a dedicated build-hygiene task.
- Run `./gradlew test` and `./gradlew assembleDebug` after each flag change.
- Avoid bundling these changes with product features.

Fix now or later: later.

## Recommended Build Hygiene Order

1. Keep current build flags until manual app review is complete.
2. Capture exact `-Pandroid.debug.obsoleteApi=true` output.
3. Identify the legacy variant API caller.
4. Remove `android.newDsl=false` only after the caller is migrated.
5. Remove `android.builtInKotlin=false` in a separate commit and verify.
6. Test configuration cache separately.
7. Re-check 16 KB native compatibility after native code changes.

## Current Decision

Do not blindly change build versions or AGP flags in this stabilization batch. The current priority is to preserve a buildable app, document the warnings and make the next cleanup path explicit.
