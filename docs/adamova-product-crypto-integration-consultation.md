# Консультация по диагностике кручения рациональных кривых и продуктовой криптографии

Статус: консультационный документ для обсуждения со специалистами.
Дата: 2026-06-05.
Scope: C++ диагностика кручения рациональных кривых, текущая Android/C++
реализация Kraken, варианты встраивания в продуктовую криптографическую
политику мессенджера.

Этот документ нужен как основа для разговора со специалистами: где диагностика
кручения рациональных кривых должна участвовать в продуктовой
криптографической политике Kraken, какие варианты безопасны, какие вопросы надо
закрыть до промышленного уровня и какие claims нельзя делать. Он также фиксирует
первый implementation batch, где контур допуска уже подключён к
QR/relationship/packet policy для экспериментального криптографического профиля.

## 1. Короткий вывод

Текущий код Kraken содержит рабочий native C++ backend диагностики кручения
рациональных кривых и первый продуктовый контур допуска:

- Android загружает `libkraken_native_placeholder.so`;
- Kotlin вызывает JNI через `NativeCoreBridge`;
- C++ классифицирует кривую
  `E: y^2 = x^3 + ax + b`
  по Stage A диагностике;
- результат используется в Research Panel и mesh evidence metadata;
- `ProductCryptoAdmissionGate` допускает или блокирует experimental
  crypto profile;
- QR invite/response/confirmation несут profile/admission binding;
- `Relationship` и `KrakenPacket` несут `cryptoProfileId`,
  `admissionDecisionHash` и `profilePolicyVersion`;
- outbox/inbox проверяют profile admission до отправки/принятия packet.

Что **не** реализовано и не должно заявляться:

- контур допуска не заменяет key agreement;
- он не подписывает packet;
- он не шифрует payload;
- текущий packet crypto остаётся prototype/no-security до отдельной
  реализации подписей, шифрования и Android Keystore.

Целевое направление:

> Использовать C++ диагностику кручения рациональных кривых как deterministic
> native admission/validation engine для экспериментальных curve-based crypto
> profiles, а не как custom encryption primitive.

## 2. Что такое диагностика кручения в нашем контексте

Алгоритм работает с рациональными эллиптическими кривыми в короткой форме
Вейерштрасса:

```text
E_{a,b}: y^2 = x^3 + ax + b
```

Рабочее поле исследования:

```text
Q, рациональные кривые, целые коэффициенты a и b
```

Это принципиально отличается от production ECC над конечными полями. Поэтому
результаты алгоритма нельзя напрямую формулировать как доказательство
промышленной стойкости ECDH/ECDSA/EdDSA/Noise-like протокола.

В нашей постановке алгоритм полезен как:

- быстрый предварительный диагностический фильтр параметров;
- способ отбраковки сингулярных и структурно подозрительных кривых;
- воспроизводимый research/evidence слой;
- admission policy для экспериментального curve-profile;
- инструмент, который может сказать не только `pass/reject`, но и
  `needs_reference_validation`.

## 3. Stage A: математическая логика

### 3.1 Невырожденность

Для кривой

```text
y^2 = x^3 + ax + b
```

проверяется дискриминантный критерий:

```text
4a^3 + 27b^2 != 0
```

В C++ коде дополнительно считается полный дискриминант:

```text
Delta = -16(4a^3 + 27b^2)
```

Если дискриминант равен нулю, кривая сингулярна и должна быть отклонена для
любого криптографического профиля.

### 3.2 Рациональная 2-торсия

Точки порядка 2 имеют `y = 0`, поэтому нужно искать рациональные корни:

```text
x^3 + ax + b = 0
```

Так как многочлен монический с целыми коэффициентами, рациональные корни
являются целыми. Поэтому алгоритм строит кандидаты из делителей `b` и проверяет
их точно.

Особый случай:

```text
b = 0
```

