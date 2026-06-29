#!/usr/bin/env python3
from __future__ import annotations

import json
import plistlib
import struct
import sys
import zlib
from pathlib import Path


EXPECTED_SCREENSHOTS = [
    "kraken-ios-native-welcome.png",
    "kraken-ios-native-home.png",
    "kraken-ios-native-contacts.png",
    "kraken-ios-native-contacts-bottom.png",
    "kraken-ios-native-contacts-qr-invite.png",
    "kraken-ios-native-contacts-qr-response.png",
    "kraken-ios-native-contacts-qr-response-bottom.png",
    "kraken-ios-native-contacts-qr-confirmation.png",
    "kraken-ios-native-realms.png",
    "kraken-ios-native-realms-bottom.png",
    "kraken-ios-native-settings.png",
    "kraken-ios-native-settings-bottom.png",
    "kraken-ios-compact-welcome.png",
    "kraken-ios-compact-contacts-qr-response-bottom.png",
    "kraken-ios-compact-settings-bottom.png",
    "kraken-ios-tablet-welcome.png",
    "kraken-ios-tablet-settings-bottom.png",
]

STALE_PATTERNS = [
    "kraken-ios-android-",
    "kraken-ios-chat",
    "kraken-ios-native-chats.png",
    "kraken-ios-launch.png",
    "kraken-ios-native-launch.png",
    "tmp-",
]

DOCS_TO_CHECK = [
    Path("artifacts/ios-smoke/README.md"),
    Path("app-ios/README.md"),
    Path("docs/kraken-ios-technical-design-state.md"),
]

EXPECTED_DIMENSIONS = {
    "kraken-ios-compact-welcome.png": (750, 1334),
    "kraken-ios-compact-contacts-qr-response-bottom.png": (750, 1334),
    "kraken-ios-compact-settings-bottom.png": (750, 1334),
    "kraken-ios-tablet-welcome.png": (1640, 2360),
    "kraken-ios-tablet-settings-bottom.png": (1640, 2360),
}

WELCOME_SCREENSHOTS = {
    "kraken-ios-native-welcome.png",
    "kraken-ios-compact-welcome.png",
    "kraken-ios-tablet-welcome.png",
}

BOTTOM_CHROME_SCREENSHOTS = [
    name for name in EXPECTED_SCREENSHOTS
    if name not in WELCOME_SCREENSHOTS and not name.startswith("kraken-ios-tablet-")
]

TOP_CHROME_SCREENSHOTS = [
    "kraken-ios-tablet-settings-bottom.png",
]

MANIFEST_PATH = Path("artifacts/ios-smoke/current-manifest.json")
LAUNCH_REFERENCE_NAME = "kraken-ios-launch-reference.png"
LAUNCH_REFERENCE_SIZE = (1170, 2532)
BRAND_SLOGAN = "ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО"
STALE_BRAND_SLOGAN = "ПРИВАТНО  •  РЯДОМ  •  СВОБОДНО"


def read_png_size(path: Path) -> tuple[int, int]:
    with path.open("rb") as handle:
        header = handle.read(24)
    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise ValueError("invalid PNG header")
    return struct.unpack(">II", header[16:24])


