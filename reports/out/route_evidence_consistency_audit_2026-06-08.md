# Route Evidence Consistency Audit

Date: 2026-06-08.

Purpose: prevent ignored raw per-device markdown summaries from being used as
current source-of-truth when they disagree with sibling JSON exports.

Status: `passed_with_quarantined_raw_mismatches`.

| Metric | Count |
| --- | ---: |
| Pairs scanned | 28 |
| Matching pairs | 16 |
| JSON-only raw exports | 9 |
| Mismatched pairs | 3 |
| Mismatched fields | 32 |

## Source Hierarchy

1. `reports/out/two_device_route_specific_smoke_2026-06-08.md`
2. `reports/out/two_device_route_specific_smoke_2026-06-08.json`
3. `reports/out/mesh_metrics_summary.json`
4. `ignored artifacts/two-phone-test JSON`
5. `ignored artifacts/two-phone-test markdown summaries only after JSON consistency check`

## Policy

Ignored per-device markdown summaries are not current source-of-truth when they disagree with sibling JSON; cite consolidated reports/out artifacts first.

## Mismatches

### `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-capture/device-a-R5CY22X6MSB/route_specific_evidence_summary_latest.md`

- Sibling JSON: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-capture/device-a-R5CY22X6MSB/route_specific_evidence_latest.json`
- Status: `raw_summary_mismatch_quarantined`

| Field | JSON path | JSON value | Markdown value |
| --- | --- | --- | --- |
| `Recent route attempts` | `transport.recent_route_attempts.count` | `12` | `4` |
| `Last packet status` | `last_packet_status` | `rejected-1` | `sync-noop` |
| `packetsSent` | `metrics.packets_sent` | `2` | `6` |
| `duplicatesDropped` | `metrics.duplicates_dropped` | `5` | `7` |
| `unknownPeerRejected` | `metrics.unknown_peer_rejected` | `48` | `63` |
| `wrongRecipientRejected` | `metrics.wrong_recipient_rejected` | `2` | `3` |
| `sentAfterTransportRestart` | `debug_smoke.sent_after_transport_restart` | `false` | `true` |
| `messageStatusAfterRestart` | `debug_smoke.message_status_after_restart` | `FAILED` | `SENT_TO_TRANSPORT` |

### `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-capture/route-specific/R5CY22X6MSB/route_specific_evidence_summary_latest.md`

- Sibling JSON: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-capture/route-specific/R5CY22X6MSB/route_specific_evidence_latest.json`
- Status: `raw_summary_mismatch_quarantined`

| Field | JSON path | JSON value | Markdown value |
| --- | --- | --- | --- |
| `Recent route attempts` | `transport.recent_route_attempts.count` | `2` | `4` |
| `Queue size` | `queue_size` | `0` | `1` |
| `packetsSent` | `metrics.packets_sent` | `2` | `6` |
| `packetsReceived` | `metrics.packets_received` | `1` | `2` |
| `duplicatesDropped` | `metrics.duplicates_dropped` | `1` | `7` |
| `expiredDropped` | `metrics.expired_dropped` | `0` | `1` |
| `unknownPeerRejected` | `metrics.unknown_peer_rejected` | `1` | `63` |
| `wrongRecipientRejected` | `metrics.wrong_recipient_rejected` | `1` | `3` |
| `lastDeliveryLatencyMs` | `metrics.last_delivery_latency_ms` | `1391` | `7192` |
| `deliveredAfterTransportRestart` | `debug_smoke.delivered_after_transport_restart` | `true` | `false` |
| `queueSizeAfterRestart` | `debug_smoke.queue_size_after_restart` | `0` | `1` |
| `messageStatusAfterRestart` | `debug_smoke.message_status_after_restart` | `DELIVERED_TO_PEER` | `SENT_TO_TRANSPORT` |

### `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-export/R5CY22X6MSB/route_specific_evidence_summary_latest.md`

- Sibling JSON: `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-stale-peer-fix-export/R5CY22X6MSB/route_specific_evidence_latest.json`
- Status: `raw_summary_mismatch_quarantined`

| Field | JSON path | JSON value | Markdown value |
| --- | --- | --- | --- |
| `Recent route attempts` | `transport.recent_route_attempts.count` | `2` | `4` |
| `Queue size` | `queue_size` | `0` | `1` |
| `packetsSent` | `metrics.packets_sent` | `2` | `6` |
| `packetsReceived` | `metrics.packets_received` | `1` | `2` |
| `duplicatesDropped` | `metrics.duplicates_dropped` | `1` | `7` |
| `expiredDropped` | `metrics.expired_dropped` | `0` | `1` |
| `unknownPeerRejected` | `metrics.unknown_peer_rejected` | `1` | `63` |
| `wrongRecipientRejected` | `metrics.wrong_recipient_rejected` | `1` | `3` |
| `lastDeliveryLatencyMs` | `metrics.last_delivery_latency_ms` | `1391` | `7192` |
| `deliveredAfterTransportRestart` | `debug_smoke.delivered_after_transport_restart` | `true` | `false` |
| `queueSizeAfterRestart` | `debug_smoke.queue_size_after_restart` | `0` | `1` |
| `messageStatusAfterRestart` | `debug_smoke.message_status_after_restart` | `DELIVERED_TO_PEER` | `SENT_TO_TRANSPORT` |

## JSON-only Raw Exports

These exports have no sibling markdown summary to compare. That is acceptable
for raw export folders; use the JSON directly or the consolidated tracked report.

- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/exported-route-evidence/route_specific_evidence_R5CY22X6MSB.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/exported-route-evidence/route_specific_evidence_d948ffd0.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-debug-exported-route-evidence/route_specific_evidence_R5CY22X6MSB.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/final-debug-exported-route-evidence/route_specific_evidence_d948ffd0.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-export/R5CY22X6MSB/route_specific_evidence_latest.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-mixed-export/d948ffd0/route_specific_evidence_latest.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-lan-bt-off-samsung-only-export/R5CY22X6MSB/route_specific_evidence_latest.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-post-radio-debug-export/R5CY22X6MSB/route_specific_evidence_after_wait.json`
- `artifacts/two-phone-test/route-specific-20260607-205617-baseline/fresh-current-apk-post-radio-debug-export/d948ffd0/route_specific_evidence_after_wait.json`
