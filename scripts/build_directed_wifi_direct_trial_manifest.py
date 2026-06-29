#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_capture_device(capture_dir: Path, serial: str) -> dict[str, Any]:
    manifest_path = capture_dir / "manifest.json"
    if not manifest_path.exists():
        return {}
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for device in manifest.get("devices", []):
        if device.get("serial") == serial:
            return device
    return manifest.get("devices", [{}])[0] if manifest.get("devices") else {}


def int_value(payload: dict[str, Any], key: str) -> int:
    value = payload.get(key)
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def string_list(values: Any) -> list[str]:
    if not isinstance(values, list):
        return []
    return [str(value) for value in values if value]


def target_observed_ids(device: dict[str, Any]) -> dict[str, list[str]]:
    delivery = device.get("target_delivery") or {}
    recent_messages = delivery.get("recent_messages") or []
    received_packets = delivery.get("received_packets") or []
    message_ids = set(string_list(device.get("target_recent_message_ids")))
    packet_ids = set(string_list(device.get("target_received_packet_ids")))
    packet_message_ids = set(string_list(device.get("target_received_packet_message_ids")))

    for message in recent_messages:
        if isinstance(message, dict) and message.get("message_id"):
            message_ids.add(str(message["message_id"]))
    for packet in received_packets:
        if not isinstance(packet, dict):
            continue
        if packet.get("packet_id"):
            packet_ids.add(str(packet["packet_id"]))
        if packet.get("message_id"):
            packet_message_ids.add(str(packet["message_id"]))

    return {
        "message_ids": sorted(message_ids),
        "packet_ids": sorted(packet_ids),
        "packet_message_ids": sorted(packet_message_ids),
    }


def bool_arg(value: str) -> bool:
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "y"}:
        return True
    if normalized in {"0", "false", "no", "n"}:
        return False
    raise argparse.ArgumentTypeError(f"expected boolean string, got: {value}")


def relative_capture_path(path: Path, out_dir: Path) -> str:
    try:
        return str(path.relative_to(out_dir))
    except ValueError:
        return str(path)


def wifi_direct_peer_seen(sender_command: dict[str, Any]) -> bool:
    return (
        int_value(sender_command, "p2p_txt_bound_peer_count") > 0
        or bool(sender_command.get("wifi_direct_discovered_peers"))
        or any(
            isinstance(record, dict) and record.get("accepted") is True
            for record in sender_command.get("wifi_direct_txt_records") or []
        )
    )


def classify_wifi_direct_failure_stage(
    *,
    permissions_ready: bool,
    relationship_ready: bool,
    wifi_direct_discovery_ready: bool,
    endpoint_bound: bool,
    sender_success: bool,
    send_attempted: bool,
    kraken_dns_sd_peer_seen: bool,
    sender_transport_errors: list[str],
    binding_reason: str | None,
    last_connect_result: str | None,
) -> str | None:
    if not permissions_ready:
        return "wifi_direct_permissions_not_ready"
    if not relationship_ready:
        return "relationship_not_ready"
    if not wifi_direct_discovery_ready:
        return "wifi_direct_discovery_not_ready"
    if sender_success:
        return None
    reason = binding_reason or ""
    connect_result = last_connect_result or ""
    if not endpoint_bound and kraken_dns_sd_peer_seen:
        if reason.startswith("p2p-group-not-formed") or connect_result.startswith(("failed:", "timeout:")):
            return "wifi_p2p_group_connect_failed"
        return "wifi_direct_endpoint_unbound_after_kraken_dns_sd_discovery"
    if not endpoint_bound:
        return "wifi_direct_endpoint_not_bound"
    if send_attempted and sender_transport_errors:
        return "wifi_direct_send_transport_failed"
    if send_attempted:
        return "sender_send_failed"
    return "send_not_attempted"