тогда `x = 0` является корнем, а при `-a` как квадрате могут появляться ещё
корни. Для демонстрационных и production-like примеров это надо формулировать
аккуратно: `b = 0` не означает автоматически сингулярность, но часто даёт
очевидную 2-торсионную структуру, поэтому такие кривые нежелательны как
"сильные" примеры.

### 3.3 Индикатор 3-торсии

Используется третья делительная функция:

```text
psi_3(x) = 3x^4 + 6a x^2 + 12b x - a^2
```

Алгоритм ищет целочисленные кандидаты `x`, для которых:

```text
psi_3(x) = 0
```

и дополнительно проверяет, что

```text
y^2 = x^3 + ax + b
```

даёт квадрат. Это не "полная теория торсионной подгруппы", а быстрый
диагностический индикатор малой структуры.

### 3.4 Классификация A1-A6

После подсчёта корней для 2-торсии и признака 3-торсии алгоритм относит кривую
к диагностическому случаю:

| Case | Условие | Интерпретация в diagnostic layer |
| --- | --- | --- |
| `A1` | 3-indicator есть, 2-torsion roots = 0 | возможные нечётные 3-related torsion cases |
| `A2` | 3-indicator есть, 2-torsion roots = 1 | mixed 2/3 structure candidate |
| `A3` | 3-indicator есть, 2-torsion roots = 3 | full 2-torsion plus 3-related indicator |
| `A4` | 3-indicator нет, 2-torsion roots = 0 | no obvious 2/3 small torsion indicator |
| `A5` | 3-indicator нет, 2-torsion roots = 1 | one rational 2-torsion root |
| `A6` | 3-indicator нет, 2-torsion roots = 3 | full rational 2-torsion pattern |
| `SINGULAR` | дискриминант = 0 | rejection |
| `SIZE_GUARDED` | локальная точная диагностика не завершена | reference validation required |

Важно: `A1-A6` должны трактоваться как diagnostic cases, а не как final proof
of production cryptographic suitability.

## 4. Текущая C/C++ реализация

Ключевые файлы:

```text
app-android/app/src/main/cpp/kraken_native_placeholder.cpp
app-android/app/src/main/cpp/CMakeLists.txt
app-android/app/src/main/java/com/disser/kraken/nativecore/NativeCoreBridge.kt
app-android/app/src/main/java/com/disser/kraken/research/ResearchModels.kt
```

Название `kraken_native_placeholder.cpp` исторически неудачное: фактически там
уже не просто placeholder, а C++ research core для Adamova Stage A diagnostics.
При следующем cleanup логично переименовать библиотеку и файл, например:

```text
kraken_adamova_core.cpp
libkraken_adamova_core.so
```

### 4.1 JNI boundary

Kotlin API:

```kotlin
NativeCoreBridge.classifyAdamovaOrNull(a: BigInteger, b: BigInteger): NativeAdamovaResult?
```

JNI exports:

```cpp
getNativeCoreStatus()
classifyAdamovaV3(a: Long, b: Long)
classifyAdamovaV3Decimal(a: String, b: String)
```

Сейчас основной production-candidate вызов должен быть decimal-string API,
потому что он не ограничен Kotlin `Long`.

### 4.2 Диапазоны

В C++ есть два пути:

1. `Int128` path:
   - быстрый путь для signed 128-bit коэффициентов;
   - факторизация/делители через `UInt128`;
   - точные BigInt операции для дискриминанта и полиномов.

2. `BigInt smooth` path:
   - decimal parser для произвольных больших коэффициентов;
   - собственный BigInt на базе `1_000_000_000`;
   - точный дискриминант;
   - divisor/factorization scan для smooth cases;
   - `SIZE_GUARDED`, если факторизация/перебор делителей выходит за бюджет.

Практический вывод:

> Для Android-продуктовой политики лучше использовать C++ path как основной
> validator, а Kotlin BigInteger path оставить fallback/debug/reference UI.

### 4.3 Что C++ возвращает

`NativeAdamovaResult` содержит:

