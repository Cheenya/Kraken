# Key Metrics Plan

## Purpose

The messenger should make key-formation performance observable. The dissertation
angle is not just "we can classify curves faster"; it is "we can measure how
cryptographic validation and key-formation choices affect message and session
workflows."

## Metric Groups

### Torsion Research Metrics

These come from the Stage A / Stage B pipeline.

```text
curve_id
a
b
discriminant_nonzero
c2
has_3_torsion_indicator
classification_case
candidate_count_2
candidate_count_3
candidate_rejected_by_mod_filter
candidate_reached_exact_check
stage_a_wall_time_ns
stage_a_cpu_time_ns
stage_b_torsion_type
stage_b_wall_time_ns
agreement_status
disagreement_reason
```

### Session Key Metrics

```text
session_id
peer_count
local_keypair_generation_ns
remote_key_validation_ns
key_agreement_ns
kdf_ns
session_state_write_ns
total_session_setup_ns
bytes_sent_before_ready
bytes_received_before_ready
failure_stage
failure_reason
```

### Message Key Metrics

```text
message_id
session_id
message_key_derivation_ns
nonce_generation_ns
encrypt_ns
decrypt_ns
payload_bytes
ciphertext_bytes
associated_data_bytes
ratchet_step
failure_stage
failure_reason
```

### Mesh Metrics

```text
mesh_id
peer_count
route_hop_count
session_count_created
session_count_reused
group_rekey_ns
total_key_ops
total_key_ops_ns
message_delivery_latency_ns
```

## Benchmark Principles

1. Always separate wall-clock time from CPU time when possible.
2. Record warm-up runs separately from measured runs.
3. Store environment metadata:
   - OS;
   - CPU;
   - runtime version;
   - build mode;
   - git commit;
   - benchmark seed.
4. Report medians and percentiles, not only averages.
5. Keep benchmark fixtures deterministic.
6. Never benchmark UI latency as cryptographic latency unless explicitly labeled.

## Output Formats

Start with plain JSON Lines:

```text
benchmarks/results/*.jsonl
```

Later add:

- CSV export for dissertation tables;
- Markdown summary reports;
- dashboard ingestion if a frontend appears.

## Dissertation-Relevant Questions

The metrics should be able to answer:

1. How much faster is Stage A than reference torsion computation?
2. How much candidate reduction do arithmetic filters provide?
3. How does validation overhead compare with key agreement and KDF time?
4. Does per-message key derivation dominate session setup?
5. How does mesh size affect key operation count?
6. Where is the practical boundary between research validation and runtime cost?
