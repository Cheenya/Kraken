#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-run}"
BUILD_PRODUCT_NAME="KrakenDesktop"
APP_DISPLAY_NAME="Kraken Desktop"
APP_EXECUTABLE_NAME="$APP_DISPLAY_NAME"
APP_BUNDLE_NAME="$APP_DISPLAY_NAME.app"
BUNDLE_ID="com.disser.kraken.desktop"
MIN_SYSTEM_VERSION="14.0"
INSTALL_DIR="${KRAKEN_DESKTOP_INSTALL_DIR:-$HOME/Applications}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
APP_BUNDLE="$DIST_DIR/$APP_BUNDLE_NAME"
LEGACY_APP_BUNDLE="$DIST_DIR/KrakenDesktop.app"
APP_CONTENTS="$APP_BUNDLE/Contents"
APP_MACOS="$APP_CONTENTS/MacOS"
APP_RESOURCES="$APP_CONTENTS/Resources"
APP_BINARY="$APP_MACOS/$APP_EXECUTABLE_NAME"
INFO_PLIST="$APP_CONTENTS/Info.plist"

pkill -x "$APP_EXECUTABLE_NAME" >/dev/null 2>&1 || true
pkill -x "$BUILD_PRODUCT_NAME" >/dev/null 2>&1 || true

cd "$ROOT_DIR"
swift build
BUILD_BINARY="$(swift build --show-bin-path)/$BUILD_PRODUCT_NAME"

rm -rf "$APP_BUNDLE"
if [ "$LEGACY_APP_BUNDLE" != "$APP_BUNDLE" ]; then
  rm -rf "$LEGACY_APP_BUNDLE"
fi
mkdir -p "$APP_MACOS" "$APP_RESOURCES"
cp "$BUILD_BINARY" "$APP_BINARY"
chmod +x "$APP_BINARY"
find "$(dirname "$BUILD_BINARY")" -maxdepth 1 -name '*.bundle' -exec cp -R {} "$APP_RESOURCES/" \;
cp "$ROOT_DIR/Sources/KrakenDesktop/Resources/AppIcon.icns" "$APP_RESOURCES/AppIcon.icns"

cat >"$INFO_PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleExecutable</key>
  <string>$APP_EXECUTABLE_NAME</string>
  <key>CFBundleIdentifier</key>
  <string>$BUNDLE_ID</string>
  <key>CFBundleName</key>
  <string>$APP_DISPLAY_NAME</string>
  <key>CFBundleDisplayName</key>
  <string>$APP_DISPLAY_NAME</string>
  <key>CFBundleDevelopmentRegion</key>
  <string>ru</string>
  <key>CFBundleLocalizations</key>
  <array>
    <string>ru</string>
  </array>
  <key>CFBundleIconFile</key>
  <string>AppIcon</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>LSMinimumSystemVersion</key>
  <string>$MIN_SYSTEM_VERSION</string>
  <key>NSPrincipalClass</key>
  <string>NSApplication</string>
  <key>NSCameraUsageDescription</key>
  <string>Kraken использует камеру Mac для сканирования QR-приглашений с других устройств.</string>
  <key>NSBluetoothAlwaysUsageDescription</key>
  <string>Kraken использует Bluetooth для проверки локального BLE-транспорта с соседними устройствами.</string>
  <key>NSBluetoothPeripheralUsageDescription</key>
  <string>Kraken публикует локальный BLE GATT-сервис для транспортных тестов.</string>
</dict>
</plist>
PLIST

/usr/bin/codesign --force --deep --sign - "$APP_BUNDLE" >/dev/null

open_app() {
  KRAKEN_REPO_ROOT="$REPO_ROOT" /usr/bin/open -n "$APP_BUNDLE"
}

install_app() {
  mkdir -p "$INSTALL_DIR"
  rm -rf "$INSTALL_DIR/$APP_BUNDLE_NAME"
  rm -rf "$INSTALL_DIR/KrakenDesktop.app"
  cp -R "$APP_BUNDLE" "$INSTALL_DIR/"
  /usr/bin/codesign --force --deep --sign - "$INSTALL_DIR/$APP_BUNDLE_NAME" >/dev/null
  echo "Installed $INSTALL_DIR/$APP_BUNDLE_NAME"
}

case "$MODE" in
  run)
    open_app
    ;;
  --install|install)
    install_app
    ;;
  --install-run|install-run)
    install_app
    KRAKEN_REPO_ROOT="$REPO_ROOT" /usr/bin/open -n "$INSTALL_DIR/$APP_BUNDLE_NAME"
    ;;
  --debug|debug)
    KRAKEN_REPO_ROOT="$REPO_ROOT" lldb -- "$APP_BINARY"
    ;;
  --logs|logs)
    open_app
    /usr/bin/log stream --info --style compact --predicate "process == \"$APP_EXECUTABLE_NAME\""
    ;;
  --telemetry|telemetry)
    open_app
    /usr/bin/log stream --info --style compact --predicate "subsystem == \"$BUNDLE_ID\""
    ;;
  --verify|verify)
    open_app
    sleep 1
    pgrep -x "$APP_EXECUTABLE_NAME" >/dev/null
    ;;
  --verify-install|verify-install)
    install_app
    test -d "$INSTALL_DIR/$APP_BUNDLE_NAME"
    /usr/libexec/PlistBuddy -c 'Print :CFBundleDisplayName' "$INSTALL_DIR/$APP_BUNDLE_NAME/Contents/Info.plist" | grep -qx "$APP_DISPLAY_NAME"
    ;;
  *)
    echo "использование: $0 [run|--install|--install-run|--debug|--logs|--telemetry|--verify|--verify-install]" >&2
    exit 2
    ;;
esac
