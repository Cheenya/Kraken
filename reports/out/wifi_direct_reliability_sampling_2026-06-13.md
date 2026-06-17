# Wi-Fi Direct Reliability Sampling Report

Дата: 2026-06-13.

## Scope

Проверялся следующий этап после единичного proof-of-delivery: повторная
directed Wi-Fi Direct выборка Device A <-> Device B, чтобы считать стабильность,
а не делать вывод по одному успешному прогону.

Устройства:

- Device A: `ANDROID_DEVICE_A`
- Device B: `ANDROID_DEVICE_B`

Артефакты полного прогона:

- `artifacts/wifi-direct-reliability/20260613-145352-device-a-device-b-wifi-direct-reliability-3x/manifest.json`
- `artifacts/wifi-direct-reliability/20260613-145352-device-a-device-b-wifi-direct-reliability-3x/wifi_direct_reliability_sampling.md`

## Result

Прогон завершился технически, но выборка отрицательная:

| Direction | Trials | Sender success | Transport counter delivery | Message delivery proven | Permission warnings |
| --- | ---: | ---: | ---: | ---: | ---: |
| `ANDROID_DEVICE_A -> ANDROID_DEVICE_B` | 3 | 0/3 | 0/3 | 0/3 | 0 |
| `ANDROID_DEVICE_B -> ANDROID_DEVICE_A` | 3 | 0/3 | 0/3 | 0/3 | 0 |

Итого:

- observed trials: `6/6`
- overall status: `completed_with_failures`
- sender debug send success: `0/6`
- transport counter delivery observed: `0/6`
- message delivery proven: `0/6`
- permission warning trials: `0`

## Diagnosis

Это не выглядит как повторение fine-location blocker:

- `wifi_direct_permission_warning == null` во всех шести trial;
- runtime permission diagnostics показывают clean permission path;
- target counters не растут, потому что sender-side send не доходит до
  transport peer resolution.

Повторяющийся sender-side симптом во всех шести trial:

- `debug_send_wait_satisfied=false`
- `debug_send_success=false`
- `debug_send_error=UNKNOWN_PEER`
- `relationship_peer_seen_by_transport=false`
- `transport_error=wifi-direct-send:wifi-direct-peer-not-found:<fingerprint>`

Device A -> Device B:

- relationship peer fingerprint prefix: `B42B 3068 93`
- observed Wi-Fi Direct peer prefixes: `[]`
- P2P diagnostics: services `0`, TXT records `0`, bound peers `0`,
  unbound visible devices `0`

Device B -> Device A:

- relationship peer fingerprint prefix: `3C4E D5BA 9D`
- observed Wi-Fi Direct peer prefixes: `[]`
- P2P diagnostics: services `0`, TXT records `0`, bound peers `0`,
  unbound visible devices `1`

## Interpretation

Текущий результат нельзя использовать как reliability proof. Напротив, это
воспроизводит нестабильность после предыдущего единичного proof-of-delivery:
permissions clean, но sender не видит relationship peer как Wi-Fi Direct
transport endpoint.

Следующий scoped refactor должен быть не про permissions, а про peer binding /
endpoint resolution / group-owner routing:

- явно логировать P2P group role, owner address и client address на sender и
  target;
- различать "relationship exists" и "Wi-Fi Direct endpoint currently bound";
- обновить directed harness так, чтобы он фиксировал discovery readiness перед
  отправкой отдельно от send result;
- проверить, не теряется ли TXT binding при повторном group negotiation или при
  переходе между направлением Device A -> Device B и Device B -> Device A.

## Claim Boundary

Это debug sampling для двух конкретных телефонов и текущей debug-сборки. Он не
доказывает production reliability/security и не должен формулироваться как
общая оценка Wi-Fi Direct транспорта.
