package com.disser.kraken.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.BuildConfig
import com.disser.kraken.mesh.MeshEvidenceExporter
import com.disser.kraken.mesh.MeshEvidenceRouteAttemptExport
import com.disser.kraken.mesh.MeshEvidenceSnapshotExport
import com.disser.kraken.mesh.MeshEvidenceTransportExport
import com.disser.kraken.mesh.MeshEvidenceTransportPathExport
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.TransportPathReadiness
import com.disser.kraken.ui.components.TransportReadinessMonitor
import com.disser.kraken.ui.components.WarningCard
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun TwoPhoneChecklistScreen(
    navController: NavHostController,
    meshSnapshot: MeshServiceSnapshot,
    onRunDebugEvidenceProbe: suspend () -> String = { "Проверка недоступна" },
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val readiness = TransportReadinessMonitor.snapshot(context, meshSnapshot)
    val evidenceSnapshot = MeshEvidenceExporter.build(
        snapshot = meshSnapshot,
        appBuildType = BuildConfig.BUILD_TYPE,
        appVersionName = BuildConfig.KRAKEN_VERSION_NAME,
        gitSha = BuildConfig.GIT_SHA,
        deviceModel = Build.MODEL ?: "unknown",
        sourceState = BuildConfig.SOURCE_STATE,
        transportReadiness = MeshEvidenceTransportExport(
            lanWifi = readiness.wifi.toEvidence("lan-wifi"),
            ble = readiness.bluetooth.toEvidence("bluetooth"),
            wifiDirect = readiness.wifiDirect.toEvidence("wifi-direct"),
            recentRouteAttempts = meshSnapshot.transportDiagnostics.recentRouteAttempts.map {
                MeshEvidenceRouteAttemptExport(
                    path = it.route,
                    peerId = "unknown",
                    peerFingerprint = "unknown",
                    success = it.success,
                    error = it.error,
                    attemptedAtEpochMillis = it.atEpochMillis,
                )
            },
        ),
    )
    val evidenceJson = MeshEvidenceExporter.toJson(evidenceSnapshot)
    val evidenceMarkdown = MeshEvidenceExporter.toMarkdownSummary(evidenceSnapshot)
    val routePreview = evidenceSnapshot.transport.recentRouteAttempts
        .takeLast(3)
        .joinToString { routeAttemptLabel(it) }
    val checked = remember { mutableStateMapOf<String, ChecklistItemState>() }
    var copyStatus by remember { mutableStateOf<String?>(null) }
    var debugStatus by remember { mutableStateOf<String?>(null) }
    var debugProbeRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    ScreenContainer("Проверка связи", navController) {
        InfoCard(
            "Текущее состояние",
            listOf(
                "Связь: ${meshStateLabel(meshSnapshot.state)}",
                "Wi‑Fi/LAN: ${readiness.wifi.readinessLabel()}",
                "Bluetooth: ${readiness.bluetooth.readinessLabel()}",
                "Последняя ошибка связи: ${friendlyTransportError(meshSnapshot.transportDiagnostics.lastError ?: meshSnapshot.queue.lastError?.toString())}",
            ),
        )
        if (!readiness.bluetooth.ready && !readiness.wifi.ready) {
            WarningCard(
                "Устройство пока не готово к локальной связи",
                listOf(
                    "Включите Wi‑Fi и локальную связь перед проверкой.",
                    "Если тест идёт через LAN, оба телефона должны быть в одной локальной сети.",
                ),
            )
        }
        ChecklistSection(
            checked = checked,
            items = listOf(
                "install_apk" to "Свежий APK установлен на оба телефона",
                "version_visible" to "На стартовом экране видна версия/сборка",
                "identity_ready" to "На обоих телефонах создан профиль Kraken",
                "permissions_ready" to "Wi‑Fi включён, локальная связь запущена",
                "scan_invite" to "Телефон B сканирует QR-приглашение телефона A",
                "one_qr_active" to "QR-рукопожатие довело контакт до активного состояния",
                "fallback_qr" to "Если локальная связь не сработала, QR подтверждения проверен",
                "messages_a_b" to "3 сообщения A -> B доставлены",
                "messages_b_a" to "3 сообщения B -> A доставлены",
                "order_receipts" to "Порядок сообщений и подтверждения доставки проверены",
                "routerless" to "Bluetooth напрямую проверен; Wi‑Fi Direct требует отдельной проверки",
                "relay_mac" to "Ретрансляция проверена только при наличии подтверждения пути",
                "attack_modes" to "Сброс, дубль и изменение пакета проверены",
            ),
        )
        InfoCard(
            "Отчёт проверки",
            listOf(
                "Источник: ${sourceStateLabel(evidenceSnapshot.sourceState)}",
                "Выбранный маршрут: ${routeLabel(evidenceSnapshot.transport.selectedRoute)}",
                "Wi‑Fi/LAN: ${yesNo(evidenceSnapshot.transport.lanWifi?.active == true)}",
                "Bluetooth: ${yesNo(evidenceSnapshot.transport.ble?.active == true)}",
                "Wi‑Fi Direct: ${yesNo(evidenceSnapshot.transport.wifiDirect?.active == true)}",
                "Попыток маршрута: ${evidenceSnapshot.transport.recentRouteAttempts.size}",
                "Отклонено не тому получателю: ${evidenceSnapshot.metrics.wrongRecipientRejected}",
                "Отклонено неизвестных устройств: ${evidenceSnapshot.metrics.unknownPeerRejected}",
                "Дублей отброшено: ${evidenceSnapshot.metrics.duplicatesDropped}",
                "Очередь: ${evidenceSnapshot.queueSize}",
                "Повторная отправка: ${retryStateLabel(evidenceSnapshot)}",
                "Последние пути: $routePreview",
                "Размер: ${evidenceJson.length} символов",
            ),
        )
        OutlinedButton(
            onClick = {
                if (!debugProbeRunning) {
                    debugProbeRunning = true
                    coroutineScope.launch {
                        debugStatus = onRunDebugEvidenceProbe()
                        debugProbeRunning = false
                    }
                }
            },
            enabled = !debugProbeRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (debugProbeRunning) "Проверка выполняется" else "Проверить отказы и повторную отправку")
        }
        debugStatus?.let { StateBadge(it) }
        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(evidenceJson))
                saveEvidenceFile(context.filesDir, "route_specific_evidence_latest.json", evidenceJson)
                copyStatus = "Отчёт скопирован и сохранён"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Скопировать отчёт")
        }
        OutlinedButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(evidenceMarkdown))
                saveEvidenceFile(context.filesDir, "route_specific_evidence_summary_latest.md", evidenceMarkdown)
                copyStatus = "Сводка скопирована и сохранена"
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Скопировать сводку")
        }
        copyStatus?.let { StateBadge(it) }
    }
}

