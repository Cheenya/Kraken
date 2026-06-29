#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import socket
import struct
import time
from pathlib import Path
from typing import Iterable

MAX_FRAME_BYTES = 256 * 1024


def malformed_cases() -> dict[str, bytes]:
    invalid_json = b"not-json"
    truncated = b'{"frame_version":1'
    return {
        "zero_length_prefix": struct.pack(">I", 0),
        "oversized_length_prefix": struct.pack(">I", MAX_FRAME_BYTES + 1),
        "invalid_json_payload": struct.pack(">I", len(invalid_json)) + invalid_json,
        "truncated_payload": struct.pack(">I", len(truncated) + 16) + truncated,
    }


def load_targets(capture_dir: Path, only_serials: set[str], address_prefixes: tuple[str, ...]) -> list[dict[str, object]]:
    targets: list[dict[str, object]] = []
    for route_json in sorted(capture_dir.glob("*/route_specific_evidence_latest.json")):
        serial = route_json.parent.name
        if only_serials and serial not in only_serials:
            continue
        payload = json.loads(route_json.read_text(encoding="utf-8"))
        transport = payload.get("transport", {})
        port = transport.get("local_port")
        addresses = transport.get("local_addresses") or []
        if not isinstance(port, int):
            continue
        for address in addresses:
            if isinstance(address, str) and address.count(".") == 3:
                if address_prefixes and not any(address.startswith(prefix) for prefix in address_prefixes):
                    continue
                targets.append(
                    {
                        "serial": serial,
                        "device_model": payload.get("device_model"),
                        "git_sha": payload.get("git_sha"),
                        "source_state": payload.get("source_state"),
                        "address": address,
                        "port": port,
                    },
                )
    return targets


def inject(target: dict[str, object], case_name: str, payload: bytes, timeout: float) -> dict[str, object]:
    started = time.time()
    address = str(target["address"])
    port = int(target["port"])
    result: dict[str, object] = {
        **target,
        "case": case_name,
        "bytes_sent": len(payload),
        "success": False,
        "error": None,
        "duration_ms": None,
    }
    try:
        with socket.create_connection((address, port), timeout=timeout) as sock:
            sock.settimeout(timeout)
            sock.sendall(payload)
        result["success"] = True
    except OSError as error:
        result["error"] = f"{error.__class__.__name__}: {error}"
    finally:
        result["duration_ms"] = round((time.time() - started) * 1000, 3)
    return result


def summarize(results: Iterable[dict[str, object]]) -> dict[str, object]:
    rows = list(results)
    return {
        "attempts": len(rows),
        "successful_connections": sum(1 for row in rows if row.get("success") is True),
        "failed_connections": sum(1 for row in rows if row.get("success") is not True),
        "serials": sorted({str(row.get("serial")) for row in rows}),
        "cases": sorted({str(row.get("case")) for row in rows}),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Send malformed LAN frame payloads to Kraken Android LAN listeners from the host machine.",
    )
    parser.add_argument("--capture-dir", required=True, type=Path, help="Debug route evidence capture directory.")
    parser.add_argument("--serial", action="append", default=[], help="Limit injection to an ADB serial.")
    parser.add_argument(
        "--address-prefix",
        action="append",
        default=[],
        help="Limit target addresses to a prefix such as 192.168.0. May be passed more than once.",
    )
    parser.add_argument("--timeout", type=float, default=1.5, help="TCP connect/send timeout in seconds.")
    parser.add_argument("--out", type=Path, help="Output JSON path. Default: <capture-dir>/lan_malformed_injection.json")
    args = parser.parse_args()

    capture_dir = args.capture_dir.resolve()
    output_path = args.out.resolve() if args.out else capture_dir / "lan_malformed_injection.json"
    targets = load_targets(capture_dir, set(args.serial), tuple(args.address_prefix))
    cases = malformed_cases()
    results = [
        inject(target, case_name, payload, args.timeout)
        for target in targets
        for case_name, payload in cases.items()
    ]
    report = {
        "report": "lan_malformed_frame_injection",
        "generated_at_epoch_millis": int(time.time() * 1000),
        "capture_dir": str(capture_dir),
        "address_prefixes": args.address_prefix,
        "claim_boundary": (
            "External host TCP injection into Android LAN listener. Successful socket writes prove transport-level "
            "malformed frame delivery attempts only; they do not prove BLE/Wi-Fi Direct injection or production security."
        ),
        "summary": summarize(results),
        "results": results,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(output_path)
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    return 0 if report["summary"]["successful_connections"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
