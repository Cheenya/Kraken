package com.disser.kraken.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.TransportReadinessMonitor
import com.disser.kraken.ui.components.WarningCard
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QrScannerScreen(
    navController: NavHostController,
    onInviteQrScanned: (String) -> QrScanImportResult,
    meshSnapshot: MeshServiceSnapshot,
    onOpenChat: (com.disser.kraken.relationship.Relationship) -> Unit,
    initialScannedText: String? = null,
    onInitialScannedTextConsumed: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val tokens = LocalKrakenThemeTokens.current
    val transportReadiness = TransportReadinessMonitor.snapshot(context, meshSnapshot)
    var hasCameraPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var scanResult by remember { mutableStateOf<QrScanImportResult?>(null) }
    var scanSession by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(initialScannedText) {
        val deepLinkText = initialScannedText?.trim().orEmpty()
        if (deepLinkText.isNotBlank()) {
            scanResult = onInviteQrScanned(deepLinkText)
            onInitialScannedTextConsumed(deepLinkText)
        }
    }

    ScreenContainer("Скан Kraken QR", navController) {
        ScannerTopCopy()

        if (hasCameraPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(tokens.cardRadius + 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
	                Box(
	                    modifier = Modifier
	                        .fillMaxWidth()
	                        .heightIn(min = 280.dp, max = 360.dp)
	                        .padding(12.dp)
	                        .clip(RoundedCornerShape(tokens.cardRadius)),
                    contentAlignment = Alignment.Center,
                ) {
                    QrCameraPreview(
                        scanningEnabled = scanResult?.isTerminalSuccess() != true,
                        scanSession = scanSession,
                        onQrText = { decodedText ->
                            scanResult = onInviteQrScanned(decodedText)
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                    ScannerFrameOverlay(
                        scanningActive = scanResult?.isTerminalSuccess() != true,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        } else {
            WarningCard(
                "Доступ к камере",
                listOf(
                    "Камера нужна только для сканирования Kraken QR.",
                    "Если доступ запрещён, контакт рядом нельзя подтвердить через QR.",
                )
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Разрешить камеру")
            }
        }

        scanResult?.let { result ->
            when (result) {
                is QrScanImportResult.Error -> {
                    WarningCard(
                        "Неверный QR",
                        listOf(
                            result.message,
                            "Повторите сканирование с корректным Kraken QR.",
                        )
                    )
                    Button(
                        onClick = {
                            scanResult = null
                            scanSession += 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Повторить")
                    }
                }
                is QrScanImportResult.Success -> {
                    LaunchedEffect(result.pendingImport.localId) {
                        navController.navigate(KrakenRoute.Contacts.route) {
                            popUpTo(KrakenRoute.QrScanner.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
	                    InfoCard(
	                        "QR принят",
	                        listOf(
	                            "Имя: ${result.pendingImport.inviterDisplayName}",
	                            "Отпечаток: ${result.pendingImport.inviterFingerprint}",
	                            "Переходим к подтверждению контакта рядом.",
	                        )
	                    )
                    if (!transportReadiness.anyReady) {
                        QueuedNearbyHandshakeWarning()
                    }
	                    StateBadge(pendingInviteStateLabel(result.pendingImport.state))
                    Button(
                        onClick = {
                            navController.navigate(KrakenRoute.Contacts.route) {
                                popUpTo(KrakenRoute.QrScanner.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("К контакту")
                    }
                }
                is QrScanImportResult.KnownContact -> {
                    InfoCard(
                        "Контакт уже добавлен",
                        listOf(
                            "Контакт: ${result.relationship.peerDisplayName ?: "неизвестно"}",
                            "Повторно сканировать его приглашение не нужно.",
                            "Откройте контакт или чат, чтобы продолжить.",
                        ),
                    )
                    StateBadge(relationshipStateLabel(result.relationship.state))
                    Button(
                        onClick = { onOpenChat(result.relationship) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть чат")
                    }
                    OutlinedButton(
                        onClick = {
                            navController.navigate(KrakenRoute.Contacts.route) {
                                popUpTo(KrakenRoute.QrScanner.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("К контактам")
                    }
                }
                is QrScanImportResult.HandshakeResponseAccepted -> {
                    val isRealmResponse = result.relationship.realmId != null
	                    InfoCard(
	                        "Ответ принят",
                        if (isRealmResponse) {
                            listOf(
	                                "Контакт: ${result.relationship.peerDisplayName ?: "неизвестно"}",
	                                "На этом устройстве контакт активен.",
                                "Для реалма сначала откройте заявки, одобрите участника, затем используйте ручное завершение через QR.",
	                            )
	                        } else {
	                            listOf(
	                                "Контакт: ${result.relationship.peerDisplayName ?: "неизвестно"}",
	                                "На этом устройстве контакт активен.",
                                "На втором устройстве может потребоваться ручное завершение через QR.",
	                            )
	                        }
	                    )
                    if (!transportReadiness.anyReady) {
                        QueuedNearbyHandshakeWarning()
                    }
	                    StateBadge(relationshipStateLabel(result.relationship.state))
                    if (isRealmResponse) {
                        Button(
                            onClick = { navController.navigate(KrakenRoute.PendingApprovals.route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("К заявкам реалма")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            navController.navigate(KrakenRoute.Contacts.route) {
                                popUpTo(KrakenRoute.QrScanner.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("К сопряжению")
                    }
                }
                is QrScanImportResult.HandshakeConfirmationAccepted -> {
                    InfoCard(
                        "Рукопожатие завершено",
                        listOf(
	                            "Контакт: ${result.relationship.peerDisplayName ?: "неизвестно"}",
	                            "Контакт активен на этом устройстве.",
	                            if (result.realmMembershipApplied) {
	                                "Сертификат реалма применён локально."
	                            } else {
	                                "Этот QR не менял участие в реалме."
	                            },
                            "Это локальное QR-рукопожатие; боевая криптографическая проверка ещё не реализована.",
	                        )
	                    )
	                    StateBadge(relationshipStateLabel(result.relationship.state))
                    Button(
                        onClick = { onOpenChat(result.relationship) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть чат")
                    }
                    if (result.realmMembershipApplied) {
                        OutlinedButton(
                            onClick = { navController.navigate(KrakenRoute.Realms.route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Открыть реалмы")
                        }
                    }
                }
                is QrScanImportResult.LanEndpointAccepted -> {
                    InfoCard(
                        "QR-адрес связи добавлен",
                        listOf(
                            "Устройство: ${result.displayName ?: "без имени"}",
                            "Отпечаток: ${result.fingerprint}",
                            "Адрес: ${result.host}:${result.port}",
                            "Это только транспортная подсказка. Доверие и чат всё равно требуют активный контакт после QR-рукопожатия.",
                        )
                    )
                    StateBadge("ЛОКАЛЬНАЯ СЕТЬ")
                    Button(
                        onClick = { navController.navigate(KrakenRoute.MeshStatus.route) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть диагностику связи")
                    }
                }
            }
        }
        if (scanResult == null || scanResult is QrScanImportResult.Error) {
            OutlinedButton(onClick = { navController.navigate(KrakenRoute.Contacts.route) }, modifier = Modifier.fillMaxWidth()) {
                Text("К контактам")
            }
        }
    }
}

@Composable
private fun QueuedNearbyHandshakeWarning() {
    WarningCard(
        "Локальная связь не активна",
        listOf(
            "QR обработан, но устройство сейчас не выглядит готовым к передаче или приёму.",
            "Включите Wi‑Fi/Bluetooth и держите второй телефон рядом.",
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScannerTopCopy() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StateBadge("Контакт")
            StateBadge("Подтверждение")
            StateBadge("Адрес")
        }
        Text(
            "Наведите рамку на QR-код Kraken.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Обычный сценарий: один скан QR, затем подтверждение контакта рядом. Резервные ручные шаги доступны в карточке контакта, если Bluetooth недоступен.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScannerFrameOverlay(
    scanningActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .border(
                    BorderStroke(
                        width = 3.dp,
                        color = if (scanningActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    ),
                    RoundedCornerShape(28.dp),
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        ) {
            Text(
                text = if (scanningActive) "Поместите QR в рамку" else "Сканирование остановлено",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        ) {
            Text(
                text = "Сеть не нужна. Камера готова.",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun pendingInviteStateLabel(state: PendingInviteState): String =
    when (state) {
        PendingInviteState.PENDING_IMPORT -> "ОЖИДАЕТ"
        PendingInviteState.PENDING_HANDSHAKE -> "РУКОПОЖАТИЕ"
    }

private fun relationshipStateLabel(state: RelationshipState): String =
    when (state) {
        RelationshipState.ACTIVE -> "АКТИВЕН"
        RelationshipState.PENDING_IMPORT -> "ОЖИДАЕТ"
        RelationshipState.PENDING_HANDSHAKE -> "РУКОПОЖАТИЕ"
        RelationshipState.UNLINK_REQUESTED -> "ОТВЯЗКА"
        RelationshipState.UNLINKED -> "ЗАВЕРШЁН"
        RelationshipState.BLOCKED_BY_PEER -> "БЛОК"
        RelationshipState.REJOIN_REQUIRED -> "ПОВТОР"
    }
