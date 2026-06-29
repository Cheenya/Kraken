# Kraken Android

Android-first прототип Kraken на Kotlin/Compose.

Kraken — диссертационный прототип с доступом только по приглашениям. В нём нет регистрации аккаунта, входа по телефону/email, публичного поиска и заявления о production-криптографии.

## Требования

- JDK 17.
- Android Studio with Android SDK platform 35.
- Android NDK and CMake installed through Android Studio SDK Manager.
- Запущенный эмулятор или авторизованное физическое устройство для установочных проверок.

На macOS `ANDROID_HOME` обычно указывает на:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

Проверить toolchain и состояние устройства:

```bash
java -version
./gradlew --version
adb devices -l
```

## Сборка

Из этого каталога:

```bash
./gradlew test
./gradlew assembleDebug
```

Debug APK записывается сюда:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Установка

При подключённом эмуляторе или авторизованном Android-устройстве:

```bash
./gradlew installDebug
```

Если `adb devices -l` показывает `unauthorized`, разблокируйте телефон и подтвердите запрос отладки.

## Скриншоты

Скриншот эмулятора из командной строки:

```bash
adb exec-out screencap -p > kraken-screen.png
```

Для физических устройств держите устройство включённым и разблокированным перед командами скриншота или установки.

## Текущий статус функций

- Android-first каркас приложения с тёмной темой Material 3.
- Создание локальной личности только по отображаемому имени.
- Placeholder-провайдер ключей личности на базе SecureRandom.
- Отображение отпечатка, вычисленного из байтов публичного ключа.
- Реальный рендер QR-приглашений и сканирование QR камерой.
- Управление жизненным циклом одноразовых приглашений: regenerate/revoke.
- Офлайн mutual QR-рукопожатие через QR-полезные нагрузки invite, response и confirmation.
- Машина состояний связи: pending, active, unlink и blocked.
- Поле отправки сообщений доступно только для связей `ACTIVE`.
- Realm, membership certificate, invite edge, policy and capacity models.
- Pending approval, delivery simulator, packet buffer, receipts/tombstones, safe crypto interfaces, battery policy, Courier Score, moderation, channels, small groups and research panel MVP models.
- Локальный demo data helper для просмотра скриншотов.
- CMake/NDK native backend for accelerated rational-curve torsion diagnostics and benchmark comparison.
- Встроенные отчёты Research Panel для диагностики рациональных эллиптических кривых.

## Хранение

- MVP-хранение использует store на базе SharedPreferences.
- Имена store и ключи централизованы в `KrakenStorageKeys`.
- Store записывает placeholder версии схемы для будущих локальных миграций.
- Нет серверного хранения.
- Нет облачной синхронизации.
- Нет восстановления аккаунта.
- `privateKeyReference` is not production non-exportable key storage.
- В будущем hardening должен перенести работу с приватным ключом в Android Keystore.

## Совместимость native library

The native library currently exposes accelerated rational-curve torsion diagnostics used by the experimental crypto-profile admission policy, plus Kotlin-vs-C++ benchmark support. It still contains no protocol runtime, production cryptography, networking or message delivery logic. CMake links the native library with 16 KB page-size compatible alignment where supported by the Android toolchain.

## Текущие ограничения

- Product UX движется к QR-only. JSON-полезные нагрузки могут оставаться как внутренние/debug/test артефакты, но не должны показываться как основной пользовательский сценарий.
- LAN NSD/TCP exists as a local prototype transport and opens local sockets when mesh is enabled or a send/sync flow starts.
- Manual two-phone LAN NSD/TCP over local Wi-Fi prototype message exchange and receipt-level delivered UI evidence exists in `../reports/out/two_device_delivery_evidence.md`; treat it as prototype delivery evidence, not Wi-Fi Direct, production reliability or production security proof.
- Safe crypto пока является только абстракцией; production-реализации шифрования нет.
- Сообщения local-first и могут ставиться в очередь LAN-прототипа, но payload/signature/encryption пока остаются prototype-only.
- Офлайн QR-рукопожатие использует prototype proof placeholders, а не production-подписи.
- Cloud/server relay не настроен; Android permission `INTERNET` используется только для локальных LAN-сокетов.
- Предупреждения Android Gradle Plugin могут оставаться из-за compatibility flags, нужных текущей связке AGP/Kotlin plugin.
