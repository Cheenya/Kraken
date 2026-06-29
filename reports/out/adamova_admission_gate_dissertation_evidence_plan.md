# Adamova Admission Gate Dissertation Evidence Plan

Статус: шаблон evidence для диссертации и Android Research Mode отчёта.
Дата: 2026-06-05.
Связанный source-of-truth: `docs/adamova-admission-gate-architecture.md`.

## Текущее состояние implementation

Первый Android batch уже содержит backend `AdamovaAdmissionAttackDemoRunner`,
Research Mode карточку запуска и unit-backed проверки для сценариев:

- singular curve profile;
- rational 2-torsion profile;
- 3-torsion indicator profile;
- size guarded profile;
- malformed profile;
- downgrade to weak profile;
- packet profile mismatch;
- handshake rejection for unknown experimental profile;
- outbox rejection for unapproved profile;
- inbox rejection for profile mismatch.

Two-phone metrics и Sage/reference policy для admission в этом документе пока
не заявляются как завершённые.

Дополнительно подготовлен воспроизводимый desktop/report experiment:

- `scripts/adamova_effectiveness_experiment.py`;
- `reports/out/adamova_effectiveness_experiment.md`;
- `reports/out/adamova_effectiveness_experiment.json`;
- `reports/out/adamova_effectiveness_experiment.csv`;
- `reports/out/adamova_effectiveness_dissertation_table.md`.

Он компилирует host CLI из того же C++ source, который используется Android
JNI (`app-android/app/src/main/cpp/kraken_native_placeholder.cpp`), и фиксирует
backend как `host_cpp_native_core_cli_from_android_source`. Live Android
NativeCoreBridge export всё ещё нужно снять отдельным запуском Research Mode
на устройстве.

## Цель эксперимента

Показать, что native C++ Adamova Admission Gate предотвращает автоматическое
принятие слабых или требующих эталонной проверки параметров
исследовательского криптографического профиля Kraken до создания сессии и
отправки сообщений.

Эксперимент не доказывает промышленную криптостойкость сообщений и не заменяет
проверенные криптографические примитивы.

## Сравниваемые режимы

| Режим | Описание |
| --- | --- |
| `no_precheck` | Профиль принимается без математической проверки. |
| `discriminant_only` | Отклоняются только сингулярные кривые. |
| `adamova_gate` | Используется C++ Adamova Admission Gate. |

## Набор сценариев

| Scenario | Описание | Ожидаемый результат Adamova gate |
| --- | --- | --- |
| `singular_curve_profile` | Подмена профиля на сингулярную кривую. | `REJECT_SINGULAR` |
| `two_torsion_profile` | Параметры с рациональным 2-торсионным признаком. | `REJECT_SMALL_TORSION_RISK` или reference policy |
| `three_torsion_indicator_profile` | Параметры с индикатором 3-кручения. | `REJECT_SMALL_TORSION_RISK` или `REFERENCE_VALIDATION_REQUIRED` |
| `large_size_guarded_profile` | Большие коэффициенты вне бюджета локального scan. | `SIZE_GUARDED`, без auto-admission |
| `malformed_profile` | Невалидный JSON/поля `a,b`. | Parser reject |
| `downgrade_profile` | Попытка заменить сохранённый профиль на более слабый. | Mismatch/reject |
| `packet_profile_mismatch` | Пакет с profile hash, отличным от session profile. | Inbox reject |

## Метрики

Минимальные поля отчёта:

```text
git_sha
native_backend_version
profile_policy_version
generated_at
profiles_total
weak_profiles_total
accepted_without_precheck
accepted_by_discriminant_only
accepted_by_adamova_gate
rejected_by_adamova_gate
needs_reference_validation
size_guarded
malformed_rejected
downgrade_rejected
profile_mismatch_rejected
median_gate_latency_ms
p95_gate_latency_ms
```

Производные показатели:

```text
weak_profile_accept_rate_without_precheck
weak_profile_accept_rate_discriminant_only
weak_profile_accept_rate_adamova_gate
absolute_reduction_vs_no_precheck
absolute_reduction_vs_discriminant_only
reference_required_rate
size_guarded_rate
```

## Dissertation wording

Рекомендуемая формулировка:

> В рамках прототипа Kraken реализуется native C++-контур допуска
> исследовательского криптографического профиля на основе алгоритма Адамовой.
> В контролируемой модели подмены параметров данный gate отклоняет
> сингулярные, структурно слабые, неподдержанные или требующие эталонной
> проверки параметры до создания сессии и отправки сообщений. Результат
> демонстрирует полезность validation gate для research-profile, но не является
> доказательством промышленной криптографической стойкости мессенджера.

## Claim boundary

Можно:

- `Adamova gate blocks weak experimental curve profiles before session use`.
- `C++ backend participates in product crypto admission policy`.
- `The experiment reduces accepted weak research profiles under the defined model`.

Нельзя:

- `Adamova proves production message security`.
- `Adamova strengthens X25519/Ed25519/AEAD`.
- `Rational diagnostics over Q validate finite-field ECC production safety`.
- `Bluetooth proximity or LAN discovery creates cryptographic trust`.

## Evidence acceptance

Отчёт считается готовым для диссертации, если:

1. Все сценарии имеют воспроизводимый input.
2. Для каждого режима есть JSON/CSV/Markdown summary.
3. Указаны `git_sha`, `native_backend_version`, `profile_policy_version`.
4. `SIZE_GUARDED` и `REFERENCE_VALIDATION_REQUIRED` не считаются успехом.
5. Есть latency summary.
6. Есть claim boundary.
7. Есть ссылка на архитектурный документ.
8. Есть тесты, доказывающие outbox/inbox enforcement.