@Composable
private fun ChecklistSection(
    checked: MutableMap<String, ChecklistItemState>,
    items: List<Pair<String, String>>,
) {
    items.forEach { (id, label) ->
        val state = checked[id] ?: ChecklistItemState.NOT_RUN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = state.label
                }
                .clickable(role = Role.Checkbox) {
                    checked[id] = state.next()
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state == ChecklistItemState.PASSED,
                onCheckedChange = { checked[id] = state.next() },
            )
            Text(label, modifier = Modifier.weight(1f))
            StateBadge(state.label)
        }
    }
}

private enum class ChecklistItemState(val label: String) {
    NOT_RUN("не проверено"),
    PASSED("пройдено"),
    FAILED("не пройдено");

    fun next(): ChecklistItemState =
        when (this) {
            NOT_RUN -> PASSED
            PASSED -> FAILED
            FAILED -> NOT_RUN
        }
}

private fun TransportPathReadiness.toEvidence(pathLabel: String): MeshEvidenceTransportPathExport =
    MeshEvidenceTransportPathExport(
        permissionGranted = permissionGranted,
        radioEnabled = radioEnabled,
        serviceAvailable = serviceRunning,
        active = ready,
        inactiveReasons = inactiveReasons(pathLabel),
    )

private fun TransportPathReadiness.inactiveReasons(pathLabel: String): List<String> =
    buildList {
        if (!permissionGranted) add("$pathLabel-permission-missing")
        if (!radioEnabled) add("$pathLabel-radio-disabled")
        if (!transportImplemented) add("$pathLabel-transport-not-implemented")
        if (!serviceRunning) add("$pathLabel-service-unavailable")
    }

private fun TransportPathReadiness.readinessLabel(): String =
    when {
        ready -> "активен"
        !permissionGranted -> "нет разрешения"
        !radioEnabled -> "выключен адаптер"
        !transportImplemented -> "не реализован"
        !serviceRunning -> "служба недоступна"
        else -> "не активен"
    }

private fun routeAttemptLabel(attempt: MeshEvidenceRouteAttemptExport): String =
    "${routeLabel(attempt.path)}: ${if (attempt.success) "успех" else "сбой"}"

private fun routeLabel(route: String?): String =
    when (route) {
        null, "", "none" -> "нет маршрута"
        "ble-gatt", "bluetooth" -> "Bluetooth напрямую"
        "lan-nsd-tcp", "lan-wifi" -> "Wi‑Fi/LAN напрямую"
        "routed-mesh", "relay-prototype" -> "через ретрансляцию"
        "wifi-direct" -> "Wi‑Fi Direct"
        else -> "нет маршрута"
    }

private fun meshStateLabel(state: MeshState): String =
    when (state) {
        MeshState.OFF -> "выключена"
        MeshState.STARTING -> "запускается"
        MeshState.SCANNING -> "поиск устройств"
        MeshState.PEER_FOUND -> "устройство найдено"
        MeshState.CONNECTED -> "связь установлена"
        MeshState.DEGRADED -> "нестабильная связь"
        MeshState.ERROR -> "ошибка"
    }

private fun friendlyTransportError(raw: String?): String =
    when (raw) {
        null -> "нет"
        "EXPIRED" -> "срок действия приглашения истёк"
        else -> raw
    }

private fun sourceStateLabel(sourceState: String): String =
    when {
        sourceState.contains("dirty", ignoreCase = true) -> "текущая сборка с локальными изменениями"
        sourceState.contains("clean", ignoreCase = true) -> "чистая сборка"
        sourceState.isBlank() -> "не указан"
        else -> sourceState
    }

private fun retryStateLabel(evidenceSnapshot: MeshEvidenceSnapshotExport): String =
    listOf(
        "поставлено в очередь — ${yesNo(evidenceSnapshot.debugSmoke.queuedBeforeTransportRestart)}",
        "отправлено — ${yesNo(evidenceSnapshot.debugSmoke.sentAfterTransportRestart)}",
        "доставлено — ${yesNo(evidenceSnapshot.debugSmoke.deliveredAfterTransportRestart)}",
    ).joinToString(", ")

private fun yesNo(value: Boolean): String = if (value) "да" else "нет"

private fun saveEvidenceFile(filesDir: File, fileName: String, content: String) {
    val evidenceDir = File(filesDir, "evidence")
    evidenceDir.mkdirs()
    File(evidenceDir, fileName).writeText(content)
}
