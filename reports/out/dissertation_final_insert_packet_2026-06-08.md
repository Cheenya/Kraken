# Финальный пакет вставки в диссертацию

Дата: 2026-06-08.

Назначение: компактный набор проверенных чисел и формулировок для разделов диссертации о математическом контуре, Android-прототипе Kraken и ограничениях. Стиль отчёта намеренно осторожный: это исследовательский прототип и evidence pack, не промышленная система защиты сообщений.

## Source Of Truth

| Источник | Статус |
| --- | --- |
| `reports/out/current_project_readiness_2026-06-08.md` | найден, базовый 2026-06-08 snapshot |
| `reports/out/kraken_10_10_gap_audit_2026-06-08.md` | найден, базовый список blockers |
| `reports/out/kraken_10_10_followup_audit_2026-06-10.md` | найден, актуальный 2026-06-10 follow-up: hard gate/runner/single-device слой без повышения до `10/10` |
| `reports/out/kraken_release_hard_gate_2026-06-10.md` | найден, minimal release-like hard gate |
| `reports/out/route_benchmark_runner_2026-06-10.md` | найден, repeatable benchmark procedure; not evidence until two-device run |
| `reports/out/physical_inline_relay_runner_2026-06-10.md` | найден, repeatable physical inline relay procedure; not evidence until two-device run |
| `reports/out/single_device_partial_evidence_2026-06-10.md` | найден, Xiaomi-only partial diagnostics |
| `reports/out/two_device_route_specific_smoke_2026-06-08.md` / `.json` | найден, route-specific LAN/BLE smoke |
| `reports/out/route_evidence_consistency_audit_2026-06-08.md` / `.json` | найден, stale raw summaries quarantined |
| `reports/out/kraken_10_10_readiness_plan_2026-06-07.md` | найден, historical |
| `reports/out/current_project_readiness_2026-06-07.md` | найден, historical |
| `reports/out/adamova_effectiveness_experiment.md` / `.json` | найден, числа сверены с JSON |
| `reports/out/adamova_effectiveness_dissertation_table.md` | найден |
| `reports/out/adamova_effectiveness_completion_audit.md` | найден |
| `reports/out/two_device_delivery_evidence.md` | найден |
| `reports/out/ble_two_device_delivery_evidence_2026-06-06.md` | найден |
| `docs/kraken-attack-scenarios-evidence.md` | найден |
| `docs/adamova-admission-gate-architecture.md` | найден |
| `/Users/cheenya/Projects/disser-messenger-project/reports/out/sage_validation/reference_comparison_summary.json` | найден, Sage pack сверка 15 кривых |
| `/Users/cheenya/Projects/disser-messenger-project/reports/out/large_coefficient_sage_validation/reference_comparison_summary.json` | найден, large coefficient сверка 20 кривых |
| `/Users/cheenya/Projects/disser-messenger-project/reports/out/large_coefficient_benchmark_aggregate.json` | найден, benchmark числа сверены |
| `/Users/cheenya/Projects/disser-messenger-project/reports/out/random_risk_simulation.json` | найден, simulation числа сверены |
| `/Users/cheenya/Projects/disser-messenger-project/reports/out/article_speedup_evidence.json` | найден, speedup и filter efficiency числа сверены |

## Таблица чисел

| Блок | Числа | Корректная интерпретация |
| --- | --- | --- |
| Sage pack | 15 curves; SymPy match 15/15; Sage direct matches 11; unsupported-local 4; Sage mismatches 0 | Базовый рациональный diagnostic pack воспроизводим; unsupported cases являются границами метода |
| Large coefficient corpus | 20 curves; Sage comparison 20 matches; unsupported/skipped 0; mismatches 0 | Диагностика и Android-ready reports воспроизводимы на больших рациональных коэффициентах над `Q` |
| Large coefficient benchmark | 5 runs; 20 curves/run; median 22.8673 ms; p95 24.1665 ms; failures 0 | Локальная производительность диагностического workflow, не универсальная метрика |
| Random risk simulation | seed 20260524; 90 curves; risky 50; safe 40; no_precheck false accepts 50; discriminant-only false accepts 40; Kraken precheck false accepts 0; needs reference 10 | Diagnostic gate отбраковывает или карантинирует construction-labeled risky cases в контролируемом корпусе |
| Эффективность диагностики кручения рациональных кривых в криптографическом контуре | 20 профилей; слабых/некорректных 10; принято без предварительной проверки 8/10; принято только по дискриминанту 6/10; автоматически принято после диагностики кручения 0/10; отклонено/заблокировано 10/10; controls accepted 10/10 | C++ реализация диагностики кручения рациональных кривых снижает автоматический допуск слабых экспериментальных профилей до нуля в контролируемой модели |
| Latency контура диагностики кручения | median 10.632542 ms; p95 15.835666 ms | Локальная задержка проверки допуска для экспериментальных профилей |
| C++ vs Kotlin | Research Panel содержит diagnostic-only Kotlin BigInteger vs native C++ benchmark entry/result; стабильного checked-in JSON с fresh run numbers в `reports/out` нет | Можно утверждать наличие in-app benchmark path, но не переносить его на криптографическую производительность сообщений |
| C++ vs Python/Sage | Python/C++ wall gain 3.24x..7.04x; CPU gain 6.59x..15.94x; diagnostic/Sage wall speedup 21.60x; CPU speedup 41.02x | Ускорение Stage A diagnostic implementation, не ускорение шифрования/подписи сообщений |
| Filter efficiency | 200000 nonsingular curves; 29384252 psi3 candidates; 28639695 rejected by modular filters; rejection share 97.47% | Эффективность предварительной фильтрации в диагностической задаче |
| LAN NSD/TCP evidence | LAN NSD/TCP route-specific smoke over local Wi-Fi; delivered both directions after stale-peer cleanup; latency samples 1391 ms and 929 ms; Wi-Fi Direct prototype transport exists separately and clean diagnostics prove permission/radio/service plus registration/discovery start | Fresh two-device smoke for `lan-nsd-tcp`; Wi-Fi Direct still needs peer discovery, two-phone route/negative evidence and is not reliability benchmark |
| BLE evidence | BLE GATT retry path with receipts; latency samples 7192 ms and 7145 ms; older physical UI evidence had `Bluetooth напрямую` | Route-specific prototype evidence, not broad BLE reliability |
| Route evidence consistency | 28 raw JSON/markdown pairs scanned; 3 stale markdown summaries quarantined; 32 mismatched fields | Consolidated report/JSON is source-of-truth when raw summaries disagree |
| Current readiness | dissertation research prototype 8/10; route/attack evidence 7/10 on 2026-06-08 | Сильный research prototype, но не `10/10`: Wi-Fi Direct phone evidence, Mac inline MITM, physical hostile injection and reliability benchmark open |

