# План реализации криптографического контура Kraken

Статус: актуальный рабочий план без UI-задач.

Цель документа - описать, как в Kraken разделяются:

- транспортная доставка пакетов;
- доверие между участниками;
- контур допуска криптографического профиля;
- будущие промышленные подписи и шифрование.

В человекочитаемых документах используем русские термины. Технические имена
enum/API допускаются в скобках, когда важно связать текст с кодом.

## Словарь

| Техническое имя | Русская формулировка |
| --- | --- |
| `ProductCryptoAdmissionGate` | контур допуска криптографического профиля |
| “контур диагностики кручения” | краткая форма для диагностики кручения рациональных кривых |
| `SIZE_GUARDED` | ограничено по размеру/сложности; требуется эталонная проверка |
| `REFERENCE_VALIDATION_REQUIRED` | требуется эталонная проверка |
| `EXPERIMENTAL_ADAMOVA_CURVE_PROFILE` | экспериментальный профиль на параметрах рациональной кривой |
| `STANDARD_REVIEWED_PRIMITIVES` | стандартный профиль на проверенных примитивах |
| `NOT_APPLICABLE_STANDARD_PROFILE` | диагностика кручения не применяется к стандартному профилю |
| `admissionDecisionHash` | хэш решения допуска |
| `profilePolicyVersion` | версия политики допуска |

## Текущее состояние

- Профиль Kraken использует compatibility key reference до миграции в Android Keystore.
- QR-сопряжение содержит служебные proof-поля; цифровые подписи вынесены в отдельный слой.
- Пакет `KrakenPacket` сохраняет `proofMode = prototype-placeholder` как маркер старого packet path.
- Legacy payload сообщений ещё может оставаться plain JSON только в
  debug/compatibility режиме.
- Android Keystore для неэкспортируемых ключей ещё не внедрён.
- Подпись сообщений вынесена в отдельный следующий слой.
- В runtime включён ключ сообщения от QR-сопряжения: обе стороны берут общий
  `sourceInviteId`, relationship/session/profile context и получают ключ через
  HKDF-SHA256.
- Полноценный public-key key agreement и rotation ещё не внедрены.
- AEAD-контур добавлен как Android/JCA AES-GCM provider.
- Для защищённого packet path добавлен `ENCRYPTED_MESSAGE_JSON`: outbox может
  запечатывать message payload через `AdamovaMessagePayloadProtector`, а inbox
  открывает payload только после восстановления `AdamovaPacketCryptoBinding`
  из `KrakenPacket + Relationship`.
- Обычный runtime `MeshService` теперь запускает message path в режиме
  `ADAMOVA_ENCRYPTED_REQUIRED`: исходящий message packet становится
  `ENCRYPTED_MESSAGE_JSON`, а inbound plaintext message packet отклоняется.
- Legacy/debug path `LOCAL_MESSAGE_JSON` пока сохранён для обратной совместимости
  и старых transport/fixture тестов, но должен включаться явно.
- Диагностика кручения рациональных кривых уже участвует в криптографическом контуре как проверка
  допуска экспериментального профиля до сессии и до packet policy.
- Решение допуска теперь также связывается с crypto envelope через
  `AdamovaBoundCryptoEnvelope`: `cryptoProfileId`, `profileHash`,
  `admissionDecisionHash`, `profilePolicyVersion`, `nativeBackendVersion`,
  `sessionProfileId` и `relationshipId` входят в AEAD associated data.

## Что уже делает диагностика кручения

Диагностика кручения рациональных кривых не шифрует сообщения и не заменяет проверенные примитивы.
Её роль уже сейчас продуктовая, но узкая:

```text
параметры экспериментального профиля
  -> нативная C++ диагностика кручения рациональных кривых
  -> решение допуска
  -> хэш решения допуска
  -> привязка к relationship/session/packet
  -> Adamova-bound crypto context
  -> AEAD associated data
  -> ENCRYPTED_MESSAGE_JSON
  -> разрешение или блокировка seal/open и отправки/приёма packet
```

Практический результат:

- сингулярный профиль не допускается;
- профиль с признаками нежелательной малой структуры не допускается;
- профиль, который локально ограничен по размеру/сложности, не считается
  успешно проверенным и переводится в состояние эталонной проверки;
- неизвестный или подменённый профиль не должен становиться message-capable;
- packet с профилем, не совпадающим с relationship/session binding, должен
  отклоняться до создания сообщения;
