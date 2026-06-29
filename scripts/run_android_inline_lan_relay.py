#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import socket
import sys
import time
from dataclasses import asdict
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "src"))

from disser_messenger.mesh.desktop_lan_relay import DesktopLanRelay, LanPeer, RelayAttackMode
from disser_messenger.mesh.lan_frame_codec import (
    LanFrameEnvelope,
    decode_frame,
    encode_envelope_frame,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a research-only Android LAN frame compatible Mac inline relay.",
    )
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--listen-port", type=int, required=True)
    parser.add_argument("--target-host", required=True, help="Android B LAN endpoint host.")
    parser.add_argument("--target-port", type=int, required=True, help="Android B LAN endpoint port.")
    parser.add_argument("--target-fingerprint", default="", help="Expected Android B fingerprint.")
    parser.add_argument("--mode", choices=[mode.value for mode in RelayAttackMode], default="normal")
    parser.add_argument("--max-frames", type=int, default=1)
    parser.add_argument("--timeout-seconds", type=float, default=90.0)
    parser.add_argument("--relay-fingerprint", default="MAC-INLINE-RELAY-FP")
    parser.add_argument("--report-dir", default="artifacts/desktop-relay-inline")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    started_at = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    frames: list[dict[str, Any]] = []
    send_context: dict[str, Any] = {}
    relay = DesktopLanRelay(
        fingerprint=args.relay_fingerprint,
        attack_mode=RelayAttackMode(args.mode),
        now_epoch_millis=lambda: int(time.time() * 1000),
        send_fn=lambda host, port, packet: _forward_current_packet(host, port, packet, send_context),
    )

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((args.listen_host, args.listen_port))
        server.listen(8)
        server.settimeout(args.timeout_seconds)
        while len(frames) < args.max_frames:
            try:
                client, source = server.accept()
            except socket.timeout:
                break
            with client:
                client.settimeout(args.timeout_seconds)
                envelope = decode_frame(client.makefile("rb"))
            target_fingerprint = args.target_fingerprint or str(
                envelope.packet.get("recipient_fingerprint", ""),
            )
            relay.remember_peer(LanPeer(target_fingerprint, args.target_host, args.target_port))
            frame_record: dict[str, Any] = {
                "source": f"{source[0]}:{source[1]}",
                "sender_fingerprint": envelope.sender_fingerprint,
                "recipient_fingerprint": envelope.packet.get("recipient_fingerprint"),
                "packet_id": envelope.packet.get("packet_id"),
                "mode": args.mode,
                "envelope": envelope,
                "forward_attempts": [],
            }
            send_context["envelope"] = envelope
            send_context["frame_record"] = frame_record
            frames.append(frame_record)
            decision = relay.handle_packet(envelope.packet, source_host=source[0])
            frame_record["decision"] = asdict(decision)

    report_dir = REPO_ROOT / args.report_dir / time.strftime("%Y%m%d-%H%M%S")
    report_dir.mkdir(parents=True, exist_ok=True)
    report = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "started_at": started_at,
        "claim_boundary": (
            "research_only_android_lan_frame_inline_relay_harness; "
            "requires Android before/after evidence before any MITM claim"
        ),
        "listen": {"host": args.listen_host, "port": args.listen_port},
        "target": {
            "host": args.target_host,
            "port": args.target_port,
            "fingerprint": args.target_fingerprint or None,
        },
        "mode": args.mode,
        "frames_received": len(frames),
        "frames": [_json_frame(frame) for frame in frames],
        "stats": asdict(relay.stats),
        "not_closed": [
            "android_a_to_mac_to_android_b_physical_run",
            "android_before_after_counters_for_normal_drop_duplicate_tamper",
            "cryptographic_mitm_resistance",
            "production_relay_security",
        ],
    }
    json_path = report_dir / "android_inline_lan_relay.json"
    md_path = report_dir / "android_inline_lan_relay.md"
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    md_path.write_text(_markdown(report), encoding="utf-8")
    print(report_dir)
    return 0


def _forward_current_packet(
    host: str,
    port: int,
    packet: dict[str, Any],
    send_context: dict[str, Any],
) -> bool:
    envelope = send_context["envelope"]
    frame_record = send_context["frame_record"]
    try:
        _forward_packet(host, port, packet, envelope)
        frame_record["forward_attempts"].append(
            {"host": host, "port": port, "packet_id": packet.get("packet_id"), "success": True},
        )
        return True
    except OSError as error:
        frame_record["forward_attempts"].append(
            {
                "host": host,
                "port": port,
                "packet_id": packet.get("packet_id"),
                "success": False,
                "error": str(error),
            },
        )
        return False


def _forward_packet(
    host: str,
    port: int,
    packet: dict[str, Any],
    original_envelope: LanFrameEnvelope,
) -> None:
    forwarded = LanFrameEnvelope(
        sender_peer_id=original_envelope.sender_peer_id,
        sender_fingerprint=original_envelope.sender_fingerprint,
        sender_display_name=original_envelope.sender_display_name,
        sender_reply_port=original_envelope.sender_reply_port,
        packet=packet,
    )
    with socket.create_connection((host, port), timeout=5.0) as sock:
        sock.sendall(encode_envelope_frame(forwarded))


def _json_frame(frame: dict[str, Any]) -> dict[str, Any]:
    return {
        "source": frame["source"],
        "sender_fingerprint": frame["sender_fingerprint"],
        "recipient_fingerprint": frame["recipient_fingerprint"],
        "packet_id": frame["packet_id"],
        "mode": frame["mode"],
        "forward_attempts": frame["forward_attempts"],
        "decision": frame.get("decision"),
    }


def _markdown(report: dict[str, Any]) -> str:
    return "\n".join(
        [
            "# Android Inline LAN Relay Harness",
            "",
            f"Generated: `{report['generated_at']}`.",
            "",
            "## Scope",
            "",
            report["claim_boundary"],
            "",
            "## Run",
            "",
            f"- Listen: `{report['listen']['host']}:{report['listen']['port']}`.",
            f"- Target: `{report['target']['host']}:{report['target']['port']}`.",
            f"- Mode: `{report['mode']}`.",
            f"- Frames received: `{report['frames_received']}`.",
            "",
            "## Not Closed",
            "",
            *[f"- `{item}`" for item in report["not_closed"]],
            "",
        ],
    )


if __name__ == "__main__":
    raise SystemExit(main())