## Что можно утверждать

- Kraken реализует Android research prototype с локальной личностью, QR-established relationship, чатами и evidence для LAN/BLE nearby routes.
- Диагностика кручения рациональных кривых участвует в продуктовой политике как C++ контур допуска экспериментального криптографического профиля.
- Metadata принятого экспериментального профиля переносится через QR invite, pending/active relationship, session profile и packet policy.
- Слабый, несовпадающий или недоступный для native-проверки экспериментальный профиль не становится message-capable.
- Стандартный профиль на проверенных примитивах получает статус “диагностика кручения не применяется” (`NOT_APPLICABLE_STANDARD_PROFILE`) и не изображается как защищённый этим алгоритмом.
- Математический контур относится к рациональным кривым над `Q`; он даёт диагностические и workflow evidence, а не доказательство промышленной криптостойкости сообщений.

## Что нельзя утверждать

- Нельзя писать, что прототип готов к промышленной защите сообщений.
- Нельзя писать, что диагностика кручения рациональных кривых доказывает криптостойкость промышленных схем над конечными полями или заменяет reviewed primitives.
- Нельзя смешивать rational diagnostics over `Q` с доказательствами для finite-field ECC.
- Нельзя утверждать повторяемую надёжность LAN/BLE маршрутов: текущие телефонные evidence являются manual smoke/UI captures, без полноценной статистики latency/loss/retry.
- Нельзя называть `PrototypeNoSecurityPacketCrypto` реальной подписью или шифрованием.
- Нельзя заявлять Wi-Fi Direct как end-to-end проверенный route.
- Нельзя писать `10/10`, пока Wi-Fi Direct не проверен на телефонах
  отдельными route/negative tests.

## Текст для главы про приложение

В рамках прикладной части разработан Android-прототип мессенджера Kraken, демонстрирующий локальную идентичность, QR-установление доверенного отношения, список чатов, обмен сообщениями и route-aware отображение локальных каналов связи. Прототип содержит двухустройственные evidence для обмена сообщениями между Samsung и Xiaomi: для LAN NSD/TCP зафиксированы route-specific доставки в обе стороны с latency samples 1391 ms и 929 ms, а для BLE GATT зафиксирован retry path с receipt counters и latency samples 7192 ms и 7145 ms. Wi-Fi Direct добавлен как prototype transport path: clean diagnostics подтверждают permission/radio/service readiness, service registration и discovery start, но peer discovery и message delivery ещё не доказаны. Эти данные показывают работоспособность конкретных prototype paths, но не заменяют reliability benchmark.

Отдельный вклад приложения состоит в связывании математической диагностики с продуктовой политикой экспериментального криптографического профиля. Диагностика кручения рациональных кривых используется не как шифрующий примитив, а как C++ контур допуска: профиль проходит проверку до создания message-capable relationship и до принятия packet/session metadata. Для стандартного профиля результатом является “диагностика кручения рациональных кривых не применяется” (`NOT_APPLICABLE_STANDARD_PROFILE`), что предотвращает ложное утверждение о защите стандартного профиля диагностикой кручения рациональных кривых.

## Текст для главы про ограничения

Полученные результаты следует интерпретировать как evidence исследовательского прототипа. Диагностика кривых выполняется для рациональной модели над `Q` и не является доказательством стойкости промышленных схем над конечными полями. Packet crypto в текущем Android-прототипе явно имеет prototype/no-security boundary; отсутствуют подписи промышленного уровня, шифрование payload, Android Keystore identity lifecycle и внешний security review.

Двухтелефонные LAN/BLE материалы подтверждают работоспособность конкретных prototype-сценариев и route-specific counters, но не образуют статистически значимый reliability benchmark. Для более сильного инженерного утверждения нужны Wi-Fi Direct peer discovery и two-phone route/negative evidence, Mac inline relay attack, physical hostile packet injection, N-run latency/loss/retry history и отдельные rejection-smoke сценарии для BLE/LAN/Wi-Fi Direct. Поэтому формулировки в диссертации должны говорить о демонстрации архитектуры, диагностического workflow и контролируемых evidence, а не о завершённой промышленной системе.

## Мини-вывод

Для диссертации наиболее защищаемая линия такая: Kraken демонстрирует криптографический контур прототипа, где математическая диагностика кручения рациональных кривых становится частью политики допуска экспериментального профиля и влияет на возможность создания message-capable path. Это сильнее, чем “метод показан на отдельной диагностической вкладке”, но остаётся строго в границах прототипа и рациональной диагностики над `Q`.
