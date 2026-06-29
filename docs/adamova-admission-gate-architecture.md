# Архитектура контура допуска диагностики кручения рациональных кривых

Статус: source of truth для архитектуры и текущего implementation batch.
Дата: 2026-06-05.
Scope: C++ контур допуска диагностики кручения рациональных кривых, криптографические профили
Kraken, QR/BLE/LAN-сопряжение, политика пакетов/сессий и диссертационные
evidence.

Этот документ фиксирует целевую архитектуру, в которой диагностика кручения рациональных кривых
участвует в продуктовой криптографической политике Kraken как **контур допуска
криптографического профиля**, а не как шифрующий примитив.

Далее для краткости используется формулировка “диагностика кручения
рациональных кривых”.

В человекочитаемых документах используем русские термины. Технические имена
enum/API оставляем в скобках только там, где нужно связать текст с кодом:
“ограничено по размеру/сложности (`SIZE_GUARDED`)”, “контур допуска
криптографического профиля (`ProductCryptoAdmissionGate`)”.

Документ отделяет уже реализованный implementation batch от целевой
архитектуры. Он нужен как:

- техническое задание для разработчиков;
- материал для консультации с криптографами;
- база для диссертационного раздела о внедрении алгоритма;
- guardrail против ложных утверждений о промышленной криптографической защите.

## 0. Текущий implementation snapshot

На 2026-06-05 в Android branch реализован первый продуктовый контур:

- `KrakenCryptoProfile`;
- `AdamovaAdmissionDecision`;
- `ProductCryptoAdmissionGate`;
- `CryptoProfileAdmissionStore`;
- профильная привязка в QR invite/response/confirmation;
- профильная привязка в `Relationship`;
- `cryptoProfileId`, `sessionProfileId`, `admissionDecisionHash`,
  `profilePolicyVersion` в `KrakenPacket`;
- outbox/inbox enforcement по профилю;
- handshake gate: неизвестный или отклонённый экспериментальный профиль не активирует
  relationship;
- backend `AdamovaAdmissionAttackDemoRunner` для сравнения `no_precheck`,
  `discriminant_only`, `adamova_gate`;
- диагностическая карточка запускает демонстрацию работы диагностики кручения в
  криптографическом контуре, показывает метрики и сохраняет локальные
  JSON/Markdown evidence-файлы;
- unit-backed evidence report.

Ещё не закрыто этим batch:

- live two-phone metrics;
- production signatures/encryption/Keystore;
- Sage/reference policy для допуска всех принятых экспериментальных профилей;
- UI для подробных причин отказа профиля.

## 1. Короткое решение

Лучший вариант интеграции:

```text
QR/BLE/LAN discovery
        ↓
QR-established trust
        ↓
CryptoProfile proposal
        ↓
C++ контур допуска диагностики кручения рациональных кривых
        ↓
SessionProfile
        ↓
Packet policy
        ↓
P2P/BLE/LAN transport
        ↓
Research evidence / attack report
```

Диагностика кручения рациональных кривых должна быть встроена так:

- **не** как замена X25519/Ed25519/AEAD/подписям;
- **не** как проверка каждого текста сообщения;
- **не** как доказательство промышленной криптостойкости;
- **да** как native C++ контур, который допускает или блокирует
  экспериментальный криптографический профиль до создания сессии и отправки
  пакетов.

Главная формулировка:

> Диагностика кручения рациональных кривых встроен как нативный C++ контур допуска
> экспериментального криптографического профиля. Он предотвращает принятие
> структурно слабых, сингулярных, подменённых или требующих эталонной проверки
> параметров рациональной эллиптической кривой до создания сессии и отправки
> сообщений.

## 2. Термины

| Термин | Значение |
| --- | --- |
| `KrakenCryptoProfile` | Описание криптографического режима, который может использоваться в relationship/session. |
| `STANDARD_REVIEWED_PRIMITIVES` | Стандартный профиль на проверенных примитивах. Диагностика кручения для него не применяется. |
| `EXPERIMENTAL_ADAMOVA_CURVE_PROFILE` | Экспериментальный профиль, где параметры рациональной кривой обязаны пройти C++ контур допуска диагностики кручения. |
| `ProductCryptoAdmissionGate` | Контур допуска криптографического профиля. |
| `AdamovaAdmissionDecision` | Результат допуска: принять, отклонить, отправить на эталонную проверку, ограничить по размеру/сложности или закрыть при недоступном native backend. |
| `CryptoProfileAdmissionStore` | Локальное хранилище результатов допуска и кэша. |
| `SessionProfile` | Профиль, привязанный к активной сессии/relationship. |
| `admissionDecisionHash` | Хэш решения допуска, привязанный к profile hash, версии политики и версии native backend. |
| `profilePolicyVersion` | Версия правил допуска. Нужна для инвалидации старых решений. |
| `nativeBackendVersion` | Версия C++ backend диагностики кручения. Нужна для воспроизводимости. |

