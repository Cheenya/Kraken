# Граница native core Kraken

## Назначение

Документ фиксирует границу между Android/Kotlin и C++ native core. Native
library содержит backend диагностики кручения рациональных кривых, который
используется в контуре допуска экспериментального криптографического профиля.

Native core нельзя трактовать как полный протокол, transport runtime,
промышленную криптографию или реализацию доставки сообщений.

## Текущая область

- Android загружает `libkraken_native_placeholder.so`.
- Kotlin обращается к native status и диагностике кручения рациональных кривых через
  `NativeCoreBridge`.
- CMake сохраняет compatibility flag для 16 KB page size:
  `-Wl,-z,max-page-size=16384`.
- Native boundary отдаёт status call и детерминированную классификацию
  параметров рациональной кривой.
- `ProductCryptoAdmissionGate` - техническое имя Kotlin API для контура допуска
  криптографического профиля.
- Контур допуска использует native-решение диагностики кручения рациональных кривых, чтобы принять
  или отклонить экспериментальный профиль до QR/relationship/packet use.
- Текущий product path связывает `cryptoProfileId`, `admissionDecisionHash` и
  `profilePolicyVersion` через handshake, relationship state и packets.

## Что делает диагностика кручения рациональных кривых

Диагностика кручения рациональных кривых проверяет параметры экспериментального профиля и возвращает
решение допуска:

- принять профиль;
- отклонить сингулярный профиль;
- отклонить профиль с признаками нежелательной малой структуры;
- перевести профиль в эталонную проверку;
- заблокировать автоматический допуск, если локальная диагностика ограничена по
  размеру/сложности;
- закрыть экспериментальный профиль без допуска, если native backend недоступен.

Это влияет на криптографическую политику Kraken: слабый или подменённый
экспериментальный профиль не должен становиться message-capable.

## Будущие C++ кандидаты

- детерминированная сериализация packet;
- policy evaluation для packet/transit;
- расширенная диагностика кривых и parity с Python research code;
- сбор метрик для симулятора и evidence runs;
- малые чистые функции, которым нужна стабильность между платформами.

## Не-цели

- нет Android UI или lifecycle logic в C++;
- нет custom production cryptographic primitives;
- нет промышленного шифрования, packet signatures, Android Keystore ownership
  или audited secure messenger proof;
- нет networking или socket ownership в native scaffold;
- нет public discovery, account или cloud assumptions.

## Правила JNI-границы

- Kotlin models остаются source of truth до отдельного scoped migration.
- Native functions должны быть маленькими, детерминированными и тестируемыми.
- Production crypto должен использовать reviewed libraries и явные abstractions,
  а не ad hoc native code.
- Native code должен сохранять Android build compatibility, включая 16 KB page
  size alignment для generated shared libraries.
