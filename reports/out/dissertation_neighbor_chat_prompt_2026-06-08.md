# Prompt для соседнего чата по диссертации

Работаем на русском, академически аккуратно, без маркетинга. Нужно вставить в диссертацию материалы по Kraken Android research prototype и алгоритму Адамовой.

Source of truth:
- `/Users/cheenya/Projects/kraken-android-research-panel/reports/out/dissertation_final_insert_packet_2026-06-08.md`
- `reports/out/adamova_product_path_demo.md`
- `reports/out/adamova_effectiveness_experiment.md`
- `reports/out/adamova_effectiveness_dissertation_table.md`
- `reports/out/two_device_delivery_evidence.md`
- `reports/out/ble_two_device_delivery_evidence_2026-06-06.md`
- `/Users/cheenya/Projects/disser-messenger-project/reports/out/dissertation_curve_diagnostics_evidence.md`
- `/Users/cheenya/Projects/disser-messenger-project/reports/out/large_coefficient_curve_evidence.md`
- `/Users/cheenya/Projects/disser-messenger-project/reports/out/random_risk_simulation.md`
- `/Users/cheenya/Projects/disser-messenger-project/reports/out/article_speedup_evidence.md`

Ключевая формулировка: алгоритм Адамовой используется в Kraken как C++ admission gate для экспериментального криптографического профиля. Он не шифрует сообщения и не заменяет reviewed primitives. Accepted experimental profile metadata переносится через QR invite, relationship, session profile и packet policy; weak/mismatched/native-unavailable experimental profile не становится message-capable. Standard profile имеет статус `NOT_APPLICABLE_STANDARD_PROFILE`.

Числа:
- Sage pack: 15 curves, SymPy match 15/15, Sage direct matches 11, unsupported-local 4, mismatches 0.
- Large coefficient corpus: 20 curves, Sage comparison 20 matches, mismatches 0; benchmark 5 runs, median 22.8673 ms, p95 24.1665 ms.
- Random risk simulation: seed 20260524, 90 curves, risky 50, safe 40; no_precheck false accepts 50, discriminant-only 40, Kraken precheck 0; needs reference 10.
- Adamova effectiveness: 20 profiles, weak/invalid 10; accepted without precheck 8/10, discriminant-only 6/10, Adamova gate 0/10; rejected/blocked 10/10; controls accepted 10/10; median 10.632542 ms, p95 15.835666 ms.
- C++ vs Kotlin: Research Panel содержит diagnostic-only Kotlin BigInteger vs native C++ benchmark entry/result; fresh checked-in JSON с числами в `reports/out` нет, поэтому не переносить это на производительность шифрования сообщений.
- C++/diagnostic speedup: Python/C++ wall gain 3.24x..7.04x; diagnostic/Sage wall speedup 21.60x.
- Android evidence: manual Samsung/Xiaomi LAN/Wi-Fi and BLE direct-route captures; BLE route label `Bluetooth напрямую`; Kraken GATT UUID `58a1257c-f4a8-48c8-99d5-917b9863d7c4`.

Запреты: не утверждать промышленную защищённость сообщений, не писать что Adamova доказывает стойкость схем над конечными полями, не смешивать rational diagnostics over Q с доказательствами для конечных полей, не заявлять repeated LAN/BLE reliability без статистики, не называть prototype packet crypto подписью или шифрованием.

Нужно подготовить связный фрагмент для главы о программной реализации и отдельный фрагмент для ограничений, с таблицей чисел и аккуратным claim boundary.
