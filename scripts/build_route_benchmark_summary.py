#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any


def walk_dicts(value: Any, path: str = "$"):
    if isinstance(value, dict):
        yield path, value
        for key, child in value.items():
            yield from walk_dicts(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk_dicts(child, f"{path}[{index}]")


def percentile(values: list[float], percentile_value: float) -> float | None:
    if not values:
        return None
    if len(values) == 1:
        return values[0]
    ordered = sorted(values)
    rank = (len(ordered) - 1) * percentile_value
    lower = int(rank)
    upper = min(lower + 1, len(ordered) - 1)
    weight = rank - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def collect_records(source: Path) -> tuple[list[dict[str, Any]], list[dict[str, Any]], list[dict[str, Any]]]:
    payload = json.loads(source.read_text(encoding="utf-8"))
    latency_records: list[dict[str, Any]] = []
    delivery_records: list[dict[str, Any]] = []
    route_attempts: list[dict[str, Any]] = []

    for path, node in walk_dicts(payload):
        route = node.get("selected_route") or node.get("selected_route_at_export")
        latency = node.get("last_delivery_latency_ms")
        debug_queue_retry = node.get("debug_queue_retry") if isinstance(node.get("debug_queue_retry"), dict) else {}
        delivered = node.get("delivered_after_transport_restart", debug_queue_retry.get("delivered_after_transport_restart"))
        sent = node.get("sent_after_transport_restart", debug_queue_retry.get("sent_after_transport_restart"))
        status = node.get("message_status_after_restart", debug_queue_retry.get("message_status_after_restart"))
        queue_retry_body = node.get("queue_retry_body", debug_queue_retry.get("queue_retry_body"))
        if isinstance(route, str) and route not in {"none", ""}:
            if delivered is not None or sent is not None or status is not None:
                delivery_records.append(
                    {
                        "source": str(source),
                        "path": path,
                        "route": route,
                        "sent_after_transport_restart": sent,
                        "delivered_after_transport_restart": delivered,
                        "message_status_after_restart": status,
                        "last_delivery_latency_ms": latency,
                        "queue_retry_body": queue_retry_body,
                    },
                )
            if isinstance(latency, (int, float)) and delivered is True:
                latency_records.append(
                    {
                        "source": str(source),
                        "path": path,
                        "route": route,
                        "latency_ms": latency,
                        "queue_retry_body": queue_retry_body,
                    },
                )

        attempts = node.get("recent_attempts") or node.get("recent_route_attempts") or node.get("route_attempts")
        if isinstance(attempts, list):
            for index, attempt in enumerate(attempts):
                if not isinstance(attempt, dict):
                    continue
                attempt_route = attempt.get("path")
                if isinstance(attempt_route, str) and attempt_route:
                    route_attempts.append(
                        {
                            "source": str(source),
                            "path": f"{path}.recent_attempts[{index}]",
                            "route": attempt_route,
                            "success": attempt.get("success"),
                            "error": attempt.get("error"),
                            "attempted_at_epoch_millis": attempt.get("attempted_at_epoch_millis"),
                        },
                    )

    return latency_records, delivery_records, route_attempts


def dedupe_records(records: list[dict[str, Any]], fields: tuple[str, ...]) -> list[dict[str, Any]]:
    seen: set[tuple[Any, ...]] = set()
    deduped: list[dict[str, Any]] = []
    for record in records:
        key = tuple(record.get(field) for field in fields)
        if key in seen:
            continue
        seen.add(key)
        deduped.append(record)
    return deduped


def summarize_route(
    route: str,
    latency_records: list[dict[str, Any]],
    delivery_records: list[dict[str, Any]],
    route_attempts: list[dict[str, Any]],
    min_samples: int,
) -> dict[str, Any]:
    latencies = [float(record["latency_ms"]) for record in latency_records if record["route"] == route]
    delivery_for_route = [record for record in delivery_records if record["route"] == route]
    attempts_for_route = [record for record in route_attempts if record["route"] == route]
    delivered_count = sum(1 for record in delivery_for_route if record.get("delivered_after_transport_restart") is True)
    failed_delivery_count = sum(1 for record in delivery_for_route if record.get("delivered_after_transport_restart") is False)
    attempt_success_count = sum(1 for record in attempts_for_route if record.get("success") is True)
    attempt_failure_count = sum(1 for record in attempts_for_route if record.get("success") is False)
    enough = len(latencies) >= min_samples
    return {
        "route": route,
        "latency_sample_count": len(latencies),
        "delivery_record_count": len(delivery_for_route),
        "delivered_record_count": delivered_count,
        "failed_delivery_record_count": failed_delivery_count,
        "route_attempt_success_count": attempt_success_count,
        "route_attempt_failure_count": attempt_failure_count,
        "median_latency_ms": statistics.median(latencies) if latencies else None,
        "p95_latency_ms": percentile(latencies, 0.95),
        "min_latency_ms": min(latencies) if latencies else None,
        "max_latency_ms": max(latencies) if latencies else None,
        "benchmark_status": "passed_min_sample_gate" if enough else "insufficient_n_for_reliability_claim",
    }


def write_markdown(report: dict[str, Any], output: Path) -> None:
    lines = [
        "# Route Benchmark Summary",
        "",
        f"Date: `{report['date']}`.",
        "",
        "## Summary",
        "",
        f"- Minimum sample gate per route: `{report['min_samples_per_route']}`.",
        f"- Overall status: `{report['overall_status']}`.",
        f"- Claim boundary: {report['claim_boundary']}",
        "",
        "## Routes",
        "",
        "| Route | Latency samples | Delivered records | Failed delivery records | Route attempt success/failure | Median ms | P95 ms | Status |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |",
    ]
    for route in report["routes"]:
        lines.append(
            "| {route} | {latency_sample_count} | {delivered_record_count} | {failed_delivery_record_count} | "
            "{route_attempt_success_count}/{route_attempt_failure_count} | {median} | {p95} | {status} |".format(
                route=route["route"],
                latency_sample_count=route["latency_sample_count"],
                delivered_record_count=route["delivered_record_count"],
                failed_delivery_record_count=route["failed_delivery_record_count"],
                route_attempt_success_count=route["route_attempt_success_count"],
                route_attempt_failure_count=route["route_attempt_failure_count"],
                median="n/a" if route["median_latency_ms"] is None else round(route["median_latency_ms"], 3),
                p95="n/a" if route["p95_latency_ms"] is None else round(route["p95_latency_ms"], 3),
                status=route["benchmark_status"],
            ),
        )
    lines.extend(
        [
            "",
            "## Latency Samples",
            "",
        ],
    )
    for record in report["latency_samples"]:
        lines.append(
            f"- `{record['route']}` `{record['latency_ms']} ms` from `{record['source']}` at `{record['path']}`",
        )
    if not report["latency_samples"]:
        lines.append("- none")
    lines.extend(
        [
            "",
            "## What This Does Not Prove",
            "",
            "- It does not prove repeatable reliability while any route is below the minimum sample gate.",
            "- It does not prove production network reliability or production security.",
            "- It does not cover Wi-Fi Direct unless the route appears with delivered samples.",
            "",
        ],
    )
    output.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build route latency/loss benchmark summary from Kraken evidence JSON.")
    parser.add_argument("--source", action="append", required=True, type=Path, help="Evidence JSON source.")
    parser.add_argument("--out-json", required=True, type=Path)
    parser.add_argument("--out-md", required=True, type=Path)
    parser.add_argument("--date", default="2026-06-08")
    parser.add_argument("--min-samples-per-route", type=int, default=10)
    args = parser.parse_args()

    all_latency: list[dict[str, Any]] = []
    all_delivery: list[dict[str, Any]] = []
    all_attempts: list[dict[str, Any]] = []
    for source in args.source:
        latency, delivery, attempts = collect_records(source)
        all_latency.extend(latency)
        all_delivery.extend(delivery)
        all_attempts.extend(attempts)
    all_latency = dedupe_records(all_latency, ("route", "queue_retry_body", "latency_ms"))
    all_delivery = dedupe_records(
        all_delivery,
        ("route", "queue_retry_body", "sent_after_transport_restart", "delivered_after_transport_restart", "message_status_after_restart"),
    )
    all_attempts = dedupe_records(all_attempts, ("route", "success", "error", "attempted_at_epoch_millis"))

    routes = sorted(
        {
            *(record["route"] for record in all_latency),
            *(record["route"] for record in all_delivery),
            *(record["route"] for record in all_attempts),
        },
    )
    summaries = [
        summarize_route(route, all_latency, all_delivery, all_attempts, args.min_samples_per_route)
        for route in routes
    ]
    overall_status = (
        "passed_min_sample_gate"
        if summaries and all(summary["benchmark_status"] == "passed_min_sample_gate" for summary in summaries)
        else "insufficient_n_for_reliability_claim"
    )
    report = {
        "report": "route_benchmark_summary",
        "date": args.date,
        "sources": [str(source) for source in args.source],
        "min_samples_per_route": args.min_samples_per_route,
        "overall_status": overall_status,
        "claim_boundary": (
            "Descriptive route benchmark aggregation from existing evidence. Do not claim repeatable reliability "
            "unless each claimed route passes the minimum sample gate with fresh comparable runs."
        ),
        "routes": summaries,
        "latency_samples": sorted(all_latency, key=lambda row: (row["route"], row["latency_ms"], row["path"])),
        "delivery_records": all_delivery,
        "route_attempts": all_attempts,
    }
    args.out_json.parent.mkdir(parents=True, exist_ok=True)
    args.out_md.parent.mkdir(parents=True, exist_ok=True)
    args.out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_markdown(report, args.out_md)
    print(args.out_json)
    print(args.out_md)
    print(json.dumps({"overall_status": overall_status, "routes": summaries}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