- `a`, `b`;
- `singular`;
- `discriminant`;
- `twoTorsionRootCount`;
- `twoTorsionRoots`;
- `threeTorsionRootCount`;
- `threeTorsionRoots`;
- `hasThreeTorsionIndicator`;
- `hasThreeTorsionInconsistency`;
- `classificationCase`;
- статистику candidate/filter/exact checks;
- `divisorCountA2`;
- `factorizationSteps`;
- `xSquare`;
- `earlyStopHit`.

Это достаточно богато для product policy, потому что можно принимать решения не
только по `case`, но и по тому, насколько диагностика была полной.

### 4.4 Optimization details

В реализации уже есть важные ускорения:

- modular prefilters для `psi_3` по модулям `5, 7, 11`;
- bound для non-root candidates в `Int128` path;
- bounded divisor enumeration;
- early `SIZE_GUARDED`, когда точный локальный scan становится слишком дорогим;
- statistics для объяснения, почему результат получен быстро или почему он
  guarded.

### 4.5 Что C++ НЕ делает

C++ Adamova core сейчас не делает:

- key agreement;
- ECDH;
- подпись;
- AEAD;
- packet encryption;
- packet signing;
- Android Keystore;
- сетевой transport;
- production finite-field ECC validation.

Это хорошо: C++ должен оставаться маленьким deterministic validator, а не
разрастаться в самодельную криптобиблиотеку.

## 5. Почему использовать C++ core, а не Kotlin fallback

Короткий ответ: Kotlin fallback полезен как insurance/debug, но product policy
должна опираться на C++ implementation.

Причины:

1. **Производительность.** Native path уже создавался как быстрый Stage A v3
   backend; Kotlin fallback содержит более простые ограничения и исторически
   был MVP.

2. **Единая реализация.** Если product admission будет зависеть от Kotlin
   fallback, появится риск расхождения между Research Panel, native benchmark и
   mesh policy.

3. **Более богатая диагностика.** C++ возвращает detailed stats, roots,
   guarded state и backend-specific counters.

4. **Лучше для портирования.** C++ validator можно позднее использовать не
   только в Android, но и в desktop/simulator tooling.

5. **Чёткая boundary.** C++ может быть pure deterministic module:
   input coefficients -> diagnostic/admission result.

Ограничение:

> C++ core должен оставаться validator/admission engine. Не надо переносить в
> него UI, Android lifecycle, networking и custom encryption.

## 6. Текущее место алгоритма в приложении

Фактически сейчас:

- Research Panel вызывает диагностику кручения рациональных кривых;
- Mesh evidence exporter содержит metadata контура допуска;
- product packet crypto остаётся prototype/no-security;
- `KrakenPacket` содержит `cryptoProfileId`, `sessionProfileId`,
  `admissionDecisionHash` и `profilePolicyVersion`;
- `MeshOutboxProcessor` спрашивает контур допуска перед отправкой
  experimental profile;
- `MeshInboxProcessor` проверяет profile/admission binding на входе;
- QR response/confirmation не активируют relationship с unknown/rejected
  experimental profile.

Текущее состояние можно описывать так:

> Adamova diagnostics are implemented as native research diagnostics,
> evidence metadata, and the first product cryptographic admission policy for
> experimental profiles. This is still not production encryption or a reviewed
> secure messenger protocol.

Это важно честно сказать специалистам: алгоритм уже участвует в policy/gating,
но не является шифром, подписью или промышленным доказательством стойкости.

## 7. Что значит "интегрировать в продуктовую криптографию"

Есть несколько уровней интеграции.

### Level 0 — Research-only

Исторический baseline, уже недостаточный для текущей цели.

Алгоритм:

- живёт в Research Panel;
- создаёт evidence;
- не влияет на отправку сообщений.

Плюс:

- безопасно по claims;
- низкий риск сломать messenger.

Минус:

- не удовлетворяет твоей цели;
- алгоритм не участвует в продуктовой криптографии.

### Level 1 — Product Admission Gate

Алгоритм проверяет experimental crypto profile до его использования.