## 3. Почему не встраивать алгоритм прямо в шифрование

Диагностика кручения рациональных кривых в текущей исследовательской постановке анализирует
рациональные эллиптические кривые:

```text
E: y^2 = x^3 + ax + b,  a,b ∈ Z,  поле Q
```

Промышленная криптография мессенджеров обычно использует конечные поля,
reviewed primitives, протоколы установления ключей, подписи, AEAD, replay
protection и key lifecycle. Поэтому диагностика рациональных кривых над `Q`
не является прямой заменой:

- key agreement;
- цифровой подписи;
- проверки подлинности peer-а;
- шифрования payload;
- MAC/AEAD;
- защиты от replay;
- Android Keystore;
- security review.

Правильная роль алгоритма:

```text
проверить, можно ли использовать исследовательские параметры профиля
```

Неправильная роль:

```text
заменить промышленную криптографию сообщений
```

## 4. Целевая модель криптографических профилей

### 4.1 Standard profile

```text
profileType = STANDARD_REVIEWED_PRIMITIVES
adamova = NOT_APPLICABLE_STANDARD_PROFILE
```

Назначение:

- будущая промышленная криптография на reviewed primitives;
- production-like безопасный baseline;
- не зависит от диагностики кручения рациональных кривых.

Запрещено:

- блокировать standard profile только потому, что диагностика кручения не применяется;
- писать, что диагностика кручения усиливает standard profile;
- смешивать rational diagnostics over Q с finite-field production proof.

### 4.2 Экспериментальный профиль на параметрах рациональной кривой

```text
profileType = EXPERIMENTAL_ADAMOVA_CURVE_PROFILE
curveModel = RATIONAL_SHORT_WEIERSTRASS
fieldModel = Q_RESEARCH_DIAGNOSTIC
a = ...
b = ...
```

Назначение:

- экспериментальный демонстрационный профиль;
- атаки на слабые параметры;
- evidence для диссертации;
- контур проверки перед сессией/пакетами.

Для этого профиля C++ контур допуска диагностики кручения обязателен.

## 5. ProductCryptoAdmissionGate

### 5.1 Вход

Минимальный вход:

```text
profileId
profileType
curveModel
fieldModel
a
b
profilePolicyVersion
requestedBy
```

`requestedBy` нужен для evidence:

```text
QR_INVITE_IMPORT
QR_RESPONSE_IMPORT
QR_CONFIRMATION_IMPORT
BLE_NEARBY_PROFILE
LAN_NEARBY_PROFILE
SESSION_REVALIDATION
OUTBOX_PRECHECK
INBOX_PRECHECK
RESEARCH_ATTACK_SIMULATION
```

### 5.2 Выход

```text
AdamovaAdmissionDecision
decisionReason
riskFlags
diagnosticCase
profileHash
admissionDecisionHash
nativeBackendVersion
profilePolicyVersion
validatedAtEpochMillis
referenceStatus
```

Решения:

| Техническое имя | Русское значение | Политика |
| --- | --- | --- |
| `ACCEPT` | Профиль прошёл локальную C++ диагностику. | Можно использовать в экспериментальной сессии. |
| `REJECT_SINGULAR` | Кривая сингулярна. | Жёсткое отклонение. |
| `REJECT_SMALL_TORSION_RISK` | Найдены признаки нежелательной малой структуры. | Жёсткое отклонение для автоматического допуска. |
| `REFERENCE_VALIDATION_REQUIRED` | Требуется эталонная проверка. | Не активировать автоматически. |
| `SIZE_GUARDED` | Ограничено по размеру/сложности. | Не считать успехом; отправить на эталонную проверку. |
| `NATIVE_UNAVAILABLE` | C++ backend недоступен. | Экспериментальный профиль закрывается без допуска. |
| `NOT_APPLICABLE_STANDARD_PROFILE` | Стандартный профиль, диагностика кручения рациональных кривых не применяется. | Стандартный профиль не блокируется этим контуром. |

