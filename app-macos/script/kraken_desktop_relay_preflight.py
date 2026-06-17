#!/usr/bin/env python3
from __future__ import annotations

import json
import sys
import time
from dataclasses import asdict
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "src"))

from disser_messenger.mesh.desktop_lan_relay import DesktopLanRelay, LanPeer, RelayAttackMode


def packet(packet_id: str = "packet-1") -> dict[str, Any]:
    return {
        "packet_id": packet_id,
        "protocol_version": 1,
        "packet_type": "MESSAGE",
        "sender_fingerprint": "ALICE-FP",
        "recipient_fingerprint": "BOB-FP",
        "relationship_id": "relationship-1",
        "conversation_id": "conversation-1",
        "message_id": "message-1",
        "created_at_epoch_millis": 1_700_000_000_000,
        "expires_at_epoch_millis": 2_000_000_000_000,
        "ttl_hops": 4,
        "payload_type": "LOCAL_MESSAGE_JSON",
        "payload_json": "{\"message_id\":\"message-1\",\"body\":\"hello\"}",
        "crypto_profile_id": "kraken-research-mesh-lc32-prime-offsets",
        "proof_mode": "verified-hmac-sha256-prototype",
        "auth_algorithm": "hmac-sha256-prototype-auth",
        "auth_tag": "prototype-tag",
    }


def mode_result(mode: RelayAttackMode) -> dict[str, Any]:
    sent: list[dict[str, Any]] = []
    relay = DesktopLanRelay(
        fingerprint="DESKTOP-RELAY-FP",
        attack_mode=mode,
        now_epoch_millis=lambda: 1_700_000_000_100,
        send_fn=lambda host, port, pkt: sent.append(pkt) is None or True,
    )
    relay.remember_peer(LanPeer("BOB-FP", "192.168.1.y", 43191))
    decision = relay.handle_packet(packet(f"packet-{mode.value}"), source_host="192.168.1.x")
    return {
        "mode": mode.value,
        "accepted": decision.accepted,
        "reason": decision.reason,
        "forwarded": decision.forwarded,
        "queued": decision.queued,
        "dropped": decision.dropped,
        "sent_count": len(sent),
        "ttl_after_relay": sent[0]["ttl_hops"] if sent else None,
        "payload_json": sent[0]["payload_json"] if sent else None,
        "stats": asdict(relay.stats),
    }


def main() -> int:
    stamp = time.strftime("%Y%m%d-%H%M%S")
    out_dir = REPO_ROOT / "artifacts/desktop-relay-preflight" / stamp
    out_dir.mkdir(parents=True, exist_ok=True)
    report = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "claim_boundary": "desktop_lan_relay_research_tool_not_production_relay",
        "modes": [mode_result(mode) for mode in RelayAttackMode],
    }
    (out_dir / "desktop_relay_preflight.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    lines = [
        "# Kraken desktop relay preflight",
        "",
        f"Generated: `{report['generated_at']}`",
        "",
        "## Attack modes",
        "",
    ]
    for item in report["modes"]:
        lines.append(
            f"- `{item['mode']}`: accepted=`{item['accepted']}`, reason=`{item['reason']}`, "
            f"forwarded=`{item['forwarded']}`, queued=`{item['queued']}`, dropped=`{item['dropped']}`"
        )
    lines.extend(
        [
            "",
            "## Boundary",
            "",
            "This artifact proves local relay decision logic only. It does not prove Android radio delivery or production security.",
        ]
    )
    (out_dir / "desktop_relay_preflight.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