Идея:

```text
CurveCryptoProfile(a,b,profileId,evidenceRef)
        -> C++ Adamova diagnostics
        -> ProductCryptoAdmissionDecision
        -> APPROVED / REJECTED / REFERENCE_REQUIRED
```

Где применяется:

- перед созданием experimental session profile;
- перед отправкой message packet;
- при приёме packet с `cryptoProfileId`;
- при отображении evidence.

Плюс:

- алгоритм реально участвует в product policy;
- не притворяется encryption primitive;
- удобно защищать в диссертации.

Минус:

- это всё ещё не production crypto proof;
- нужно аккуратно связать rational `Q` diagnostics и actual crypto profile.

Рекомендация: **первый реальный integration target**.

### Level 2 — Handshake/Profile Binding

При QR-сопряжении стороны договариваются о crypto profile:

```text
QR invite -> includes supported profile ids / curve-policy version
nearby handshake -> confirms chosen profile
session metadata -> stores admitted profile
packet -> references profile id
```

Контур допуска проверяет выбранный profile до активации или до первой отправки.

Плюс:

- profile становится частью trust establishment;
- проще объяснить, почему packet принадлежит approved profile.

Минус:

- нужно версионирование;
- нужен canonical profile serialization;
- без подписей это всё ещё prototype.

### Level 3 — Packet-Level Enforcement

Каждый packet несёт:

```text
crypto_profile_id
crypto_profile_hash
admission_version
admission_decision
```

На отправке:

- если profile не approved -> packet не создаётся.

На приёме:

- если profile неизвестен/подменён/не approved -> reject.

Плюс:

- attack scenarios становятся ясными:
  - weak profile injection;
  - profile substitution;
  - missing reference evidence;
  - unsupported/guarded profile.

Минус:

- без signature/integrity layer attacker всё ещё может менять metadata;
- поэтому желательно совмещать с signed envelope или хотя бы с explicit
  prototype boundary.

### Level 4 — Real Crypto Envelope Integration

Контур допуска становится частью политики допуска experimental curve-based
crypto, а actual message security делает нормальный crypto envelope:

- Android Keystore;
- reviewed key agreement;
- signed handshake;
- AEAD;
- replay protection;
- key rotation.

Adamova не шифрует, а допускает/блокирует experimental curve profile.

Плюс:

- правильная архитектура;
- можно честно сказать, где research contribution, а где standard crypto.

Минус:

- существенно больше работы;
- нужен crypto review.

## 8. Варианты, куда именно интегрировать

| Вариант | Где интегрировать | Что делает Adamova | Плюсы | Риски |
| --- | --- | --- | --- | --- |
| A | Research Panel only | показывает диагностику | уже есть | не product crypto |
| B | Settings -> Crypto profile | проверяет выбранный profile | простой UX, хорош для demo | weak binding к session |
| C | QR handshake | не даёт активировать profile без допуска | сильная связь с trust setup | сложнее QR payload/schema |
| D | Message send pipeline | блокирует outgoing packets с плохим profile | прямое участие в доставке | без signed metadata неполно |
| E | Incoming packet gate | reject unknown/unapproved profile | хорошие attack scenarios | нужна profile registry |
| F | Session/key agreement policy | profile участвует в session construction | ближе к "криптографии" | нужен настоящий crypto design |
| G | Native C++ shared validator | единый engine для Android/simulator | меньше drift | нужен стабильный ABI/schema |

Практичная комбинация:

```text
C + D + E + G
```

То есть:

1. profile выбирается/фиксируется при QR handshake;
2. C++ validator допускает или отклоняет profile;
3. outgoing packet требует approved profile;
4. incoming packet требует known approved profile;
5. validator остаётся native C++ shared core.

## 9. Вопросы к специалистам и варианты ответов

### Q1. Можно ли вообще использовать rational curve diagnostics over Q в продуктовой crypto policy?

Варианты:

- **A. Да, как research admission/diagnostic layer.**
  Тогда claims ограничиваются фильтрацией/диагностикой, а не production security.