def build_manifest(
    *,
    out_dir: Path,
    generated_at: str,
    git_branch: str,
    git_sha: str,
    git_source_state: str,
    sender_device: str,
    target_device: str,
    target_before_dir: Path,
    sender_dir: Path,
    target_after_dir: Path,
    body: str,
    prearm_target_group_owner: bool,
    hint_target_wifi_direct_peer: bool,
    target_hint_device_address: str,
    target_hint_device_name: str,
    target_hint_port: int | None,
    target_hint_status: str,
) -> dict[str, Any]:
    out_dir.mkdir(parents=True, exist_ok=True)
    target_before = load_capture_device(target_before_dir, target_device)
    sender = load_capture_device(sender_dir, sender_device)
    target_after = load_capture_device(target_after_dir, target_device)
    sender_command = sender.get("command_result") or {}
    sender_results = sender_command.get("debug_send_results") or []
    sender_success = sender_command.get("debug_send_success") is True
    successful_sends = [result for result in sender_results if result.get("success") is True]
    sender_transport_errors = sorted(
        {
            str(result.get("transport_error"))
            for result in sender_results
            if result.get("transport_error")
        }
    )
    sender_rejection_errors = sorted(
        {
            str(result.get("error"))
            for result in sender_results
            if result.get("error")
        }
    )
    sender_message_ids = [
        str(result.get("message_id"))
        for result in sender_results
        if result.get("message_id")
    ]
    sender_packet_ids = [
        str(result.get("packet_id"))
        for result in sender_results
        if result.get("packet_id")
    ]

    accepted_delta = int_value(target_after, "accepted_connections") - int_value(target_before, "accepted_connections")
    inbound_delta = int_value(target_after, "inbound_packets") - int_value(target_before, "inbound_packets")
    malformed_delta = int_value(target_after, "malformed_frames_dropped") - int_value(target_before, "malformed_frames_dropped")
    transport_counter_delivery_observed = sender_success and inbound_delta > 0
    sender_permissions = sender.get("wifi_direct_permissions") or {}
    target_before_permissions = target_before.get("wifi_direct_permissions") or {}
    target_after_permissions = target_after.get("wifi_direct_permissions") or {}
    permissions_ready = (
        sender.get("wifi_direct_permission_warning") is None
        and target_before.get("wifi_direct_permission_warning") is None
        and target_after.get("wifi_direct_permission_warning") is None
        and sender_permissions.get("fine_location_granted") is True
        and target_before_permissions.get("fine_location_granted") is True
        and target_after_permissions.get("fine_location_granted") is True
    )
    relationship_ready = int_value(sender_command, "sendable_relationship_count") > 0
    wifi_direct_discovery_ready = (
        int_value(sender_command, "p2p_service_found_count") > 0
        or int_value(sender_command, "p2p_txt_record_count") > 0
        or int_value(sender_command, "p2p_txt_bound_peer_count") > 0
        or int_value(sender_command, "p2p_visible_device_count") > 0
        or int_value(sender_command, "p2p_unbound_visible_device_count") > 0
        or bool(sender_command.get("wifi_direct_discovered_peers"))
        or bool(sender_command.get("wifi_direct_visible_devices"))
        or bool(sender_command.get("wifi_direct_txt_records"))
    )
    kraken_dns_sd_peer_seen = wifi_direct_peer_seen(sender_command)
    sender_bound_endpoints = sender_command.get("wifi_direct_bound_endpoints") or []
    sender_last_send_host = sender_command.get("wifi_direct_last_send_host")
    sender_last_send_port = sender_command.get("wifi_direct_last_send_port")
    endpoint_bound = (
        bool(sender_bound_endpoints)
        or (bool(sender_last_send_host) and sender_last_send_port is not None)
    )
    send_attempted = bool(sender_results)
    target_before_observed = target_observed_ids(target_before)
    target_after_observed = target_observed_ids(target_after)
    target_new_observed = {
        key: sorted(set(target_after_observed[key]) - set(target_before_observed[key]))
        for key in ["message_ids", "packet_ids", "packet_message_ids"]
    }
    matching_message_ids = sorted(
        set(sender_message_ids).intersection(target_new_observed["message_ids"])
        | set(sender_message_ids).intersection(target_new_observed["packet_message_ids"])
    )
    matching_packet_ids = sorted(set(sender_packet_ids).intersection(target_new_observed["packet_ids"]))
    message_delivery_proven = sender_success and bool(matching_message_ids) and bool(matching_packet_ids)
    sender_binding_reason = (
        sender_command.get("wifi_direct_endpoint_binding_reason")
        or sender.get("wifi_direct_endpoint_binding_reason")
    )
    sender_last_connect_result = (
        sender_command.get("wifi_direct_last_connect_result")
        or sender.get("wifi_direct_last_connect_result")
    )
    transport_route_unavailable = (
        send_attempted
        and not sender_success
        and bool(sender_transport_errors)
        and not endpoint_bound
    )
    failure_stage = classify_wifi_direct_failure_stage(
        permissions_ready=permissions_ready,
        relationship_ready=relationship_ready,
        wifi_direct_discovery_ready=wifi_direct_discovery_ready,
        endpoint_bound=endpoint_bound,
        sender_success=sender_success,
        send_attempted=send_attempted,
        kraken_dns_sd_peer_seen=kraken_dns_sd_peer_seen,
        sender_transport_errors=sender_transport_errors,
        binding_reason=sender_binding_reason,
        last_connect_result=sender_last_connect_result,
    )
    verdict_status = (
        "target_message_id_delivery_proven"
        if message_delivery_proven
        else "target_transport_counter_delta_observed"
        if transport_counter_delivery_observed
        else "relationship_not_ready"
        if not relationship_ready
        else "wifi_direct_discovery_not_ready"
        if not wifi_direct_discovery_ready
        else "sender_endpoint_not_bound"
        if not endpoint_bound
        else "sender_send_failed"
        if send_attempted and not sender_success
        else "target_transport_counter_delta_not_observed"
    )

    manifest = {
        "report_version": "kraken.directed_wifi_direct_route_trial.v2",
        "generated_at": generated_at,
        "git": {
            "branch": git_branch,
            "sha": git_sha,
            "source_state": git_source_state,
            "status_file": "git_status.txt",
        },
        "sender_device": sender_device,
        "target_device": target_device,
        "debug_send_body": body,
        "prearm_target_group_owner": prearm_target_group_owner,
        "debug_wifi_direct_peer_hint_requested": hint_target_wifi_direct_peer,
        "debug_wifi_direct_peer_hint": {
            "device_address": target_hint_device_address or None,
            "device_name": target_hint_device_name or None,
            "port": target_hint_port,
            "status": target_hint_status,
            "source": "target-before:wifip2p-mGroup-go" if hint_target_wifi_direct_peer else None,
        },
        "captures": {
            "target_before_dir": relative_capture_path(target_before_dir, out_dir),
            "sender_dir": relative_capture_path(sender_dir, out_dir),
            "target_after_dir": relative_capture_path(target_after_dir, out_dir),
        },
        "sender": {
            "debug_send_success": sender_success,
            "successful_send_count": len(successful_sends),
            "debug_send_rejection_errors": sender_rejection_errors,
            "debug_send_transport_errors": sender_transport_errors,
            "message_ids": sender_message_ids,
            "packet_ids": sender_packet_ids,
            "selected_route": sender.get("selected_route"),
            "wifi_direct_permission_warning": sender.get("wifi_direct_permission_warning"),
            "wifi_direct_permissions": sender.get("wifi_direct_permissions"),
            "kraken_dns_sd_peer_seen": kraken_dns_sd_peer_seen,
            "wifi_direct_group_role": sender.get("wifi_direct_group_role"),
            "wifi_direct_group_owner_address": sender.get("wifi_direct_group_owner_address"),
            "wifi_direct_local_p2p_address": sender.get("wifi_direct_local_p2p_address"),
            "wifi_direct_endpoint_binding_state": sender.get("wifi_direct_endpoint_binding_state"),
            "wifi_direct_endpoint_binding_reason": sender.get("wifi_direct_endpoint_binding_reason"),
            "wifi_direct_relationship_peer_fingerprint_prefix": sender.get("wifi_direct_relationship_peer_fingerprint_prefix"),
            "wifi_direct_last_connect_device_address": sender.get("wifi_direct_last_connect_device_address"),
            "wifi_direct_last_connect_device_name": sender.get("wifi_direct_last_connect_device_name"),
            "wifi_direct_last_connect_group_owner_intent": sender.get("wifi_direct_last_connect_group_owner_intent"),
            "wifi_direct_last_connect_result": sender.get("wifi_direct_last_connect_result"),
            "wifi_direct_last_connect_failure_reason": sender.get("wifi_direct_last_connect_failure_reason"),
            "wifi_direct_connect_attempts": sender.get("wifi_direct_connect_attempts", []),
            "wifi_direct_discovered_peers": sender.get("wifi_direct_discovered_peers", []),
            "wifi_direct_visible_devices": sender.get("wifi_direct_visible_devices", []),
            "wifi_direct_txt_records": sender.get("wifi_direct_txt_records", []),
            "wifi_direct_bound_endpoints": sender.get("wifi_direct_bound_endpoints", []),
            "wifi_p2p_state_summary": sender.get("wifi_p2p_state_summary"),
            "command_wifi_direct_endpoint_binding_state": sender_command.get("wifi_direct_endpoint_binding_state"),
            "command_wifi_direct_endpoint_binding_reason": sender_command.get("wifi_direct_endpoint_binding_reason"),
            "command_wifi_direct_last_connect_device_address": sender_command.get("wifi_direct_last_connect_device_address"),
            "command_wifi_direct_last_connect_device_name": sender_command.get("wifi_direct_last_connect_device_name"),
            "command_wifi_direct_last_connect_group_owner_intent": sender_command.get("wifi_direct_last_connect_group_owner_intent"),
            "command_wifi_direct_last_connect_result": sender_command.get("wifi_direct_last_connect_result"),
            "command_wifi_direct_last_connect_failure_reason": sender_command.get("wifi_direct_last_connect_failure_reason"),
            "command_wifi_direct_connect_attempts": sender_command.get("wifi_direct_connect_attempts", []),
            "command_wifi_direct_bound_endpoints": sender_command.get("wifi_direct_bound_endpoints", []),
            "debug_wifi_direct_peer_requested": sender_command.get("debug_wifi_direct_peer_requested"),
            "debug_wifi_direct_peer_device_address": sender_command.get("debug_wifi_direct_peer_device_address"),
            "debug_wifi_direct_peer_device_name": sender_command.get("debug_wifi_direct_peer_device_name"),
            "debug_wifi_direct_peer_port": sender_command.get("debug_wifi_direct_peer_port"),
            "debug_wifi_direct_peer_status": sender_command.get("debug_wifi_direct_peer_status"),
        },
        "target": {
            "before": {
                "accepted_connections": int_value(target_before, "accepted_connections"),
                "inbound_packets": int_value(target_before, "inbound_packets"),
                "malformed_frames_dropped": int_value(target_before, "malformed_frames_dropped"),
                "selected_route": target_before.get("selected_route"),
                "wifi_direct_permission_warning": target_before.get("wifi_direct_permission_warning"),
                "wifi_direct_permissions": target_before.get("wifi_direct_permissions"),
                "wifi_direct_group_formed": target_before.get("wifi_direct_group_formed"),
                "wifi_direct_group_role": target_before.get("wifi_direct_group_role"),
                "wifi_direct_group_owner_address": target_before.get("wifi_direct_group_owner_address"),
                "wifi_direct_local_p2p_address": target_before.get("wifi_direct_local_p2p_address"),
                "ensure_wifi_direct_group_owner_result": target_before.get("ensure_wifi_direct_group_owner_result"),
                "wifi_p2p_state_summary": target_before.get("wifi_p2p_state_summary"),
            },
            "after": {
                "accepted_connections": int_value(target_after, "accepted_connections"),
                "inbound_packets": int_value(target_after, "inbound_packets"),
                "malformed_frames_dropped": int_value(target_after, "malformed_frames_dropped"),
                "selected_route": target_after.get("selected_route"),
                "wifi_direct_permission_warning": target_after.get("wifi_direct_permission_warning"),
                "wifi_direct_permissions": target_after.get("wifi_direct_permissions"),
                "wifi_direct_group_formed": target_after.get("wifi_direct_group_formed"),
                "wifi_direct_group_role": target_after.get("wifi_direct_group_role"),
                "wifi_direct_group_owner_address": target_after.get("wifi_direct_group_owner_address"),
                "wifi_direct_local_p2p_address": target_after.get("wifi_direct_local_p2p_address"),
                "ensure_wifi_direct_group_owner_result": target_after.get("ensure_wifi_direct_group_owner_result"),
                "wifi_p2p_state_summary": target_after.get("wifi_p2p_state_summary"),
            },
            "deltas": {
                "accepted_connections": accepted_delta,
                "inbound_packets": inbound_delta,
                "malformed_frames_dropped": malformed_delta,
            },
            "observed_before": {
                "message_ids": target_before_observed["message_ids"],
                "packet_ids": target_before_observed["packet_ids"],
                "packet_message_ids": target_before_observed["packet_message_ids"],
            },
            "observed_after": {
                "message_ids": target_after_observed["message_ids"],
                "packet_ids": target_after_observed["packet_ids"],
                "packet_message_ids": target_after_observed["packet_message_ids"],
            },
            "newly_observed_after": {
                "message_ids": target_new_observed["message_ids"],
                "packet_ids": target_new_observed["packet_ids"],
                "packet_message_ids": target_new_observed["packet_message_ids"],
                "matching_message_ids": matching_message_ids,
                "matching_packet_ids": matching_packet_ids,
            },
        },
        "verdict": {
            "permissions_ready": permissions_ready,
            "relationship_ready": relationship_ready,
            "wifi_direct_discovery_ready": wifi_direct_discovery_ready,
            "kraken_dns_sd_peer_seen": kraken_dns_sd_peer_seen,
            "endpoint_bound": endpoint_bound,
            "send_attempted": send_attempted,
            "transport_route_unavailable": transport_route_unavailable,
            "transport_counter_delivery_observed": transport_counter_delivery_observed,
            "message_delivery_proven": message_delivery_proven,
            "failure_stage": failure_stage,
            "status": verdict_status,
        },
        "claim_boundary": (
            "Directed debug Wi-Fi Direct route trial. target_transport_counter_delta_observed means "
            "target inbound counters increased after a sender debug-send. message_delivery_proven means "
            "target-after evidence contains the sender debug message_id and packet_id."
        ),
    }
    write_outputs(
        out_dir=out_dir,
        manifest=manifest,
        generated_at=generated_at,
        git_branch=git_branch,
        git_sha=git_sha,
        git_source_state=git_source_state,
        sender_device=sender_device,
        target_device=target_device,
        hint_target_wifi_direct_peer=hint_target_wifi_direct_peer,
        target_hint_status=target_hint_status,
        target_hint_device_name=target_hint_device_name,
        target_hint_device_address=target_hint_device_address,
        target_hint_port=target_hint_port,
        sender_success=sender_success,
        permissions_ready=permissions_ready,
        relationship_ready=relationship_ready,
        wifi_direct_discovery_ready=wifi_direct_discovery_ready,
        endpoint_bound=endpoint_bound,
        send_attempted=send_attempted,
        sender_command=sender_command,
        sender=sender,
        target_before=target_before,
        target_after=target_after,
        accepted_delta=accepted_delta,
        inbound_delta=inbound_delta,
        malformed_delta=malformed_delta,
        transport_counter_delivery_observed=transport_counter_delivery_observed,
        message_delivery_proven=message_delivery_proven,
        matching_message_ids=matching_message_ids,
        matching_packet_ids=matching_packet_ids,
        target_before_dir=target_before_dir,
        sender_dir=sender_dir,
        target_after_dir=target_after_dir,
    )
    return manifest