### 5.3 Политика отказа

| Случай | Стандартный профиль | Экспериментальный профиль на параметрах рациональной кривой |
| --- | --- | --- |
| Native backend работает | Продолжить | Контур допуска обязателен |
| Native backend недоступен | Продолжить | Закрыть без допуска |
| Ограничено по размеру/сложности (`SIZE_GUARDED`) | Не применяется | Блокировать автоматический допуск |
| Требуется эталонная проверка (`REFERENCE_VALIDATION_REQUIRED`) | Не применяется | Не создавать message-capable сессию без эталонной проверки |
| Неизвестный тип профиля | Отклонить | Отклонить |

Для диссертации важно: “ограничено по размеру/сложности” и “требуется
эталонная проверка” не являются успехом алгоритма. Это честные состояния:
локальная диагностика не может автоматически допустить профиль.

## 6. C++ backend как источник истины

Целевое правило:

> Для product admission используется C++ backend диагностики кручения. Kotlin не должен
> становиться второй независимой реализацией математического решения.

Kotlin-слой отвечает за:

- сериализацию профиля;
- вызов `NativeCoreBridge`;
- хранение результата;
- UI/evidence;
- packet/session policy;
- fallback messaging для standard profile.

C++-слой отвечает за:

- точную диагностику кривой;
- дискриминант;
- рациональное 2-кручение;
- индикатор 3-кручения;
- диагностический случай A1-A6;
- состояние “ограничено по размеру/сложности”;
- native backend version/status.

Ключевой developer contract:

```text
ProductCryptoAdmissionGate
    -> NativeCoreBridge.classifyAdamovaOrNull(a, b)
    -> map NativeAdamovaResult to AdamovaAdmissionDecision
    -> persist decision
    -> enforce decision in relationship/session/packet pipeline
```

## 7. Хэширование и воспроизводимость решения

`profileHash` должен считаться от canonical profile representation:

```text
profileType
curveModel
fieldModel
a
b
declaredProfileVersion
```

`admissionDecisionHash` должен включать:

```text
profileHash
AdamovaAdmissionDecision
diagnosticCase
riskFlags
nativeBackendVersion
profilePolicyVersion
referenceStatus
```

Кэш-ключ:

```text
curveHash + nativeBackendVersion + admissionPolicyVersion
```

Инвалидация:

- изменилась версия C++ backend;
- изменилась политика admission;
- изменились параметры кривой;
- изменился reference status;
- обнаружен mismatch между локальным и remote hash.

## 8. Привязка к QR/BLE/LAN-сопряжению

### 8.1 Invite

Invite может содержать:

```text
identityFingerprint
relationshipIntent
cryptoProfileId
profileHash
profilePolicyVersion
nativeBackendVersion
admissionDecisionHash
```

Если invite предлагает экспериментальный профиль на параметрах рациональной кривой:

```text
parse invite
    ↓
load/propose profile
    ↓
C++ контур допуска диагностики кручения рациональных кривых
    ↓
if ACCEPT -> continue handshake
if reject/reference/guarded -> relationship не активируется
```

### 8.2 Response

Responder должен:

- самостоятельно пересчитать local admission decision;
- не доверять remote `admissionDecisionHash` без проверки;
- вернуть свой `admissionDecisionHash`;
- зафиксировать mismatch как отказ или warning, в зависимости от policy.

### 8.3 Confirmation

Final confirmation должна активировать контакт только если:

- QR trust flow завершён;
- relationship identity совпадает;
- crypto profile hash совпадает;
- local admission decision допустим;
- remote admission decision совместим;
- policy version не конфликтует.

## 9. Привязка к SessionProfile

Relationship `ACTIVE` должен иметь session/profile state:

```text
relationshipId
peerFingerprint
sessionProfileId
cryptoProfileId
profileHash
admissionDecisionHash
admissionDecision
profilePolicyVersion
nativeBackendVersion
referenceStatus
createdAtEpochMillis
lastRevalidatedAtEpochMillis
```

Если profile experimental и `admissionDecision != ACCEPT`, сессия не должна
становиться message-capable.

Допустимые состояния:

| Session state | Meaning |
| --- | --- |
| `STANDARD_PROFILE_READY` | Standard profile ready; диагностика кручения не применяется. |
| `EXPERIMENTAL_PROFILE_ACCEPTED` | Experimental profile accepted by C++ gate. |
| `EXPERIMENTAL_PROFILE_REFERENCE_REQUIRED` | Не message-capable без reference. |
| `EXPERIMENTAL_PROFILE_REJECTED` | Не message-capable. |
| `PROFILE_MISMATCH` | Требует повторного согласования. |
| `NATIVE_GATE_UNAVAILABLE` | Не message-capable для экспериментального профиля. |

## 10. Привязка к KrakenPacket

В `KrakenPacket` целевые поля:

```text
cryptoProfileId
sessionProfileId
admissionDecisionHash
profilePolicyVersion
```

Нельзя класть в каждый packet полный математический отчёт. Пакет должен нести
только ссылку на уже утверждённый профиль и decision hash.

### 10.1 Outbox enforcement

Перед созданием или отправкой packet:

```text
message -> relationship -> sessionProfile
    ↓
if standard profile -> continue
if experimental accepted -> continue
if rejected/reference/guarded/native unavailable -> block
```

Rejection reasons:

```text
CRYPTO_PROFILE_REJECTED
CRYPTO_PROFILE_REFERENCE_REQUIRED
CRYPTO_PROFILE_SIZE_GUARDED
CRYPTO_PROFILE_NATIVE_UNAVAILABLE
CRYPTO_PROFILE_MISSING
CRYPTO_PROFILE_HASH_MISMATCH
```

### 10.2 Inbox enforcement

Перед созданием входящего сообщения:

```text
packet -> relationship lookup -> sessionProfile lookup
    ↓
check cryptoProfileId
check sessionProfileId
check admissionDecisionHash
check profilePolicyVersion
    ↓
accept only if compatible
```

Inbound packet должен отклоняться, если:

- peer неизвестен;
- relationship не `ACTIVE`;
- packet profile неизвестен;
- profile hash не совпадает;
- decision hash не совпадает;
- local profile rejected/reference-required/guarded;
- packet пытается downgrade на слабый profile.

## 11. Демонстрация результата алгоритма в криптографическом контуре

Цель демонстрации:

> Показать, что C++ контур допуска диагностики кручения рациональных кривых предотвращает принятие
> слабого экспериментального криптографического профиля до сессии и отправки
> сообщений.

Базовые режимы сравнения:

| Режим | Значение |
| --- | --- |
| `no_precheck` | Приложение принимает профиль без проверки. |
| `discriminant_only` | Проверяется только сингулярность. |
| `adamova_gate` | Работает полный C++ контур допуска диагностики кручения рациональных кривых. |

### 11.1 Сценарии

| Сценарий | Вход атаки | Ожидаемый результат диагностики кручения рациональных кривых | Evidence |
| --- | --- | --- | --- |
| Подмена сингулярной кривой | `4a^3 + 27b^2 = 0` | Отклонить как сингулярную (`REJECT_SINGULAR`) | профиль не активируется |
| Профиль с рациональным 2-кручением | кубика имеет рациональный корень | Отклонить как риск малой структуры (`REJECT_SMALL_TORSION_RISK`) | нет сессии/сообщения |
| Индикатор 3-кручения | найден кандидат `psi_3` | Отклонить или отправить на эталонную проверку | причина записана |
| Профиль вне локального бюджета | коэффициенты превышают бюджет локальной диагностики | Ограничено по размеру/сложности (`SIZE_GUARDED`) | нет автоматического допуска |
| Некорректный профиль | неверные или отсутствующие `a,b` | Отклонить до допуска | нет контакта/сессии |
| Понижение до слабого профиля | peer пытается заменить сохранённый профиль слабым | Несовпадение/отклонение профиля | нет downgrade |
| Несовпадение профиля в packet | хэш packet profile не совпадает с session hash | inbox отклоняет packet | сообщение не создаётся |

### 11.2 Metrics

Minimum metrics:

```text
injected_profiles_total
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
native_backend_version
profile_policy_version
```

Dissertation-friendly derived metrics:

```text
weak_profile_accept_rate_without_precheck
weak_profile_accept_rate_discriminant_only
weak_profile_accept_rate_adamova_gate
absolute_reduction_vs_no_precheck
absolute_reduction_vs_discriminant_only
reference_required_rate
guarded_rate
```