- **B. Да, но только как metadata/evidence, не как runtime gate.**
  Это был бы более осторожный альтернативный режим, но он слабее для
  диссертации и уже не описывает текущий Kraken batch, где gate участвует в
  QR/relationship/packet policy для experimental profile.

- **C. Нет, нельзя связывать с product crypto.**
  При таком запрете диагностику кручения пришлось бы оставить только в Research
  Panel/evidence, без участия в messenger crypto policy; это не текущая
  выбранная позиция Kraken.

Предпочтительный ответ для Kraken: **A**, при строгом claim boundary.

### Q2. Должен ли контур допуска блокировать отправку сообщений?

Варианты:

- **A. Да, если включён experimental curve profile.**
  Standard safe profile работает без этой диагностики; experimental profile требует
  admission.

- **B. Да всегда.**
  Рискованно, потому что standard modern crypto может не иметь rational
  curve parameters.

- **C. Нет, только предупреждать.**
  Слабый вариант для product integration.

Рекомендация: **A**.

### Q3. Что делать с `SIZE_GUARDED`?

Варианты:

- **A. Reject by default.**
  Максимально безопасно, но может отбрасывать сложные валидные cases.

- **B. Require Sage/reference validation.**
  Лучший research вариант: не врём, но сохраняем возможность допуска.

- **C. Allow with warning.**
  Нежелательно для product policy.

Рекомендация: **B** для research prototype, **A** для release-like mode.

### Q4. Нужен ли `cryptoProfileId` в packet?

Варианты:

- **A. Да, обязательно.**
  Позволяет проверять, какой profile использован.

- **B. Только в session state, не в каждом packet.**
  Меньше overhead, но сложнее debug/evidence.

- **C. Нет.**
  Тогда attack scenarios profile substitution плохо формализуются.

Рекомендация: **A** для research prototype.

### Q5. Нужен ли hash/canonical serialization profile?

Варианты:

- **A. Да: `profile_hash = SHA-256(canonical_profile_json)`.**
  Минимально нужна трассируемость и защита от accidental mismatch.

- **B. Только human-readable id.**
  Удобно, но слабее.

- **C. Только asset path.**
  Непригодно для packet/session binding.

Рекомендация: **A**.

### Q6. Должен ли C++ validator сам возвращать `APPROVED/REJECTED`?

Варианты:

- **A. C++ возвращает diagnostics, Kotlin policy принимает решение.**
  Гибко, удобно для UI и тестов.

- **B. C++ возвращает уже готовое admission decision.**
  Меньше drift, но policy harder to evolve.

- **C. Дублировать policy и там, и там.**
  Плохо: риск рассинхронизации.

Рекомендация: **A** сейчас, возможно **B** позже для shared core.

### Q7. Где хранить approved profiles?

Варианты:

- **A. Bundled JSON registry в assets.**
  Хорошо для dissertation/demo.

- **B. Hardcoded Kotlin constants.**
  Быстро, но плохо масштабируется.

- **C. Dynamic import over network.**
  Сейчас нельзя: нет cloud/server assumptions.

Рекомендация: **A**.

### Q8. Как связать Sage evidence?

Варианты:

- **A. Profile требует `SageMath direct match` для admission.**
  Сильный research-evidence вариант.

- **B. Sage optional.**
  Слабее, но проще.

- **C. Sage runtime на Android.**
  Нельзя: тяжело и не нужно.

Рекомендация: **A** для curated research profiles.

### Q9. Можно ли использовать Adamova для standard production crypto?

Варианты:

- **A. Нет, standard profile bypasses Adamova as NOT_APPLICABLE.**
  Самый честный вариант.

- **B. Да, адаптировать к finite-field curve validation.**
  Это уже другая математическая задача, не текущий алгоритм.

- **C. Притвориться, что rational diagnostics применимы напрямую.**
  Нельзя.

Рекомендация: **A**.

### Q10. Какой лучший dissertation claim?