- подмена `admissionDecisionHash` или `sessionProfileId` должна ломать
  crypto-context до расшифровки payload.
- защищённый message packet не должен содержать plaintext body в `payloadJson`.

Это вклад диагностики кручения в admission/context-binding path Kraken: она не
заменяет AEAD, но задаёт обязательную политику допуска и контекстную привязку
для криптографического envelope. Payload может быть открыт только в том же
relationship/session/profile context, который прошёл локальную проверку.

## Транспортный план без UI

1. Закрыть текущий Wi-Fi Direct endpoint-binding WIP.
2. Сохранять инвариант: `fingerprint` - стабильная identity, endpoint - текущий
   transport address/session.
3. Не считать peer отправляемым, пока transport endpoint не подтверждён.
4. Заменить неинформативный `UNKNOWN_PEER` в transport path на stage-specific
   причины: relationship есть, TXT не найден, device виден без binding,
   connect failed, group-owner route missing.
5. Проверять delivery через evidence: `endpoint_bound`, `send_attempted`,
   `message_delivery_proven`.
6. Не менять UI-ветку в рамках transport/crypto работ.

## Криптографический план

1. Сохранить диагностику кручения рациональных кривых как обязательный контур
   допуска экспериментального профиля.
2. Держать `AdamovaBoundCryptoEnvelope` обязательным boundary перед `AEAD seal/open`.
3. Усилить тесты, что rejected/unknown/ограниченный по размеру профиль не входит
   в outbox/inbox delivery.
4. Проверить, что `cryptoProfileId`, `sessionProfileId`, хэш решения допуска и
   версия политики покрывают все packet paths: message, receipt, retry.
5. Добавить отрицательные тесты для downgrade и mismatch между relationship и
   packet metadata.
6. Runtime default уже переведён на `ADAMOVA_ENCRYPTED_REQUIRED`; legacy
   `LOCAL_MESSAGE_JSON` оставлен только как явно выбранный debug/compatibility
   режим для старых fixture-тестов.
7. Отдельным этапом завершить промышленные примитивы:
   - Android Keystore для ключей identity;
   - public-key key agreement;
   - canonical serialization для QR и packet envelope;
   - подпись QR invite/response/confirmation;
   - подпись packet envelope;
   - постоянный encrypted payload format для всех transport/storage paths;
   - replay protection и key rotation;
   - разделение debug compatibility и release/prod build flavors;
   - внешний security review.

## Разрешённая формулировка

> В Kraken диагностика кручения рациональных кривых используется как нативный C++
> контур допуска экспериментального криптографического профиля и как
> обязательная контекстная привязка crypto envelope. Решение допуска входит в
> relationship/session/packet metadata и в AEAD associated data, поэтому слабый,
> подменённый или не совпадающий с локальной политикой профиль блокируется до
> отправки packet и до открытия `ENCRYPTED_MESSAGE_JSON` payload.

## Запрещённые формулировки

- “Диагностика кручения сама по себе шифрует сообщения”.
- “Диагностика кручения заменяет X25519/Ed25519/AEAD”.
- “Kraken полностью закрывает все промышленные сценарии защиты”.
- “Рациональная диагностика над `Q` доказывает стойкость схем над конечными
  полями”.
- “`DebugPlaintextPacketCrypto` является подписью или шифрованием”.

## Проверки перед сильными утверждениями

- Unit-тесты packet admission для outbox/inbox.
- Unit-тесты `AdamovaBoundCryptoEnvelope` и `AdamovaPacketCryptoBinding`.
- Unit-тесты encrypted message delivery через `ENCRYPTED_MESSAGE_JSON`.
- Unit-тесты strict payload policy, которая запрещает plaintext outbox/inbox без
  Adamova-bound protector.
- Negative tests для слабого, неизвестного, ограниченного по размеру и
  mismatched профиля.
- Negative tests для подмены `admissionDecisionHash` и `sessionProfileId`.
- Evidence report с числами accepted/rejected для контролируемой модели атаки.
- Отдельный отчёт, что Adamova-bound encrypted message path уже реализован и
  использует текущий QR-derived HKDF-контекст, а public-key agreement,
  ротация ключей, Keystore, подписи, canonical envelope, replay protection и
  внешний review закрываются отдельными слоями.
