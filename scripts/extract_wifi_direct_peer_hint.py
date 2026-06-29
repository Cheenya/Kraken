#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def load_target_port(manifest_path: Path, target_device: str) -> int | None:
    if not manifest_path.exists():
        return None
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for device in manifest.get("devices", []):
        if device.get("serial") != target_device:
            continue
        value = device.get("local_port")
        try:
            port = int(value)
        except (TypeError, ValueError):
            return None
        return port if 1 <= port <= 65535 else None
    return None


def parse_group_owner(dumpsys_text: str) -> dict[str, str | None]:
    lines = dumpsys_text.splitlines()
    group_index = next((index for index, line in enumerate(lines) if "mGroup network:" in line), None)
    if group_index is None:
        group_index = next((index for index, line in enumerate(lines) if line.strip().startswith("network:")), None)
    if group_index is None:
        return {"device_address": None, "device_name": None}

    block = lines[group_index : group_index + 80]
    go_index = next((index for index, line in enumerate(block) if "GO: Device:" in line), None)
    if go_index is None:
        return {"device_address": None, "device_name": None}

    go_block = block[go_index : go_index + 18]
    joined = "\n".join(go_block)
    address_match = re.search(r"deviceAddress:\s*(([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})", joined)
    name: str | None = None
    name_match = re.search(r"GO: Device:\s*([^,\n]+)", go_block[0])
    if name_match:
        name = name_match.group(1).strip() or None
    return {
        "device_address": address_match.group(1).lower() if address_match else None,
        "device_name": name,
    }


def build_hint(capture_dir: Path, target_device: str) -> dict[str, Any]:
    device_dir = capture_dir / target_device
    manifest_path = capture_dir / "manifest.json"
    dumpsys_path = device_dir / "network_state_after_broadcast" / "dumpsys_wifip2p.txt"
    owner = (
        parse_group_owner(dumpsys_path.read_text(encoding="utf-8", errors="replace"))
        if dumpsys_path.exists()
        else {"device_address": None, "device_name": None}
    )
    port = load_target_port(manifest_path, target_device)
    status = "ready" if owner["device_address"] and port else "missing-target-go-address-or-port"
    return {**owner, "port": port, "status": status}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture-dir", required=True, type=Path)
    parser.add_argument("--target-device", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    print(json.dumps(build_hint(args.capture_dir, args.target_device), ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
