# Adamova Product Path Demo

Дата: 2026-06-08.

Статус: product-path evidence для экспериментального криптографического профиля Kraken. Этот отчёт показывает, что алгоритм Адамовой участвует в политике допуска профиля, а не только отображается в Research Panel.

## Claim Boundary

Разрешённая формулировка:

> В Kraken алгоритм Адамовой подключён как C++ admission gate для экспериментального криптографического профиля. Принятый профиль переносит metadata через QR, relationship, session profile и packet policy; слабый или недоступный для проверки experimental profile не становится message-capable.

Нельзя утверждать:

- что алгоритм Адамовой заменяет шифрование сообщений, подписи, key agreement или Android Keystore;
- что рациональная диагностика кривых над `Q` доказывает промышленную криптостойкость finite-field ECC;
- что стандартный профиль защищён Адамовой: для него корректный статус `NOT_APPLICABLE_STANDARD_PROFILE`.

## Product Path

| Шаг | Точка в коде | Evidence |
| --- | --- | --- |
| Profile definition | `KrakenCryptoProfile`, `DefaultCryptoProfileRegistry` | standard profile и `EXPERIMENTAL_ADAMOVA_CURVE_PROFILE` разделены |
| Native gate | `ProductCryptoAdmissionGate`, `NativeCoreBridge` | experimental profile вызывает C++ Adamova classifier; native unavailable fail-closed |
| QR invite | `InvitePayloadFactory.createForProfile` | accepted profile записывает `cryptoProfileId`, `cryptoProfileHash`, `admissionDecisionHash`, `profilePolicyVersion`, `nativeBackendVersion` |
| Pending relationship | `InviteImportService`, `RelationshipService.createFromPendingInvite` | QR metadata переносится в pending relationship |
| Response/final QR | `OfflineHandshakeService` | response и confirmation сохраняют profile/admission binding |
| Active relationship | `OfflineHandshakeService.processResponsePayload/processConfirmationPayload` | rejected/unknown/mismatched profile не активирует relationship |
| Packet/session policy | `MeshOutboxProcessor`, `MeshInboxProcessor`, `validatePacketAdmission` | packet несёт `cryptoProfileId`, `sessionProfileId`, `admissionDecisionHash`, `profilePolicyVersion`; mismatch rejected |
| UI/Research mode | `ResearchScreen.AdamovaAdmissionGateCard` | видимый текст: “Экспериментальный профиль отклонён проверкой Адамовой...” без claim о боевой криптографии |

## Demonstrated Cases

| Case | Expected policy | Test evidence |
| --- | --- | --- |
| Weak experimental profile | block before message send | `MeshDeliveryPipelineTest.weakExperimentalProfileBlockedBeforeMessageSendByAdamovaPolicy` |
| Accepted experimental profile | allow only matching metadata/session | `MeshDeliveryPipelineTest.acceptedExperimentalProfileRequiresMatchingPacketMetadata` |
| Inbound mismatch profile/session | reject packet | `MeshDeliveryPipelineTest.inboundPacketWithMismatchedCryptoProfileIsRejected`; session mismatch branch in accepted profile test |
| Native unavailable | fail-closed for experimental profile | `ProductCryptoAdmissionGateTest.nativeUnavailableFailsClosedForExperimentalProfile`; `MeshDeliveryPipelineTest.nativeUnavailableFailsClosedForExperimentalProfileBeforeTransport` |
| Standard profile | not blocked by Adamova and not Adamova-protected | `ProductCryptoAdmissionGateTest.standardProfileBypassesAdamovaAsNotApplicable` |
| QR/relationship round trip | accepted experimental metadata reaches invite, response, confirmation and active relationship | `OfflineHandshakeServiceTest.acceptedExperimentalProfileMetadataRoundTripsThroughInviteResponseAndConfirmation` |
| Weak QR creation | rejected experimental profile cannot create a message-capable invite | `OfflineHandshakeServiceTest.weakExperimentalProfileCannotCreateMessageCapableInvite` |

## Краткая таблица для диссертации

| Компонент | Что доказано | Ограничение |
| --- | --- | --- |
| Adamova C++ gate | experimental profile допускается или отклоняется до сессии | это admission policy, не шифрование |
| QR/relationship metadata | accepted profile binding переносится через invite/response/confirmation | QR payload пока не является подписанным production artifact |
| Packet/session policy | packet accepted только при совпадении profile/session/admission metadata | `PrototypeNoSecurityPacketCrypto` не подписывает и не шифрует |
| Standard profile | получает `NOT_APPLICABLE_STANDARD_PROFILE` | нельзя писать, что он защищён Адамовой |
| UI | Research mode показывает отказ experimental profile проверкой Адамовой | UI не является криптографическим доказательством |

## Validation Scope

Релевантный точечный прогон после изменений:

```text
./gradlew :app:testDebugUnitTest \
  --tests 'com.disser.kraken.crypto.ProductCryptoAdmissionGateTest' \
  --tests 'com.disser.kraken.handshake.OfflineHandshakeServiceTest' \
  --tests 'com.disser.kraken.mesh.MeshDeliveryPipelineTest'
```

Результат: `BUILD SUCCESSFUL`.
