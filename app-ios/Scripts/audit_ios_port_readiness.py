#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path


CURRENT_SCREENSHOT_COUNT = 17
BRAND_SLOGAN = "ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО"
STALE_BRAND_SLOGAN = "ПРИВАТНО  •  РЯДОМ  •  СВОБОДНО"
REQUIRED_DEVICE_GATE_IDS = [
    "ios-multipeer-two-device",
    "android-ios-qr-handshake",
    "android-ios-packet-negative-policy",
    "ios-persistence-lifecycle",
    "ios-physical-visual-review",
]


@dataclass(frozen=True)
class ReadinessItem:
    id: str
    status: str
    evidence: list[str]
    note: str


def contains(path: Path, text: str) -> bool:
    return path.exists() and text in path.read_text(encoding="utf-8")


def latest_device_evidence(repo_root: Path) -> tuple[str, str | None]:
    evidence_root = repo_root / "artifacts/ios-device-validation"
    candidates = sorted(evidence_root.glob("*/evidence.json"))
    if not candidates:
        return ("pending_external_devices", None)
    latest = candidates[-1]
    try:
        payload = json.loads(latest.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return ("invalid_evidence_json", str(latest.relative_to(repo_root)))
    status = str(payload.get("status", "unknown"))
    if status.startswith("blocked_missing_") or status == "template_not_run":
        return ("pending_external_devices", str(latest.relative_to(repo_root)))
    gate_status = validate_device_gates(repo_root, payload)
    return (gate_status, str(latest.relative_to(repo_root)))


def valid_iso_timestamp(value: str) -> bool:
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return False
    return True


def valid_artifact_path(repo_root: Path, value: str) -> bool:
    path = Path(value)
    if path.is_absolute() or ".." in path.parts:
        return False
    return (repo_root / path).exists()


def validate_device_gates(repo_root: Path, payload: dict[str, object]) -> str:
    gates = payload.get("gates")
    if not isinstance(gates, list):
        return "invalid_device_evidence"

    by_id: dict[str, dict[str, object]] = {}
    for gate in gates:
        if not isinstance(gate, dict):
            return "invalid_device_evidence"
        gate_id = gate.get("id")
        if isinstance(gate_id, str):
            by_id[gate_id] = gate

    for gate_id in REQUIRED_DEVICE_GATE_IDS:
        gate = by_id.get(gate_id)
        if gate is None:
            return "incomplete_device_evidence"
        artifact_paths = gate.get("artifactPaths")
        verified_at = gate.get("verifiedAtUtc")
        verdict_notes = gate.get("verdictNotes")
        if gate.get("status") != "passed":
            return "pending_external_devices"
        if not isinstance(verified_at, str) or not verified_at.strip() or not valid_iso_timestamp(verified_at):
            return "incomplete_device_evidence"
        if (
            not isinstance(artifact_paths, list)
            or not artifact_paths
            or not all(isinstance(path, str) and path.strip() and valid_artifact_path(repo_root, path) for path in artifact_paths)
        ):
            return "incomplete_device_evidence"
        if (
            not isinstance(verdict_notes, list)
            or not verdict_notes
            or not all(isinstance(note, str) and note.strip() for note in verdict_notes)
        ):
            return "incomplete_device_evidence"

    return "passed"


def audit(repo_root: Path) -> list[ReadinessItem]:
    root_view = repo_root / "app-ios/KrakenIOS/Views/KrakenIOSRootView.swift"
    android_welcome = repo_root / "app-android/app/src/main/java/com/disser/kraken/ui/screens/WelcomeScreen.kt"
    root_readme = repo_root / "README.md"
    verifier = repo_root / "app-ios/Scripts/verify_ios_smoke.py"
    manifest_path = repo_root / "artifacts/ios-smoke/current-manifest.json"
    info_plist = repo_root / "app-ios/KrakenIOS/Info.plist"
    models = repo_root / "app-ios/KrakenIOS/Core/KrakenModels.swift"
    simulator = repo_root / "app-ios/KrakenIOS/Core/KrakenIOSSimulator.swift"
    store = repo_root / "app-ios/KrakenIOS/Core/KrakenIOSStore.swift"
    transport = repo_root / "app-ios/KrakenIOS/Transport/IOSNearbyTransport.swift"
    tests = repo_root / "app-ios/KrakenIOSTests/KrakenIOSStoreTests.swift"

    items: list[ReadinessItem] = []

    manifest_ok = False
    if manifest_path.exists():
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            notes = manifest.get("notes", [])
            manifest_ok = (
                len(manifest.get("currentScreenshots", [])) == CURRENT_SCREENSHOT_COUNT
                and isinstance(notes, list)
                and any("pixel-checked" in str(note) and "LaunchGlyph" in str(note) for note in notes)
            )
        except json.JSONDecodeError:
            manifest_ok = False
    verifier_ok = all(contains(verifier, marker) for marker in [
        "has_bottom_tab_chrome",
        "has_tablet_top_tab_chrome",
        "tab-chrome pixels",
    ])
    items.append(ReadinessItem(
        id="ui-smoke-evidence",
        status="proven" if manifest_ok and verifier_ok else "missing_or_stale",
        evidence=[str(manifest_path.relative_to(repo_root)), str(verifier.relative_to(repo_root))],
        note="Current Simulator evidence must list 17 screenshots across native iPhone, compact iPhone and iPad, with pixel guards against early LaunchGlyph frames.",
    ))

    welcome_ok = all(contains(root_view, marker) for marker in [
        'Image("StartBackground")',
        'Image("LaunchMark")',
        'Text("K R A K E N")',
        f'Text("{BRAND_SLOGAN}")',
        'KrakenScreen(title: "Чаты")',
        "private struct ChatListScreen: View",
        "private struct ChatListRow: View",
        'KrakenPanel(title: "Диалоги"',
    ]) and all(contains(path, BRAND_SLOGAN) and not contains(path, STALE_BRAND_SLOGAN) for path in [
        root_view,
        android_welcome,
        root_readme,
    ]) and not contains(root_view, "HomeMetricRow(title:")
    items.append(ReadinessItem(
        id="welcome-and-copy",
        status="proven" if welcome_ok else "missing_or_stale",
        evidence=[
            str(root_view.relative_to(repo_root)),
            str(android_welcome.relative_to(repo_root)),
            str(root_readme.relative_to(repo_root)),
        ],
        note="Welcome screen and copy must match the Android-backed Kraken brand source of truth.",
    ))

    launch_ok = all(contains(info_plist, marker) for marker in ["UILaunchScreen", "LaunchGlyph", "BrandBackground"])
    launch_reference = repo_root / "artifacts/ios-smoke/kraken-ios-launch-reference.png"
    items.append(ReadinessItem(
        id="launch-screen-and-icons",
        status="proven" if launch_ok and launch_reference.exists() else "missing_or_stale",
        evidence=[
            str(info_plist.relative_to(repo_root)),
            "app-ios/KrakenIOS/Assets.xcassets/AppIcon.appiconset/Contents.json",
            str(launch_reference.relative_to(repo_root)),
        ],
        note="Launch proof is structural plus generated reference, not physical launch timing evidence.",
    ))

    native_ui_ok = all(contains(root_view, marker) for marker in [
        "TabView(selection:",
        "GlassEffectContainer",
        "tabBarMinimizeBehavior(.onScrollDown)",
        ".navigationBarTitleDisplayMode(.inline)",
        "min(max(screenWidth - 68, 286), 520)",
        "min(max(screenWidth - 20, 300), 680)",
        'case .home: "Чаты"',
        ".tag(KrakenTab.home)",
        "private struct ChatListScreen: View",
        "private struct ChatListRow: View",
        'KrakenPanel(title: "Диалоги"',
    ])
    items.append(ReadinessItem(
        id="native-ios-ui-and-layout",
        status="proven" if native_ui_ok else "missing_or_stale",
        evidence=[str(root_view.relative_to(repo_root))],
        note="Native tabs, Liquid Glass grouping, inline titles and wide-screen constraints are guarded.",
    ))

    qr_binding_ok = all(contains(path, marker) for path, marker in [
        (models, "pendingInviteId"),
        (models, "pendingResponseId"),
        (models, "pendingResponderFingerprint"),
        (simulator, "$0.pendingInviteId == confirmation.inviteId"),
        (simulator, "$0.pendingResponseId == confirmation.responseId"),
        (simulator, "$0.pendingResponderFingerprint == confirmation.responderFingerprint"),
        (tests, "testConfirmationRequiresPendingInviteResponseAndResponderBinding"),
    ])
    items.append(ReadinessItem(
        id="qr-confirmation-binding",
        status="proven" if qr_binding_ok else "missing_or_stale",
        evidence=[
            str(models.relative_to(repo_root)),
            str(simulator.relative_to(repo_root)),
            str(tests.relative_to(repo_root)),
        ],
        note="Confirmation activation must bind invite id, generated response id and local responder fingerprint.",
    ))

    packet_policy_ok = all(contains(store, marker) for marker in [
        "packetPolicyValidator.acceptInbound(packet",
        "resolveRelationship(for: packet)",
        "unresolvedTransportRelationship",
    ]) and all(contains(transport, marker) for marker in [
        "public func send(_ data: Data, toPeerNamed peerDisplayName: String)",
        'info?["transport"] == descriptor.transportId',
    ]) and contains(root_view, "toPeerNamed: relationship.peerDisplayName")
    items.append(ReadinessItem(
        id="packet-policy-before-timeline",
        status="proven" if packet_policy_ok else "missing_or_stale",
        evidence=[str(store.relative_to(repo_root)), str(transport.relative_to(repo_root)), str(tests.relative_to(repo_root))],
        note="Live packet receive must validate policy and relationship binding before timeline mutation; outbound sends must target the selected connected peer.",
    ))

    device_status, device_evidence = latest_device_evidence(repo_root)
    items.append(ReadinessItem(
        id="physical-device-gates",
        status=device_status,
        evidence=[
            "docs/kraken-ios-device-validation-runbook.md",
            "app-ios/Scripts/prepare_ios_device_validation.py",
            *( [device_evidence] if device_evidence else [] ),
        ],
        note="Requires real iPhone/iPad MultipeerConnectivity and Android/iOS QR/packet interop evidence.",
    ))

    return items


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit Kraken iOS port readiness without overclaiming physical gates.")
    parser.add_argument("--json", action="store_true", help="Emit JSON instead of Markdown.")
    parser.add_argument("--fail-on-missing-proven", action="store_true", help="Exit 1 if a non-physical proven item is missing.")
    parser.add_argument(
        "--check-matrix-doc",
        default=None,
        help="Check that a Markdown readiness matrix contains the current item statuses.",
    )
    return parser.parse_args()


def check_matrix_doc(repo_root: Path, items: list[ReadinessItem], doc_path: str) -> list[str]:
    path = repo_root / doc_path
    if not path.exists():
        return [f"missing readiness matrix document: {doc_path}"]

    text = path.read_text(encoding="utf-8")
    failures: list[str] = []
    for item in items:
        expected_row_start = f"| `{item.id}` | `{item.status}` |"
        if expected_row_start not in text:
            failures.append(f"{doc_path} missing current status row for {item.id}: {item.status}")

    if any(item.id == "physical-device-gates" and item.status != "proven" for item in items):
        if "It must not be used to claim real-device completion." not in text:
            failures.append(f"{doc_path} must explicitly prohibit real-device completion claims while physical gates are open")
        if "physical iPhone/iPad MultipeerConnectivity" not in text:
            failures.append(f"{doc_path} must name the open physical iPhone/iPad MultipeerConnectivity gate")
        if "Android/iOS physical QR invite -> response -> confirmation interop" not in text:
            failures.append(f"{doc_path} must name the open Android/iOS physical QR interop gate")

    return failures


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    items = audit(repo_root)
    if args.json:
        print(json.dumps([asdict(item) for item in items], ensure_ascii=False, indent=2))
    else:
        print("| Requirement | Status | Evidence | Note |")
        print("|---|---|---|---|")
        for item in items:
            evidence = "<br>".join(item.evidence)
            print(f"| `{item.id}` | `{item.status}` | {evidence} | {item.note} |")
    if args.check_matrix_doc:
        matrix_failures = check_matrix_doc(repo_root, items, args.check_matrix_doc)
        if matrix_failures:
            for failure in matrix_failures:
                print(f"FAIL: {failure}")
            return 1
    if args.fail_on_missing_proven and any(item.id != "physical-device-gates" and item.status != "proven" for item in items):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
