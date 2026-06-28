# Эксперимент допуска по критерию Адамовой

ID эксперимента: `adamova_effectiveness_experiment_20260605`.
Seed: `20260605`.
Git SHA: `e542ea0`.
Версия политики профиля: `1`.
Backend проверки: `host_cpp_native_core_cli_from_android_source`.
Версия native backend: `Kraken native C++: диагностика Adamova Stage A для signed 128-bit и smooth arbitrary-size коэффициентов.`.
Путь native CLI: `build/adamova-host/adamova_native_cli`.
Хранение native CLI: `generated_on_demand_build_output`.

Native CLI пересобирается по запросу в каталоге `build/`; в репозитории
хранятся исходные файлы и сохранённые отчёты.

## Область проверки

Контролируемый эксперимент с подстановкой профилей рациональных кривых. Он
показывает работу admission/precheck-логики для выбранного набора профилей.

## Короткий вывод

В контролируемой модели подстановки профилей критерий Адамовой снизил число
принятых weak/invalid-профилей с 8/10 без precheck и 6/10 при проверке только
дискриминанта до 0/10. Случаи с превышением размера и неподдержанными
параметрами отправлены на reference validation до использования в сессии или
сообщении.

## Метрики

| Метрика | Значение |
| --- | ---: |
| `profiles_total` | 20 |
| `weak_or_invalid_total` | 10 |
| `accepted_control_total` | 10 |
| `constructed_reference_cases` | 10 |
| `sage_fixture_controls` | 3 |
| `generated_control_profiles` | 7 |
| `malformed_total` | 2 |
| `size_guarded_total` | 1 |
| `downgrade_or_mismatch_total` | 2 |
| `accepted_without_precheck_weak` | 8 |
| `accepted_by_discriminant_only_weak` | 6 |
| `accepted_by_adamova_gate_weak` | 0 |
| `rejected_or_blocked_by_adamova_gate_weak` | 10 |
| `accepted_controls_by_adamova_gate` | 10 |
| `needs_reference_validation` | 2 |
| `size_guarded` | 1 |
| `median_gate_latency_ms` | 10.632542 |
| `p95_gate_latency_ms` | 15.835666 |

## Результаты

| Сценарий | Группа | Риск | Эталон | Без precheck | Только дискриминант | Решение Adamova | Принят Adamova |
| --- | --- | ---: | --- | ---: | ---: | --- | ---: |
| `singular_zero_zero` | singular | True | constructed_singular | True | False | `REJECT_SINGULAR` | False |
| `singular_minus3_2` | singular | True | constructed_singular | True | False | `REJECT_SINGULAR` | False |
| `two_torsion_minus1_0` | small_torsion | True | constructed_rational_2_torsion | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `two_torsion_minus4_0` | small_torsion | True | constructed_rational_2_torsion | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `three_torsion_0_1` | small_torsion | True | constructed_3_torsion_indicator | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `malformed_a` | malformed | True | not_applicable_malformed | False | False | `REFERENCE_VALIDATION_REQUIRED` | False |
| `malformed_b` | malformed | True | not_applicable_malformed | False | False | `REFERENCE_VALIDATION_REQUIRED` | False |
| `size_guarded_128ish` | size_guarded | True | requires_reference_validation | True | True | `SIZE_GUARDED` | False |
| `downgrade_to_two_torsion` | downgrade | True | constructed_downgrade_attack | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `packet_profile_mismatch_three_torsion` | packet_mismatch | True | constructed_packet_profile_mismatch | True | True | `REJECT_SMALL_TORSION_RISK` | False |
| `sage_accept_control_sage_checked_5_001` | accepted_control | False | SageMath torsion Z/5Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `sage_accept_control_sage_checked_5_003` | accepted_control | False | SageMath torsion Z/5Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `sage_accept_control_sage_checked_7_001` | accepted_control | False | SageMath torsion Z/7Z; Stage A case A4 | True | True | `ACCEPT` | True |
| `generated_accept_control_01` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_02` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_03` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_04` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_05` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_06` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |
| `generated_accept_control_07` | accepted_control | False | generated_stage_a_accept_control | True | True | `ACCEPT` | True |

## Интерпретация

- `no_precheck` моделирует принятие любого синтаксически корректного профиля.
- `discriminant_only` отсекает сингулярные кривые, но пропускает индикаторы rational 2/3-torsion.
- `adamova_gate` отсекает сингулярные и small-torsion-risk профили, а malformed/size-guarded случаи не допускает автоматически.
- Эксперимент фиксирует поведение admission-логики на проверенном корпусе.