def write_outputs(
    *,
    out_dir: Path,
    manifest: dict[str, Any],
    generated_at: str,
    git_branch: str,
    git_sha: str,
    git_source_state: str,
    sender_device: str,
    target_device: str,
    hint_target_wifi_direct_peer: bool,
    target_hint_status: str,
    target_hint_device_name: str,
    target_hint_device_address: str,
    target_hint_port: int | None,
    sender_success: bool,
    permissions_ready: bool,
    relationship_ready: bool,
    wifi_direct_discovery_ready: bool,
    endpoint_bound: bool,
    send_attempted: bool,
    sender_command: dict[str, Any],
    sender: dict[str, Any],
    target_before: dict[str, Any],
    target_after: dict[str, Any],
    accepted_delta: int,
    inbound_delta: int,
    malformed_delta: int,
    transport_counter_delivery_observed: bool,
    message_delivery_proven: bool,
    matching_message_ids: list[str],
    matching_packet_ids: list[str],
    target_before_dir: Path,
    sender_dir: Path,
    target_after_dir: Path,
) -> None:
    (out_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    lines = [
        "# Kraken Directed Wi-Fi Direct Route Trial",
        "",
        f"Generated: `{generated_at}`",
        f"Git: `{git_branch}` / `{git_sha}` / `{git_source_state}`",
        f"Sender: `{sender_device}`",
        f"Target: `{target_device}`",
        f"Debug Wi-Fi Direct peer hint requested: `{str(hint_target_wifi_direct_peer).lower()}`",
        f"Debug Wi-Fi Direct peer hint status: `{target_hint_status}`",
        (
            "Debug Wi-Fi Direct peer hint endpoint: "
            f"`{target_hint_device_name or 'none'}` / "
            f"`{target_hint_device_address or 'none'}` / "
            f"`{target_hint_port or 'none'}`"
        ),
        f"Sender debug-send success: `{str(sender_success).lower()}`",
        f"Permissions ready: `{str(permissions_ready).lower()}`",
        f"Relationship ready: `{str(relationship_ready).lower()}`",
        f"Wi-Fi Direct discovery ready: `{str(wifi_direct_discovery_ready).lower()}`",
        f"Kraken DNS-SD peer seen: `{str(manifest['verdict']['kraken_dns_sd_peer_seen']).lower()}`",
        f"Endpoint bound: `{str(endpoint_bound).lower()}`",
        f"Send attempted: `{str(send_attempted).lower()}`",
        f"Transport route unavailable: `{str(manifest['verdict']['transport_route_unavailable']).lower()}`",
        f"Failure stage: `{manifest['verdict']['failure_stage']}`",
        (
            "Sender endpoint binding state: "
            f"`{sender_command.get('wifi_direct_endpoint_binding_state') or sender.get('wifi_direct_endpoint_binding_state')}`"
        ),
        (
            "Sender endpoint binding reason: "
            f"`{sender_command.get('wifi_direct_endpoint_binding_reason') or sender.get('wifi_direct_endpoint_binding_reason')}`"
        ),
        (
            "Sender last connect target: "
            f"`{sender_command.get('wifi_direct_last_connect_device_name') or sender.get('wifi_direct_last_connect_device_name')}` / "
            f"`{sender_command.get('wifi_direct_last_connect_device_address') or sender.get('wifi_direct_last_connect_device_address')}`"
        ),
        (
            "Sender last connect result: "
            f"`{sender_command.get('wifi_direct_last_connect_result') or sender.get('wifi_direct_last_connect_result')}`"
        ),
        (
            "Sender Wi-Fi Direct connect attempts: "
            f"`{len(sender_command.get('wifi_direct_connect_attempts') or sender.get('wifi_direct_connect_attempts') or [])}`"
        ),
        f"Sender P2P state before: `{((sender.get('wifi_p2p_state_summary') or {}).get('before_broadcast') or {}).get('wifi_p2p_info')}`",
        f"Sender P2P state after: `{((sender.get('wifi_p2p_state_summary') or {}).get('after_broadcast') or {}).get('wifi_p2p_info')}`",
        f"Target accepted delta: `{accepted_delta}`",
        f"Target inbound delta: `{inbound_delta}`",
        f"Target malformed delta: `{malformed_delta}`",
        f"Transport counter delivery observed: `{str(transport_counter_delivery_observed).lower()}`",
        f"Message delivery proven: `{str(message_delivery_proven).lower()}`",
        f"Matching message ids: `{', '.join(matching_message_ids) or 'none'}`",
        f"Matching packet ids: `{', '.join(matching_packet_ids) or 'none'}`",
        f"Sender permission warning: `{sender.get('wifi_direct_permission_warning')}`",
        f"Target before permission warning: `{target_before.get('wifi_direct_permission_warning')}`",
        f"Target after permission warning: `{target_after.get('wifi_direct_permission_warning')}`",
        "",
        "## Captures",
        "",
        f"- Target before: `{relative_capture_path(target_before_dir, out_dir)}`",
        f"- Sender send: `{relative_capture_path(sender_dir, out_dir)}`",
        f"- Target after: `{relative_capture_path(target_after_dir, out_dir)}`",
        "",
        "## Boundary",
        "",
        manifest["claim_boundary"],
        "",
    ]
    (out_dir / "directed_wifi_direct_route_trial.md").write_text("\n".join(lines), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out-dir", required=True, type=Path)
    parser.add_argument("--generated-at", required=True)
    parser.add_argument("--git-branch", required=True)
    parser.add_argument("--git-sha", required=True)
    parser.add_argument("--git-source-state", required=True)
    parser.add_argument("--sender-device", required=True)
    parser.add_argument("--target-device", required=True)
    parser.add_argument("--target-before-dir", required=True, type=Path)
    parser.add_argument("--sender-dir", required=True, type=Path)
    parser.add_argument("--target-after-dir", required=True, type=Path)
    parser.add_argument("--body", required=True)
    parser.add_argument("--prearm-target-group-owner", required=True, type=bool_arg)
    parser.add_argument("--hint-target-wifi-direct-peer", required=True, type=bool_arg)
    parser.add_argument("--target-hint-device-address", default="")
    parser.add_argument("--target-hint-device-name", default="")
    parser.add_argument("--target-hint-port", default="")
    parser.add_argument("--target-hint-status", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    target_hint_port = int(args.target_hint_port) if str(args.target_hint_port).strip() else None
    build_manifest(
        out_dir=args.out_dir,
        generated_at=args.generated_at,
        git_branch=args.git_branch,
        git_sha=args.git_sha,
        git_source_state=args.git_source_state,
        sender_device=args.sender_device,
        target_device=args.target_device,
        target_before_dir=args.target_before_dir,
        sender_dir=args.sender_dir,
        target_after_dir=args.target_after_dir,
        body=args.body,
        prearm_target_group_owner=args.prearm_target_group_owner,
        hint_target_wifi_direct_peer=args.hint_target_wifi_direct_peer,
        target_hint_device_address=args.target_hint_device_address,
        target_hint_device_name=args.target_hint_device_name,
        target_hint_port=target_hint_port,
        target_hint_status=args.target_hint_status,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