Варианты:

- **A. Algorithm participates in product crypto admission policy for
  experimental curve profiles.**
  Хорошо и честно.

- **B. Algorithm proves Kraken messages are cryptographically secure.**
  Нельзя.

- **C. Algorithm accelerates all modern cryptosystems.**
  Нельзя.

Рекомендация: **A**.

## 10. Предлагаемая архитектура интеграции

### 10.1 Data model

```kotlin
data class KrakenCryptoProfile(
    val profileId: String,
    val profileVersion: Int,
    val profileKind: CryptoProfileKind,
    val curveA: BigInteger?,
    val curveB: BigInteger?,
    val profileHash: String,
    val evidenceAssetPath: String?,
    val requiredReferenceStatus: String?,
)

enum class CryptoProfileKind {
    STANDARD_REVIEWED_PRIMITIVES,
    EXPERIMENTAL_ADAMOVA_CURVE_PROFILE,
}
```

### 10.2 Admission result

```kotlin
enum class AdamovaAdmissionDecision {
    APPROVED_FOR_RESEARCH_PROFILE,
    REJECTED_SINGULAR,
    REJECTED_SMALL_TORSION_RISK,
    REFERENCE_VALIDATION_REQUIRED,
    NOT_APPLICABLE_STANDARD_PROFILE,
}
```

### 10.3 Native call boundary

```kotlin
interface AdamovaNativeValidator {
    fun classify(a: BigInteger, b: BigInteger): NativeAdamovaResult?
}
```

Policy:

```kotlin
class ProductCryptoAdmissionGate(
    private val validator: AdamovaNativeValidator,
    private val registry: CryptoProfileRegistry,
) {
    fun evaluate(profile: KrakenCryptoProfile): ProductCryptoAdmissionResult
}
```

### 10.4 Packet/session metadata

```kotlin
data class KrakenPacket(
    ...
    val cryptoProfileId: String?,
    val cryptoProfileHash: String?,
    val admissionVersion: Int?,
)
```

### 10.5 Enforcement points

Outgoing:

```text
LocalMessage
  -> relationship ACTIVE?
  -> session/profile exists?
  -> profile approved by ProductCryptoAdmissionGate?
  -> create packet
```

Incoming:

```text
KrakenPacket
  -> packet parse/ttl/duplicate checks
  -> relationship ACTIVE?
  -> profile id/hash known?
  -> profile approved?
  -> accept message / reject
```

QR handshake:

```text
Invite
  -> supported profile ids
Response
  -> selected profile id/hash
Confirmation
  -> profile binding accepted
```

## 11. Attack scenarios where Adamova looks strong

### Scenario 1 — Singular curve injection

Attacker proposes:

```text
4a^3 + 27b^2 = 0
```

Expected:

```text
Adamova C++ -> SINGULAR -> reject profile before session/message use
```

Good claim:

> The admission layer rejects structurally invalid curve parameters before they
> can become an experimental crypto profile.

### Scenario 2 — Obvious rational 2-torsion profile

Attacker proposes curve with easy roots of:

```text
x^3 + ax + b = 0
```

Expected:

```text
twoTorsionRootCount > 0
classificationCase A5/A6 or mixed A2/A3
policy may reject or mark as not acceptable for selected profile class
```

### Scenario 3 — 3-torsion indicator profile

Attacker proposes curve where:

```text
psi_3(x) = 0
```

and squarecheck passes.

Expected:

```text
hasThreeTorsionIndicator = true
classificationCase A1/A2/A3
policy rejects or requires reference validation
```

### Scenario 4 — Oversized unsupported profile

Attacker proposes huge coefficients designed to exhaust factorization/divisor
scan.

Expected:

```text
C++ exact discriminant computed
classificationCase = SIZE_GUARDED
policy = REFERENCE_VALIDATION_REQUIRED or reject
```

This is strong because the app does not pretend success.

### Scenario 5 — Profile substitution

Packet/session claims approved `profileId`, but coefficient/hash differs.