def read_png_rgba_pixels(path: Path) -> tuple[int, int, list[tuple[int, int, int, int]]]:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("invalid PNG signature")
    offset = 8
    width = height = color_type = None
    compressed = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        kind = data[offset + 4:offset + 8]
        body = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if kind == b"IHDR":
            width, height, bit_depth, color_type, compression, filter_method, interlace = struct.unpack(">IIBBBBB", body)
            if bit_depth != 8 or compression != 0 or filter_method != 0 or interlace != 0 or color_type not in {2, 6}:
                raise ValueError("unsupported PNG format")
        elif kind == b"IDAT":
            compressed.extend(body)
        elif kind == b"IEND":
            break
    if width is None or height is None or color_type is None:
        raise ValueError("missing PNG IHDR")

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(bytes(compressed))
    previous = bytearray(stride)
    position = 0
    pixels: list[tuple[int, int, int, int]] = []
    for _ in range(height):
        filter_type = raw[position]
        position += 1
        scanline = bytearray(raw[position:position + stride])
        position += stride
        for index in range(stride):
            left = scanline[index - channels] if index >= channels else 0
            above = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_type == 1:
                scanline[index] = (scanline[index] + left) & 0xFF
            elif filter_type == 2:
                scanline[index] = (scanline[index] + above) & 0xFF
            elif filter_type == 3:
                scanline[index] = (scanline[index] + ((left + above) // 2)) & 0xFF
            elif filter_type == 4:
                estimate = left + above - upper_left
                candidates = [(abs(estimate - left), left), (abs(estimate - above), above), (abs(estimate - upper_left), upper_left)]
                scanline[index] = (scanline[index] + min(candidates, key=lambda item: item[0])[1]) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"unsupported PNG filter {filter_type}")
        previous = scanline
        for index in range(0, stride, channels):
            pixels.append((
                scanline[index],
                scanline[index + 1],
                scanline[index + 2],
                scanline[index + 3] if channels == 4 else 255,
            ))
    return width, height, pixels


def read_png_visual_stats(path: Path, *, bottom_start_ratio: float = 0.86) -> dict[str, int]:
    width, height, pixels = read_png_rgba_pixels(path)
    bottom_start = int(height * bottom_start_ratio)
    stats = {
        "width": width,
        "height": height,
        "bright": 0,
        "cyan": 0,
        "nonDark": 0,
        "topBright": 0,
        "topCyan": 0,
        "topNonDark": 0,
        "bottomBright": 0,
        "bottomCyan": 0,
        "bottomNonDark": 0,
    }
    top_end = int(height * 0.12)
    for index, (red, green, blue, alpha) in enumerate(pixels):
        if not alpha:
            continue
        is_bright = red > 150 and green > 150 and blue > 150
        is_cyan = green > 120 and blue > 120 and red < 120
        is_non_dark = (red + green + blue) > 120
        if is_bright:
            stats["bright"] += 1
        if is_cyan:
            stats["cyan"] += 1
        if is_non_dark:
            stats["nonDark"] += 1
        row = index // width
        if row < top_end:
            if is_bright:
                stats["topBright"] += 1
            if is_cyan:
                stats["topCyan"] += 1
            if is_non_dark:
                stats["topNonDark"] += 1
        if row >= bottom_start:
            if is_bright:
                stats["bottomBright"] += 1
            if is_cyan:
                stats["bottomCyan"] += 1
            if is_non_dark:
                stats["bottomNonDark"] += 1
    return stats


def has_bottom_tab_chrome(path: Path) -> bool:
    stats = read_png_visual_stats(path)
    return (
        stats["bottomBright"] >= 1000
        and stats["bottomCyan"] >= 500
        and stats["bottomNonDark"] >= 8000
    )


def has_tablet_top_tab_chrome(path: Path) -> bool:
    stats = read_png_visual_stats(path)
    return (
        stats["topBright"] >= 6000
        and stats["topCyan"] >= 1500
        and stats["topNonDark"] >= 30000
    )


def require(condition: bool, failures: list[str], message: str) -> None:
    if not condition:
        failures.append(message)


def icon_pixel_size(size: str, scale: str) -> tuple[int, int]:
    width, height = (float(part) for part in size.split("x"))
    multiplier = int(scale.removesuffix("x"))
    return (int(round(width * multiplier)), int(round(height * multiplier)))


def asset_color(repo_root: Path, name: str) -> tuple[int, int, int]:
    contents_path = repo_root / f"app-ios/KrakenIOS/Assets.xcassets/{name}.colorset/Contents.json"
    contents = json.loads(contents_path.read_text(encoding="utf-8"))
    components = contents["colors"][0]["color"]["components"]
    return tuple(int(components[channel], 16) for channel in ("red", "green", "blue"))


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    failures: list[str] = []
    smoke_dir = repo_root / "artifacts/ios-smoke"
    manifest_path = repo_root / MANIFEST_PATH
    require(manifest_path.exists(), failures, f"missing current evidence manifest: {MANIFEST_PATH}")
    current_manifest: dict[str, object] = {}
    if manifest_path.exists():
        try:
            current_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as error:
            failures.append(f"invalid current evidence manifest JSON: {error}")
        else:
            current_screenshots = current_manifest.get("currentScreenshots")
            require(
                current_screenshots == EXPECTED_SCREENSHOTS,
                failures,
                "current evidence manifest must list exactly the verifier's current screenshot set",
            )
            compact_viewport = current_manifest.get("compactViewport")
            if isinstance(compact_viewport, dict):
                require(
                    compact_viewport.get("expectedPixels") == [750, 1334],
                    failures,
                    "current evidence manifest must pin compact viewport to 750x1334",
                )
            else:
                failures.append("current evidence manifest missing compactViewport object")
            tablet_viewport = current_manifest.get("tabletViewport")
            if isinstance(tablet_viewport, dict):
                require(
                    tablet_viewport.get("device") == "iPad (A16)",
                    failures,
                    "current evidence manifest must pin tablet viewport device",
                )
                require(
                    tablet_viewport.get("screenshots") == [
                        "kraken-ios-tablet-welcome.png",
                        "kraken-ios-tablet-settings-bottom.png",
                    ],
                    failures,
                    "current evidence manifest must list tablet screenshots",
                )
                require(
                    "physical iPad review remains a device gate" in str(tablet_viewport.get("note", "")),
                    failures,
                    "tablet manifest note must preserve physical iPad gate",
                )
            else:
                failures.append("current evidence manifest missing tabletViewport object")
            stale_excluded = current_manifest.get("staleExcludedPatterns")
            require(
                stale_excluded == STALE_PATTERNS,
                failures,
                "current evidence manifest staleExcludedPatterns must match verifier stale patterns",
            )
            if isinstance(current_screenshots, list):
                for stale_pattern in STALE_PATTERNS:
                    require(
                        not any(isinstance(name, str) and stale_pattern in name for name in current_screenshots),
                        failures,
                        f"stale screenshot pattern appears in current evidence manifest: {stale_pattern}",
                    )
            launch_reference = current_manifest.get("launchReference")
            if isinstance(launch_reference, dict):
                require(
                    launch_reference.get("file") == LAUNCH_REFERENCE_NAME,
                    failures,
                    "current evidence manifest must point at the launch reference image",
                )
                require(
                    launch_reference.get("pixels") == list(LAUNCH_REFERENCE_SIZE),
                    failures,
                    "current evidence manifest must pin launch reference pixels",
                )
                require(
                    "not a Simulator launch screenshot" in str(launch_reference.get("note", "")),
                    failures,
                    "launch reference manifest note must not claim Simulator screenshot evidence",
                )
            else:
                failures.append("current evidence manifest missing launchReference object")

    for name in EXPECTED_SCREENSHOTS:
        path = smoke_dir / name
        require(path.exists(), failures, f"missing screenshot: {path.relative_to(repo_root)}")
        if path.exists():
            try:
                width, height = read_png_size(path)
            except ValueError as error:
                failures.append(f"invalid PNG {path.relative_to(repo_root)}: {error}")
            else:
                require(width >= 300 and height >= 600, failures, f"unexpected screenshot size for {name}: {width}x{height}")
                if name in EXPECTED_DIMENSIONS:
                    require((width, height) == EXPECTED_DIMENSIONS[name], failures, f"unexpected compact screenshot size for {name}: {width}x{height}")

    for name in BOTTOM_CHROME_SCREENSHOTS:
        path = smoke_dir / name
        if path.exists():
            try:
                require(
                    has_bottom_tab_chrome(path),
                    failures,
                    f"{name} must show rendered app content with native bottom tab chrome, not an early launch frame",
                )
            except ValueError as error:
                failures.append(f"invalid visual stats for {path.relative_to(repo_root)}: {error}")

    for name in TOP_CHROME_SCREENSHOTS:
        path = smoke_dir / name
        if path.exists():
            try:
                require(
                    has_tablet_top_tab_chrome(path),
                    failures,
                    f"{name} must show rendered iPad app content with top tab chrome, not an early launch frame",
                )
            except ValueError as error:
                failures.append(f"invalid visual stats for {path.relative_to(repo_root)}: {error}")

    require(
        not (smoke_dir / "kraken-ios-native-launch.png").exists(),
        failures,
        "kraken-ios-native-launch.png must not be present; simctl launch transition captures are not valid launch evidence",
    )
    launch_reference_path = smoke_dir / LAUNCH_REFERENCE_NAME
    require(launch_reference_path.exists(), failures, f"missing launch reference: {launch_reference_path.relative_to(repo_root)}")
    if launch_reference_path.exists():
        try:
            width, height, pixels = read_png_rgba_pixels(launch_reference_path)
        except ValueError as error:
            failures.append(f"invalid launch reference PNG {launch_reference_path.relative_to(repo_root)}: {error}")
        else:
            require((width, height) == LAUNCH_REFERENCE_SIZE, failures, f"unexpected launch reference size: {width}x{height}")
            background = asset_color(repo_root, "BrandBackground")
            require(
                pixels[0][:3] == background,
                failures,
                f"launch reference background must match BrandBackground: {pixels[0][:3]} != {background}",
            )
            center = pixels[(height // 2) * width + (width // 2)]
            require(center[:3] != background, failures, "launch reference center must contain LaunchGlyph pixels")

    docs = {path: (repo_root / path).read_text(encoding="utf-8") for path in DOCS_TO_CHECK}
    root_readme = (repo_root / "README.md").read_text(encoding="utf-8")
    gitignore = (repo_root / ".gitignore").read_text(encoding="utf-8")
    android_welcome = (repo_root / "app-android/app/src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt").read_text(encoding="utf-8")
    require(
        BRAND_SLOGAN in root_readme,
        failures,
        "root README must pin the current Kraken brand slogan for cross-platform source-of-truth",
    )
    require(BRAND_SLOGAN in android_welcome, failures, "Android welcome source must use the current Kraken brand slogan")
    require(STALE_BRAND_SLOGAN not in android_welcome, failures, "Android welcome source must not use stale РЯДОМ copy")
    for name in EXPECTED_SCREENSHOTS:
        for path, text in docs.items():
            require(name in text, failures, f"{name} is not listed in {path}")
    for path, text in docs.items():
        require(
            "current-manifest.json" in text,
            failures,
            f"current evidence manifest is not documented in {path}",
        )
        require(
            LAUNCH_REFERENCE_NAME in text,
            failures,
            f"launch reference image is not documented in {path}",
        )
    manifest = docs[Path("artifacts/ios-smoke/README.md")]
    for allow_pattern in [
        "!artifacts/ios-smoke/kraken-ios-native-*.png",
        "!artifacts/ios-smoke/kraken-ios-compact-*.png",
        "!artifacts/ios-smoke/kraken-ios-tablet-*.png",
    ]:
        require(allow_pattern in gitignore, failures, f".gitignore must allow current smoke evidence pattern: {allow_pattern}")
        require(allow_pattern.removeprefix("!artifacts/ios-smoke/") in manifest, failures, f"smoke README must document gitignore evidence allow pattern: {allow_pattern}")

    for stale_pattern in STALE_PATTERNS:
        if stale_pattern == "kraken-ios-native-launch.png":
            require(stale_pattern not in manifest, failures, "native launch screenshot must not be listed as current evidence")
        else:
            require(stale_pattern in manifest, failures, f"stale pattern not documented in artifacts manifest: {stale_pattern}")

    info_plist_path = repo_root / "app-ios/KrakenIOS/Info.plist"
    xcode_project = (repo_root / "app-ios/KrakenIOS.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    xcode_scheme = (repo_root / "app-ios/KrakenIOS.xcodeproj/xcshareddata/xcschemes/KrakenIOS.xcscheme").read_text(encoding="utf-8")
    with info_plist_path.open("rb") as handle:
        info_plist = plistlib.load(handle)
    for marker in [
        "PRODUCT_BUNDLE_IDENTIFIER = local.kraken.ios;",
        "INFOPLIST_FILE = KrakenIOS/Info.plist;",
        "GENERATE_INFOPLIST_FILE = NO;",
        "ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;",
        'TARGETED_DEVICE_FAMILY = "1,2";',
        'SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";',
    ]:
        require(marker in xcode_project, failures, f"Xcode project missing required app build setting: {marker}")
    for marker in [
        'BuildableName = "KrakenIOS.app"',
        'BlueprintName = "KrakenIOS"',
        'BuildableName = "KrakenIOSTests.xctest"',
        'shouldUseLaunchSchemeArgsEnv = "YES"',
    ]:
        require(marker in xcode_scheme, failures, f"Xcode scheme missing required marker: {marker}")
    launch_screen = info_plist.get("UILaunchScreen", {})
    require(info_plist.get("CFBundleDisplayName") == "Kraken", failures, "Info.plist CFBundleDisplayName must stay Kraken")
    url_types = info_plist.get("CFBundleURLTypes", [])
    kraken_url_type = next((item for item in url_types if isinstance(item, dict) and item.get("CFBundleURLName") == "local.kraken.ios"), None)
    require(kraken_url_type is not None, failures, "Info.plist must declare local.kraken.ios URL type")
    if isinstance(kraken_url_type, dict):
        require("kraken" in kraken_url_type.get("CFBundleURLSchemes", []), failures, "Info.plist local.kraken.ios URL type must include kraken scheme")
    require(launch_screen.get("UIImageName") == "LaunchGlyph", failures, "UILaunchScreen UIImageName must be LaunchGlyph")
    require(launch_screen.get("UIColorName") == "BrandBackground", failures, "UILaunchScreen UIColorName must be BrandBackground")
    require(
        info_plist.get("UISupportedInterfaceOrientations") == ["UIInterfaceOrientationPortrait"],
        failures,
        "iPhone supported orientations must stay portrait-only until landscape UI has dedicated smoke evidence",
    )
    require(
        info_plist.get("UISupportedInterfaceOrientations~ipad") == [
            "UIInterfaceOrientationPortrait",
            "UIInterfaceOrientationPortraitUpsideDown",
            "UIInterfaceOrientationLandscapeLeft",
            "UIInterfaceOrientationLandscapeRight",
        ],
        failures,
        "iPad supported orientations must include portrait and landscape entries",
    )
    require(
        "NSCameraUsageDescription" in info_plist,
        failures,
        "Info.plist must include NSCameraUsageDescription for QR scanner",
    )
    require(
        "NSLocalNetworkUsageDescription" in info_plist and "NSBluetoothAlwaysUsageDescription" in info_plist,
        failures,
        "Info.plist must include local-network and Bluetooth usage descriptions for nearby transport",
    )
    for privacy_key in ["NSCameraUsageDescription", "NSLocalNetworkUsageDescription", "NSBluetoothAlwaysUsageDescription"]:
        privacy_text = str(info_plist.get(privacy_key, ""))
        require("Kraken" in privacy_text, failures, f"{privacy_key} must name Kraken in user-facing permission copy")
        require("Wi-Fi Direct" not in privacy_text, failures, f"{privacy_key} must not claim Android Wi-Fi Direct on iOS")
    bonjour_services = info_plist.get("NSBonjourServices", [])
    require("_kraken-ios._tcp" in bonjour_services, failures, "Info.plist NSBonjourServices must include _kraken-ios._tcp for MultipeerConnectivity discovery")

    glyph_contents_path = repo_root / "app-ios/KrakenIOS/Assets.xcassets/LaunchGlyph.imageset/Contents.json"
    glyph_contents = json.loads(glyph_contents_path.read_text(encoding="utf-8"))
    expected_glyphs = {
        "1x": ("launch-glyph-120.png", (120, 120)),
        "2x": ("launch-glyph-240.png", (240, 240)),
        "3x": ("launch-glyph-360.png", (360, 360)),
    }
    found_glyphs = {image.get("scale"): image.get("filename") for image in glyph_contents.get("images", [])}
    for scale, (filename, expected_size) in expected_glyphs.items():
        require(found_glyphs.get(scale) == filename, failures, f"LaunchGlyph {scale} must reference {filename}")
        glyph_path = glyph_contents_path.parent / filename
        require(glyph_path.exists(), failures, f"missing LaunchGlyph image: {glyph_path.relative_to(repo_root)}")
        if glyph_path.exists():
            try:
                actual_size = read_png_size(glyph_path)
            except ValueError as error:
                failures.append(f"invalid LaunchGlyph PNG {glyph_path.relative_to(repo_root)}: {error}")
            else:
                require(actual_size == expected_size, failures, f"unexpected LaunchGlyph size for {filename}: {actual_size}")

    app_icon_contents_path = repo_root / "app-ios/KrakenIOS/Assets.xcassets/AppIcon.appiconset/Contents.json"
    app_icon_contents = json.loads(app_icon_contents_path.read_text(encoding="utf-8"))
    required_icon_slots = {
        ("iphone", "20x20", "2x"),
        ("iphone", "20x20", "3x"),
        ("iphone", "29x29", "2x"),
        ("iphone", "29x29", "3x"),
        ("iphone", "40x40", "2x"),
        ("iphone", "40x40", "3x"),
        ("iphone", "60x60", "2x"),
        ("iphone", "60x60", "3x"),
        ("ipad", "20x20", "1x"),
        ("ipad", "20x20", "2x"),
        ("ipad", "29x29", "1x"),
        ("ipad", "29x29", "2x"),
        ("ipad", "40x40", "1x"),
        ("ipad", "40x40", "2x"),
        ("ipad", "76x76", "1x"),
        ("ipad", "76x76", "2x"),
        ("ipad", "83.5x83.5", "2x"),
        ("ios-marketing", "1024x1024", "1x"),
    }
    found_icon_slots: set[tuple[str, str, str]] = set()
    for image in app_icon_contents.get("images", []):
        idiom = image.get("idiom")
        size = image.get("size")
        scale = image.get("scale")
        filename = image.get("filename")
        if not idiom or not size or not scale:
            failures.append(f"malformed AppIcon slot: {image}")
            continue
        slot = (idiom, size, scale)
        found_icon_slots.add(slot)
        require(filename is not None, failures, f"AppIcon slot {slot} must reference a PNG filename")
        if filename is not None:
            icon_path = app_icon_contents_path.parent / filename
            require(icon_path.exists(), failures, f"missing AppIcon image: {icon_path.relative_to(repo_root)}")
            if icon_path.exists():
                try:
                    actual_size = read_png_size(icon_path)
                except ValueError as error:
                    failures.append(f"invalid AppIcon PNG {icon_path.relative_to(repo_root)}: {error}")
                else:
                    require(actual_size == icon_pixel_size(size, scale), failures, f"unexpected AppIcon size for {slot}: {actual_size}")
    for slot in sorted(required_icon_slots - found_icon_slots):
        failures.append(f"missing AppIcon slot: {slot}")

    root_view = (repo_root / "app-ios/KrakenIOS/Views/KrakenIOSRootView.swift").read_text(encoding="utf-8")
    transport_source = (repo_root / "app-ios/KrakenIOS/Transport/IOSNearbyTransport.swift").read_text(encoding="utf-8")
    fixtures_source = (repo_root / "app-ios/KrakenIOS/Core/KrakenIOSFixtures.swift").read_text(encoding="utf-8")
    require('serviceType = "kraken-ios"' in transport_source, failures, "IOSNearbyTransportDescriptor serviceType must be kraken-ios")
    require('transportId = "ios-multipeerconnectivity"' in transport_source, failures, "IOSNearbyTransportDescriptor transportId must stay ios-multipeerconnectivity")
    require("peerNotConnected" in transport_source, failures, "IOSNearbyTransportAdapter must expose targeted-send peer-not-connected errors")
    require("public func send(_ data: Data, toPeerNamed peerDisplayName: String)" in transport_source, failures, "IOSNearbyTransportAdapter must support targeted sends to a selected peer")
    require('info?["transport"] == descriptor.transportId' in transport_source, failures, "IOSNearbyTransportAdapter must filter incompatible discoveryInfo before inviting peers")
    require("toPeerNamed: relationship.peerDisplayName" in root_view, failures, "Message sends must target the selected relationship peer instead of broadcasting to all connected peers")
    require("GlassEffectContainer" in root_view, failures, "Kraken root UI must group custom glass surfaces with GlassEffectContainer")
    require('displayName: "Kraken"' in fixtures_source, failures, "Demo fixtures must use platform-neutral Kraken identity copy")
    require("Kraken iPhone" not in fixtures_source, failures, "Demo fixtures must not use iPhone-specific identity copy because iPad smoke uses the same data")
    require(
        "tabBarMinimizeBehavior(.onScrollDown)" in root_view,
        failures,
        "Kraken TabView must opt into native iPhone tab bar minimize-on-scroll behavior on iOS 26",
    )
    for tab_case, title in [
        ("home", "Чаты"),
        ("contacts", "Контакты"),
        ("realms", "Реалмы"),
        ("settings", "Настройки"),
    ]:
        require(
            root_view.count(f".tag(KrakenTab.{tab_case})") == 1,
            failures,
            f"Kraken TabView must define exactly one tag for {tab_case}",
        )
        require(
            f'case .{tab_case}: "{title}"' in root_view,
            failures,
            f"KrakenTab title missing or stale for {tab_case}: {title}",
        )
    require(".buttonStyle(.bordered)" not in root_view, failures, "Kraken screens should not reintroduce default bordered buttons inside the glass UI")
    require(
        'DisclosureGroup("Текущий JSON evidence")' in root_view,
        failures,
        "Settings evidence JSON must stay collapsed behind a disclosure by default",
    )
    for expected in [
        "welcomeContentWidth(for screenWidth: CGFloat) -> CGFloat",
        "min(max(screenWidth - 68, 286), 520)",
        "screenContentWidth(for screenWidth: CGFloat) -> CGFloat",
        "min(max(screenWidth - 20, 300), 680)",
    ]:
        require(expected in root_view, failures, f"Kraken root UI missing adaptive width guard: {expected}")
    for expected in [
        'Image("StartBackground")',
        'Image("LaunchMark")',
        'Text("K R A K E N")',
        f'Text("{BRAND_SLOGAN}")',
        'KrakenScreen(title: "Чаты")',
        "private struct ChatListScreen: View",
        "private struct ChatListRow: View",
        'KrakenPanel(title: "Диалоги"',
        '.navigationBarTitleDisplayMode(.inline)',
    ]:
        require(expected in root_view, failures, f"Kraken root UI missing required welcome/native UI marker: {expected}")
    require("HomeMetricRow(title:" not in root_view, failures, "iOS Чаты tab must stay chat-list-first, not the old dashboard metric screen")
    require(STALE_BRAND_SLOGAN not in root_view, failures, "Kraken iOS welcome must not use stale РЯДОМ copy")
    for required_asset in [
        "StartBackground.imageset/start-background.png",
        "LaunchMark.imageset/launch-mark.png",
    ]:
        asset_path = repo_root / "app-ios/KrakenIOS/Assets.xcassets" / required_asset
        require(asset_path.exists(), failures, f"missing welcome asset: {asset_path.relative_to(repo_root)}")
        if asset_path.exists():
            try:
                width, height = read_png_size(asset_path)
            except ValueError as error:
                failures.append(f"invalid welcome asset PNG {asset_path.relative_to(repo_root)}: {error}")
            else:
                require(width >= 100 and height >= 100, failures, f"welcome asset is unexpectedly small: {asset_path.relative_to(repo_root)} {width}x{height}")
    for environment_key in ["KRAKEN_TAB", "KRAKEN_QR", "KRAKEN_SCROLL", "KRAKEN_SKIP_WELCOME"]:
        require(environment_key in root_view, failures, f"launch configuration must support {environment_key} environment fallback for stable simctl smoke captures")

    capture_script = (repo_root / "app-ios/Scripts/capture_ios_smoke.py").read_text(encoding="utf-8")
    for name in EXPECTED_SCREENSHOTS:
        require(name in capture_script, failures, f"{name} is not captured by app-ios/Scripts/capture_ios_smoke.py")
    for environment_key in ["SIMCTL_CHILD_KRAKEN_TAB", "SIMCTL_CHILD_KRAKEN_QR", "SIMCTL_CHILD_KRAKEN_SCROLL", "SIMCTL_CHILD_KRAKEN_SKIP_WELCOME"]:
        require(environment_key in capture_script, failures, f"capture_ios_smoke.py must use {environment_key} for stable Simulator launches")
    require(
        "composite_launch_reference(repo_root)" in capture_script,
        failures,
        "capture_ios_smoke.py must regenerate the launch reference artifact",
    )
    device_harness = (repo_root / "app-ios/Scripts/prepare_ios_device_validation.py").read_text(encoding="utf-8")
    device_runbook = (repo_root / "docs/kraken-ios-device-validation-runbook.md").read_text(encoding="utf-8")
    readiness_audit = (repo_root / "app-ios/Scripts/audit_ios_port_readiness.py").read_text(encoding="utf-8")
    readiness_matrix = (repo_root / "docs/kraken-ios-readiness-matrix.md").read_text(encoding="utf-8")
    for marker in [
        "ios-multipeer-two-device",
        "android-ios-qr-handshake",
        "android-ios-packet-negative-policy",
        "ios-persistence-lifecycle",
        "ios-physical-visual-review",
    ]:
        require(marker in device_harness, failures, f"device validation harness missing gate: {marker}")
        require(marker in device_runbook or marker.replace("-", " ") in device_runbook.lower(), failures, f"device validation runbook missing gate: {marker}")
    require(
        "Do not mark the iOS port complete from this template alone." in device_harness,
        failures,
        "device validation harness must explicitly avoid completion overclaim",
    )
    require(
        "must not be described as Android Wi-Fi Direct parity" in device_runbook,
        failures,
        "device validation runbook must preserve iOS transport boundary",
    )
    for marker in [
        "ui-smoke-evidence",
        "welcome-and-copy",
        "launch-screen-and-icons",
        "native-ios-ui-and-layout",
        "qr-confirmation-binding",
        "packet-policy-before-timeline",
        "physical-device-gates",
    ]:
        require(marker in readiness_audit, failures, f"readiness audit missing item: {marker}")
        require(marker in readiness_matrix, failures, f"readiness matrix missing item: {marker}")
    require(
        "--check-matrix-doc" in readiness_audit,
        failures,
        "readiness audit must be able to check the Markdown readiness matrix against live statuses",
    )
    for marker in [
        "REQUIRED_DEVICE_GATE_IDS",
        "validate_device_gates",
        "valid_iso_timestamp",
        "valid_artifact_path",
    ]:
        require(marker in readiness_audit, failures, f"readiness audit missing strict physical evidence marker: {marker}")
    for marker in [
        "artifactPaths",
        "verifiedAtUtc",
        "verdictNotes",
        '"passed"',
    ]:
        require(marker in readiness_audit, failures, f"readiness audit missing strict physical evidence marker: {marker}")
        require(marker in device_harness or marker in device_runbook, failures, f"device validation docs/harness missing strict physical evidence marker: {marker}")
    require(
        "pending_external_devices" in readiness_matrix,
        failures,
        "readiness matrix must keep physical gates pending until real device evidence exists",
    )

    if failures:
        for failure in failures:
            print(f"FAIL: {failure}")
        return 1

    print(
        f"OK: {len(EXPECTED_SCREENSHOTS)} current iOS smoke screenshots, "
        "launch reference, LaunchGlyph assets, AppIcon slots, tab-chrome pixels and UI guards verified"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