Нельзя называть эти метрики "доказательством промышленной криптостойкости".
Корректная формулировка: “диагностика кручения рациональных кривых снижает число автоматически
принятых слабых экспериментальных профилей в заданной модели атаки и переводит
непроверяемые локально случаи в эталонную проверку”.

## 12. Claim boundary

### 12.1 Разрешённые формулировки

Можно писать:

> Диагностика кручения рациональных кривых используется как C++ контур допуска экспериментального
> криптографического профиля Kraken.

Можно писать:

> Контур допуска блокирует структурно слабые, сингулярные или требующие
> эталонной проверки параметры до создания сессии и отправки сообщений.

Можно писать:

> В контролируемой модели атаки подмены параметров диагностика кручения рациональных кривых снижает
> число принятых слабых экспериментальных профилей по сравнению с режимами без
> проверки и с проверкой только дискриминанта.

Можно писать:

> Стандартный профиль использует проверенные примитивы; диагностика кручения рациональных кривых для
> него не применяется (`NOT_APPLICABLE_STANDARD_PROFILE`).

### 12.2 Запрещённые формулировки

Нельзя писать:

- `Диагностика кручения рациональных кривых доказывает промышленную криптостойкость сообщений`.
- `Диагностика кручения усиливает X25519/Ed25519/AEAD`.
- `Рациональная диагностика над Q доказывает безопасность finite-field ECC`.
- `Bluetooth proximity доказывает личность peer-а`.
- `Диагностика кручения заменяет подписи, AEAD, Keystore или replay protection`.
- `Kraken production secure messenger`.
- `Пакеты подписаны`, если активен `prototype-placeholder`.

### 12.3 Точная диссертационная формулировка

Рекомендуемый текст:

> В прототипе Kraken диагностика кручения рациональных кривых интегрируется как native C++-контур
> допуска экспериментального криптографического профиля. Перед использованием
> такого профиля в сессии выполняется диагностика параметров рациональной
> эллиптической кривой над `Q`; структурно слабые, сингулярные,
> неподдержанные или требующие эталонной проверки параметры не допускаются
> автоматически. Данная проверка не заменяет промышленную криптографию
> сообщений, но демонстрирует полезность контура допуска при защите
> экспериментального профиля от подмены слабых параметров.

## 13. Что надо доказать реализацией

Для developers/tests:

1. `KrakenCryptoProfile` существует и сериализуется.
2. `STANDARD_REVIEWED_PRIMITIVES` не блокируется диагностикой кручения рациональных кривых.
3. `EXPERIMENTAL_ADAMOVA_CURVE_PROFILE` не допускается без C++ решения.
4. `ProductCryptoAdmissionGate` вызывает `NativeCoreBridge`.
5. `NATIVE_UNAVAILABLE` для экспериментального профиля означает закрытие без допуска.
6. “Ограничено по размеру/сложности” (`SIZE_GUARDED`) не считается успехом.
7. `REFERENCE_VALIDATION_REQUIRED` не активирует message-capable session без
   reference policy.
8. Решение допуска сохраняется в `CryptoProfileAdmissionStore`.
9. Кэш инвалидируется по backend/policy/profile hash.
10. QR/BLE/LAN handshake не делает contact `ACTIVE` с rejected profile.
11. Outbox не создаёт packet без допустимого session profile.
12. Inbox отклоняет packet с mismatch/unknown/rejected profile.
13. Демонстрация атакующих сценариев создаёт воспроизводимый отчёт.
14. UI/evidence не содержит запрещённых утверждений.

Для dissertation evidence:

1. Описан corpus слабых и контрольных profiles.
2. Есть режимы `no_precheck`, `discriminant_only`, `adamova_gate`.
3. Есть JSON/CSV/Markdown report.
4. Указаны версии native backend и admission policy.
5. Указаны latency metrics.
6. Указаны ограничения.
7. Есть сравнение acceptance rate.
8. Есть чёткая граница утверждения.

## 14. Минимальный implementation batch

Рекомендуемый первый batch:

1. `KrakenCryptoProfile`.
2. `AdamovaAdmissionDecision`.
3. `ProductCryptoAdmissionGate`.
4. `CryptoProfileAdmissionStore`.
5. `cryptoProfileId` и `admissionDecisionHash` в `KrakenPacket`.
6. Outbox enforcement.
7. Inbox enforcement.
8. Отчёт по демонстрации атакующих сценариев.

