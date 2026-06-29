# Wi-Fi Direct AF_INET Listener Attempt

Date: `2026-06-11`.

## Change

`WifiDirectTransport` now uses `android.system.Os.socket(AF_INET, SOCK_STREAM,
IPPROTO_TCP)` for the Wi-Fi Direct listener instead of `java.net.ServerSocket`.
The intent is to avoid the IPv4-mapped `tcp6` listener observed in earlier
diagnostics and create a true IPv4 listener for P2P traffic.

The implementation keeps `minSdk=26` compatibility:

- no `StructTimeval`;
- no `Os.setsockoptTimeval`;
- no `Os.fcntlInt`;
- read timeout is implemented with `Os.poll` + `Os.read`.

## Validation

Passed:

- `./gradlew :app:testDebugUnitTest --tests com.disser.kraken.mesh.MeshEvidenceExportTest --tests com.disser.kraken.mesh.WifiDirectDnsSdTest --tests com.disser.kraken.mesh.MeshServiceTest`
- `./gradlew assembleDebug`
- `git diff --check`
- APK installed on Samsung `R5CY22X6MSB`

Not completed:

- Two-device Wi-Fi Direct capture, because Xiaomi `d948ffd0` was not visible in
  ADB during this pass.

Later validation update:

- Commit `05ba28d` adds the Android <=12 companion
  `ACCESS_COARSE_LOCATION` permission, capped with `maxSdkVersion=32`, and
  keeps Android 13+ on `NEARBY_WIFI_DEVICES` with `neverForLocation`.
- After that commit, `./gradlew :app:lintDebug` passes.
- This closes the manifest/lint policy issue only; the two-device Wi-Fi Direct
  route proof remains open.

## Claim Boundary

This is an implementation attempt plus single-device install validation. It
does not prove Wi-Fi Direct delivery, bidirectional reliability, route-bound
negative tests, production network reliability or production security.
