package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.courier.CourierScoreCalculator
import com.disser.kraken.courier.CourierScoreEvent
import com.disser.kraken.courier.RelayReliabilityScore
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.mesh.LanEndpointPayload
import com.disser.kraken.mesh.LanEndpointPayloadCodec
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.mesh.MeshTransportSelection
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relay.ForwardingAllowedEvaluator
import com.disser.kraken.relay.RelayDeviceContext
import com.disser.kraken.relay.RelayPolicyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.PayloadQrCodeCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure

@Composable
fun MeshStatusScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    relationships: List<Relationship>,
    realmSnapshot: RealmSnapshot,
    relayPolicyState: RelayPolicyState,
    meshSnapshot: MeshServiceSnapshot,
    selectedTransportProfile: String,
    onStartMesh: () -> Unit,
    onStartHotspotCompatibleMesh: () -> Unit,
    onStartWifiDirectTrialMesh: () -> Unit,
    onStopMesh: () -> Unit,
    onSyncMeshNow: () -> Unit,
    onAddManualPeer: (fingerprint: String, host: String, port: String) -> Unit,
) {
    var manualFingerprint by remember { mutableStateOf("") }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    val localLanEndpointPayload = localLanEndpointPayload(localIdentity, meshSnapshot)
    val meshRunning = meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)

    ScreenContainer("Диагностика связи", navController) {
        InfoCard(
            "Маршруты",
            listOf(
                "LAN: ${lanRouteLabel(meshSnapshot)}",
                "BLE: ${bleRouteLabel(meshSnapshot)}",
                "Очередь: ${queueSummary(meshSnapshot)}",
                "Последняя ошибка: ${meshSnapshot.transportDiagnostics.lastError ?: meshSnapshot.queue.lastError ?: "нет"}",
            ),
        )
        InfoCard(
            "Состояние",
            listOf(
                "Связь: ${meshStateLabel(meshSnapshot.state)}",
                "Профиль транспорта: ${transportProfileLabel(selectedTransportProfile)}",
                "Фоновая служба: ${if (meshSnapshot.foregroundServiceEnabled) "включена" else "выключена"}",
                "Последний старт службы: ${meshSnapshot.lastServiceStartedAtEpochMillis?.toString() ?: "нет"}",
                "Устройств рядом: ${meshSnapshot.discoveredPeers.size}",
                "Сообщений в очереди: ${meshSnapshot.queuedPackets}",
            ),
        )
        if (meshSnapshot.discoveredPeers.isNotEmpty()) {
            InfoCard(
                "Найденные устройства",
                meshSnapshot.discoveredPeers.take(6).mapIndexed { index, peer ->
                    "${index + 1}. ${peer.displayName ?: "Устройство рядом"}"
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (meshRunning) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text("Запущено")
                }
                OutlinedButton(onClick = onStopMesh, modifier = Modifier.weight(1f)) {
                    Text("Остановить")
                }
            } else {
                Button(onClick = onStartMesh, modifier = Modifier.weight(1f)) {
                    Text("Включить")
                }
            }
        }
        if (meshRunning) {
            OutlinedButton(
                onClick = {
                    onStopMesh()
                    onStartMesh()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Перезапустить связь")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onStartHotspotCompatibleMesh,
                modifier = Modifier.weight(1f),
            ) {
                Text("С точкой доступа")
            }
            OutlinedButton(
                onClick = onStartWifiDirectTrialMesh,
                modifier = Modifier.weight(1f),
            ) {
                Text("Wi‑Fi Direct")
            }
        }
        Button(onClick = onSyncMeshNow, modifier = Modifier.fillMaxWidth()) {
            Text(if (meshSnapshot.queuedPackets > 0) "Отправить очередь" else "Проверить связь")
        }
        TechnicalDetailsDisclosure("QR-адрес связи") {
            if (localLanEndpointPayload == null) {
                InfoCard(
                    "QR-адрес связи",
                    listOf(
                        "Включите локальную связь, чтобы показать QR для второго телефона.",
                        "QR-адрес помогает найти устройство в локальной сети, если автообнаружение не сработало.",
                        "Этот QR не создаёт доверие и не заменяет взаимное QR-рукопожатие.",
                    ),
                )
            } else {
                PayloadQrCodeCard(
                    title = "QR-адрес связи",
                    payloadJson = LanEndpointPayloadCodec.encode(localLanEndpointPayload),
                    details = listOf(
                        "Сканируйте на втором телефоне, если автообнаружение не нашло устройство.",
                        "Адрес: ${localLanEndpointPayload.host}:${localLanEndpointPayload.port}",
                        "Отпечаток: ${localLanEndpointPayload.fingerprint}",
                        "Это только транспортная подсказка. Активный контакт по QR-рукопожатию всё ещё обязателен.",
                    ),
                )
            }
        }
        val trustedPeerLines = p2pTrustedPeerLines(localIdentity, relationships, realmSnapshot, meshSnapshot)
        if (trustedPeerLines.isNotEmpty()) {
            InfoCard(
                "Проверенные QR-контакты",
                trustedPeerLines,
            )
        }
        OutlinedButton(
            onClick = {
                navController.navigate(KrakenRoute.TwoPhoneChecklist.route) {
                    launchSingleTop = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Открыть проверку на двух телефонах")
        }

        TechnicalDetailsDisclosure("Техническая диагностика связи") {
            DebugMeshDiagnostics(
                localIdentity = localIdentity,
                relayPolicyState = relayPolicyState,
                meshSnapshot = meshSnapshot,
                manualFingerprint = manualFingerprint,
                manualHost = manualHost,
                manualPort = manualPort,
                onManualFingerprintChanged = { manualFingerprint = it },
                onManualHostChanged = { manualHost = it },
                onManualPortChanged = { manualPort = it },
                onAddManualPeer = onAddManualPeer,
            )
        }
    }
}

private fun localLanEndpointPayload(
    localIdentity: LocalIdentity?,
    meshSnapshot: MeshServiceSnapshot,
): LanEndpointPayload? {
    val identity = localIdentity ?: return null
    val host = meshSnapshot.transportDiagnostics.localAddresses.firstOrNull() ?: return null
    val port = meshSnapshot.transportDiagnostics.localPort ?: return null
    return LanEndpointPayload(
        fingerprint = identity.fingerprint,
        displayName = identity.displayName,
        host = host,
        port = port,
    )
}

private fun p2pTrustedPeerLines(
    localIdentity: LocalIdentity?,
    relationships: List<Relationship>,
    realmSnapshot: RealmSnapshot,
    meshSnapshot: MeshServiceSnapshot,
): List<String> {
    val identity = localIdentity ?: return emptyList()
    val discovered = meshSnapshot.discoveredPeers.associateBy { it.fingerprint }
    return relationships
        .filter { relationship ->
            RealmCommunicationPolicy.canUseRelationship(identity, relationship, realmSnapshot).allowed
        }
        .take(6)
        .map { relationship ->
            val peer = discovered[relationship.peerFingerprint]
            val peerName = relationship.peerDisplayName ?: peer?.displayName ?: "QR-контакт"
            val shortFingerprint = relationship.peerFingerprint.shortMeshFingerprint()
            if (peer != null) {
                "$peerName · $shortFingerprint · найден в LAN"
            } else {
                "$peerName · $shortFingerprint · ожидается в LAN"
            }
        }
}

private fun queueSummary(meshSnapshot: MeshServiceSnapshot): String =
    when {
        meshSnapshot.queuedPackets == 0 &&
            meshSnapshot.queue.sentAwaitingAck == 0 &&
            meshSnapshot.queue.queuedReceipts == 0 -> "пусто"
        meshSnapshot.queue.sentAwaitingAck > 0 -> "ожидает подтверждения ${meshSnapshot.queue.sentAwaitingAck}"
        meshSnapshot.queue.queuedReceipts > 0 -> "готовы подтверждения ${meshSnapshot.queue.queuedReceipts}"
        else -> "ожидает отправки ${meshSnapshot.queuedPackets}"
    }

private fun transportProfileLabel(profile: String): String =
    when (profile) {
        MeshTransportSelection.PROFILE_WIFI_DIRECT_ONLY -> "только Wi‑Fi Direct"
        MeshTransportSelection.PROFILE_LAN_ONLY -> "только LAN"
        MeshTransportSelection.PROFILE_AUTO -> "авто"
        else -> "совместимо с точкой доступа"
    }

private fun lanRouteLabel(meshSnapshot: MeshServiceSnapshot): String {
    val diagnostics = meshSnapshot.transportDiagnostics
    val modeActive = diagnostics.transportModes.any { it == "lan-nsd-tcp" } ||
        diagnostics.registrationState.contains("lan", ignoreCase = true)
    return when {
        diagnostics.localPort != null && diagnostics.localAddresses.isNotEmpty() -> "активен ${diagnostics.localAddresses.first()}:${diagnostics.localPort}"
        modeActive && meshSnapshot.state != MeshState.OFF -> "запускается"
        else -> "не активен"
    }
}

private fun bleRouteLabel(meshSnapshot: MeshServiceSnapshot): String {
    val diagnostics = meshSnapshot.transportDiagnostics
    val started = listOf(
        diagnostics.bleAdvertisingState,
        diagnostics.bleScanningState,
        diagnostics.bleGattServerState,
    ).any { it.contains("started", ignoreCase = true) || it.contains("ready", ignoreCase = true) }
    return when {
        started -> "активен"
        diagnostics.transportModes.any { it == "ble-gatt" } && meshSnapshot.state != MeshState.OFF -> "запускается"
        else -> "не активен"
    }
}

private fun String.shortMeshFingerprint(): String =
    if (length <= 12) this else "${take(4)}…${takeLast(4)}"

@Composable
private fun DebugMeshDiagnostics(
    localIdentity: LocalIdentity?,
    relayPolicyState: RelayPolicyState,
    meshSnapshot: MeshServiceSnapshot,
    manualFingerprint: String,
    manualHost: String,
    manualPort: String,
    onManualFingerprintChanged: (String) -> Unit,
    onManualHostChanged: (String) -> Unit,
    onManualPortChanged: (String) -> Unit,
    onAddManualPeer: (fingerprint: String, host: String, port: String) -> Unit,
) {
    val demoContext = RelayDeviceContext(
        batteryPercent = 72,
        isCharging = true,
        isWifiConnected = true,
    )
    val forwardingAllowed = ForwardingAllowedEvaluator.canForwardTransit(relayPolicyState, demoContext)
    val courierSnapshot = CourierScoreCalculator.aggregate(
        listOf(
            CourierScoreEvent(
                dayBucket = "demo-day",
                forwardedCount = 0,
                confirmedUsefulRelayCount = 0,
                relayWindows = 0,
                ecoRelayBonus = 0,
            )
        )
    )
    val reliability = RelayReliabilityScore(successfulSessions = 0, failedSessions = 0)
    val routePreview = meshSnapshot.transportDiagnostics.recentRouteAttempts
        .takeLast(5)
        .joinToString { "${it.route}:${it.success}" }

    SelectionContainer {
        InfoCard(
            "Диагностика связи",
            listOf(
                "Личность: ${localIdentity?.displayName ?: "не создана"}",
                "Отпечаток: ${localIdentity?.fingerprint ?: "нет"}",
                "Режимы связи: ${meshSnapshot.transportDiagnostics.transportModes.takeIf { it.isNotEmpty() }?.joinToString() ?: meshSnapshot.transportMode}",
                "Bluetooth-реклама: ${meshSnapshot.transportDiagnostics.bleAdvertisingState}",
                "Bluetooth-сканирование: ${meshSnapshot.transportDiagnostics.bleScanningState}",
                "Bluetooth GATT-сервер: ${meshSnapshot.transportDiagnostics.bleGattServerState}",
                "Bluetooth-пиры: ${meshSnapshot.transportDiagnostics.bleConnectedPeerCount}",
                "Ожидаемое NSD-имя: ${localIdentity?.fingerprint?.let(::expectedNsdServiceName) ?: "нет"}",
                "IPv4: ${meshSnapshot.transportDiagnostics.localAddresses.takeIf { it.isNotEmpty() }?.joinToString() ?: "нет"}",
                "TCP-порт: ${meshSnapshot.transportDiagnostics.localPort?.toString() ?: "нет"}",
                "Регистрация: ${meshSnapshot.transportDiagnostics.registrationState}",
                "Обнаружение: ${meshSnapshot.transportDiagnostics.discoveryState}",
                "Multicast-блокировка: ${meshSnapshot.transportDiagnostics.multicastLockHeld}",
                "Ручные устройства: ${meshSnapshot.transportDiagnostics.manualPeerCount}",
                "Последняя ошибка транспорта: ${meshSnapshot.transportDiagnostics.lastError ?: "нет"}",
            ),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoCard(
            "Ручное добавление LAN-устройства",
            listOf(
                "Используйте, если NSD/multicast не видит второй телефон.",
                "Это не создаёт доверие или контакт; активное QR-рукопожатие всё ещё обязательно.",
            ),
        )
        OutlinedTextField(
            value = manualFingerprint,
            onValueChange = onManualFingerprintChanged,
            label = { Text("Fingerprint пира") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = manualHost,
                onValueChange = onManualHostChanged,
                label = { Text("IP / host") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = manualPort,
                onValueChange = { onManualPortChanged(it.filter(Char::isDigit).take(5)) },
                label = { Text("Port") },
                modifier = Modifier.weight(0.65f),
            )
        }
        OutlinedButton(
            onClick = { onAddManualPeer(manualFingerprint, manualHost, manualPort) },
            enabled = manualFingerprint.isNotBlank() && manualHost.isNotBlank() && manualPort.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Добавить устройство вручную")
        }
    }
    InfoCard(
        "Метрики прототипа",
        listOf(
                "В очереди: ${meshSnapshot.metrics.packetsQueued}",
                "Отправлено: ${meshSnapshot.metrics.packetsSent}",
                "Получено: ${meshSnapshot.metrics.packetsReceived}",
                "Подтверждений: ${meshSnapshot.metrics.receiptsReceived}",
                "Дубли отброшены: ${meshSnapshot.metrics.duplicatesDropped}",
                "Истекшие отброшены: ${meshSnapshot.metrics.expiredDropped}",
                "Неизвестные пиры отклонены: ${meshSnapshot.metrics.unknownPeerRejected}",
                "Ошибочные получатели отклонены: ${meshSnapshot.metrics.wrongRecipientRejected}",
                "Через relay-прототип: ${meshSnapshot.metrics.relayForwarded}",
                "Последняя задержка, мс: ${meshSnapshot.metrics.lastDeliveryLatencyMs?.toString() ?: "нет"}",
                "Хранилище очереди: ${meshSnapshot.queue.storedOutboxPackets}, отклонено: ${meshSnapshot.queue.rejectedPackets}, истекло: ${meshSnapshot.queue.expiredPackets}",
                "Следующая попытка: ${meshSnapshot.queue.nextAttemptAtEpochMillis?.toString() ?: "нет"}, ошибка очереди: ${meshSnapshot.queue.lastError ?: "нет"}",
                "Транспорт: принято ${meshSnapshot.transportDiagnostics.acceptedConnections}, входящих ${meshSnapshot.transportDiagnostics.inboundPackets}, битых кадров ${meshSnapshot.transportDiagnostics.malformedFramesDropped}, сбоев отправки ${meshSnapshot.transportDiagnostics.sendFailures}",
            "Маршруты: $routePreview",
        ),
    )
    InfoCard(
        "Политика ретрансляции",
        listOf(
            "Режим: ${relayPolicyState.mode}",
            "Состояние: ${ForwardingAllowedEvaluator.runtimeStateFor(relayPolicyState, demoContext)}",
            "Передача через relay-прототип в тестовом контексте: $forwardingAllowed",
            "Оценка переносчика: ${courierSnapshot.localScore}",
            "Надёжность ретрансляции: ${reliability.reliabilityPercent}%",
        ),
    )
        InfoCard(
            "Проверка на двух устройствах",
            listOf(
                "Ожидаемый сценарий для ручной проверки; доказательства для этой сборки могут быть ещё не сняты.",
                "1. Установите один APK на оба устройства.",
                "2. Завершите QR-рукопожатие до активного контакта с обеих сторон.",
                "3. Откройте этот экран на обоих телефонах и сравните fingerprints.",
                "4. Отправьте A -> B, пока оба приложения открыты.",
                "5. B получает сообщение; A показывает прототипное подтверждение доставки.",
            ),
        )
}

private fun expectedNsdServiceName(fingerprint: String): String =
    "Kraken-${fingerprint.filter { it.isLetterOrDigit() }.take(24)}"

private fun meshStateLabel(state: MeshState): String =
    when (state) {
        MeshState.OFF -> "выключена"
        MeshState.STARTING -> "запускается"
        MeshState.SCANNING -> "ищет устройства рядом"
        MeshState.PEER_FOUND -> "устройство найдено"
        MeshState.CONNECTED -> "связь установлена"
        MeshState.DEGRADED -> "нестабильная связь"
        MeshState.ERROR -> "ошибка"
    }