Не включать в этот batch:

- полноценный Android Keystore;
- production packet signatures;
- production encryption;
- replacement of standard primitives;
- физические утверждения о Wi-Fi Direct/BLE;
- "secure messenger" wording.

## 15. Вопросы к специалистам

1. Корректно ли использовать диагностику рациональных кривых над `Q` как
   контур допуска для экспериментального криптографического профиля?
2. Должен ли риск малой структуры (`REJECT_SMALL_TORSION_RISK`) быть жёстким
   отклонением или поводом для эталонной проверки?
3. Должно ли состояние “ограничено по размеру/сложности” всегда блокировать
   автоматический допуск?
4. Можно ли считать состояние “требуется эталонная проверка” допустимым только
   для диагностического отчёта, но не для message-capable session?
5. Должны ли обе стороны независимо пересчитывать решение диагностики кручения рациональных кривых при
   handshake?
6. Как обрабатывать mismatch `nativeBackendVersion`/`profilePolicyVersion`?
7. Достаточно ли хранить в packet `admissionDecisionHash`, а полный отчёт
   держать локально?
8. Как лучше формализовать модель атаки: `weak profile injection`,
   `parameter substitution`, `downgrade to weak research profile`?
9. Какие утверждения допустимы для диссертации без доказательства промышленной
   криптостойкости сообщений?
10. Нужно ли обязательное SageMath confirmation для всех принятых
    экспериментальных профилей в демонстрационном наборе?

## 16. Developer checklist

Перед merge implementation:

- [ ] Нет Android UI текста “диагностика кручения защищает каждое сообщение”.
- [ ] Нет утверждения, что standard profile усилен диагностикой кручения рациональных кривых.
- [x] `KrakenPacket` не хранит full curve report.
- [ ] `proofMode = prototype-placeholder` не называется подписью.
- [x] `NATIVE_UNAVAILABLE` не даёт включить экспериментальный профиль.
- [x] “Ограничено по размеру/сложности” (`SIZE_GUARDED`) не считается `ACCEPT`.
- [ ] В отчёте есть `nativeBackendVersion`.
- [ ] В отчёте есть `profilePolicyVersion`.
- [x] В диагностическом отчёте есть оговорка, что это не доказательство
  промышленной криптостойкости сообщений.
- [x] Tests проверяют отклонение слабого профиля.
- [x] Tests проверяют handshake rejection для неизвестного экспериментального профиля.
- [x] Tests проверяют packet profile mismatch rejection.

## 17. Relation to existing documents

Связанные документы:

- `docs/adamova-product-crypto-integration-consultation.md` - широкий
  консультационный документ с вариантами интеграции и вопросами.
- `docs/kraken-attack-scenarios-evidence.md` - текущая матрица атак и evidence
  boundary.
- `reports/out/adamova_admission_gate_attack_demo.md` - текущий unit-backed
  evidence report по результату алгоритма в криптографическом контуре.
- `reports/out/adamova_admission_gate_dissertation_evidence_plan.md` -
  диссертационный план метрик и формулировок.
- `docs/kraken-crypto-implementation-plan.md` - общий план перехода от
  prototype crypto к production-ready crypto architecture.
- `docs/kraken-native-core-boundary.md` - boundary native core; обновлён после
  подключения контура допуска диагностики кручения рациональных кривых, потому что native core уже не
  только status placeholder.

## 18. Финальная позиция

Финальная позиция для разработки:

> C++ backend диагностики кручения рациональных кривых должен быть обязательным контуром допуска для
> экспериментального криптографического профиля на параметрах кривой.
> Стандартный профиль остаётся на проверенных примитивах и не зависит от
> диагностики кручения рациональных кривых. QR/BLE/LAN-сопряжение, session profile и packet policy
> должны ссылаться на результат допуска через `cryptoProfileId`, `profileHash`
> и `admissionDecisionHash`. Диагностический отчёт должен демонстрировать атаку
> подмены слабого профиля и показывать, что контур допуска блокирует профиль до
> сессии/сообщений. Утверждения ограничиваются защитой экспериментального
> профиля от слабых параметров и не распространяются на доказательство
> промышленной криптографической безопасности.