Expected after integration:

```text
profile_hash mismatch -> reject
```

Requires:

- canonical profile serialization;
- packet/session metadata;
- signed/protected envelope later.

### Scenario 6 — Baseline comparison

Compare:

```text
no_precheck
discriminant_only
Adamova/Kraken precheck
Sage reference
```

Expected:

```text
Adamova rejects/flags more risky profiles before reference stage
```

Safe claim:

> Under the defined generated/injected profile model, Adamova-based precheck
> reduces false acceptance of structurally risky curve parameters compared with
> weaker baselines.

## 12. Что спросить у специалистов

1. Корректно ли использовать рациональную диагностику over `Q` как admission
   policy для experimental curve profile, если мы явно не называем это
   production ECC proof?

2. Какие A-cases считать rejection для экспериментального профиля, а какие
   можно считать warning/reference-required?

3. Должны ли мы reject любые 2-torsion/3-torsion indicators или только
   отдельные classes?

4. Нужен ли обязательный SageMath direct match для всех profiles, которые
   попадают в Android app?

5. Как лучше формализовать `SIZE_GUARDED`: reject, reference required или
   allowed only in research mode?

6. Где лучше фиксировать profile binding: в QR invite, в confirmation, в
   session state или в каждом packet?

7. Нужно ли `cryptoProfileHash` включать в будущую подпись packet envelope?

8. Можно ли в диссертации говорить "участвует в продуктовой криптографической
   политике", если actual encryption делает reviewed primitive, а Adamova
   допускает только experimental profile?

9. Нужно ли переименовать `kraken_native_placeholder` перед демонстрацией, чтобы
   не выглядело как заглушка?

10. Какие attack scenarios убедительнее для комиссии: singular/2-torsion,
    profile substitution, guarded oversized input или baseline simulation?

## 13. Минимальный implementation batch после консультации

Если специалисты подтверждают Level 1-3 integration:

1. Переименовать native core:

```text
kraken_native_placeholder -> kraken_adamova_core
```

2. Добавить:

```text
KrakenCryptoProfile
CryptoProfileRegistry
ProductCryptoAdmissionGate
AdamovaAdmissionDecision
```

3. Добавить asset registry:

```text
app-android/app/src/main/assets/research/crypto_profiles/...
```

4. Добавить packet/session metadata:

```text
cryptoProfileId
cryptoProfileHash
admissionVersion
```

5. Встроить gate:

- outgoing message path;
- incoming packet path;
- QR/session profile binding.

6. Добавить tests:

- singular profile rejected;
- 2-torsion profile rejected/warned according to policy;
- 3-torsion indicator profile rejected/reference-required;
- `SIZE_GUARDED` requires reference;
- approved profile accepted;
- missing/unknown profile rejected;
- profile hash mismatch rejected;
- standard reviewed profile returns `NOT_APPLICABLE`.

7. Обновить docs/evidence:

- `docs/kraken-attack-scenarios-evidence.md`;
- `docs/kraken-crypto-implementation-plan.md`;
- dissertation prompt/materials.

## 14. Рекомендуемая финальная позиция

Я бы предлагал специалистам такую архитектурную позицию:

> В Kraken диагностика кручения рациональных кривых должна быть не шифром и не
> заменой современным reviewed primitives, а native C++ admission engine для
> экспериментальных curve-based crypto profiles. Она должна блокировать
> структурно рискованные или неподтверждённые параметры до использования профиля
> в handshake/session/packet policy. Standard production primitives должны идти
> отдельным безопасным профилем, где диагностика возвращает `NOT_APPLICABLE`, а
> не притворяется доказательством безопасности.

Это даёт сильную диссертационную формулировку:

> Диагностика кручения рациональных кривых интегрирована в продуктовую
> криптографическую политику как воспроизводимый контур допуска для
> экспериментальных параметров кривой; она повышает контролируемость выбора
> параметров в research-profile, но не заявляется как самостоятельное
> доказательство промышленной криптостойкости.
