package com.disser.kraken.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.KrakenCompactCard
import com.disser.kraken.ui.components.KrakenListRow
import com.disser.kraken.ui.components.KrakenPrimaryAction
import com.disser.kraken.ui.components.KrakenSecondaryAction
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.TransportPathReadiness
import com.disser.kraken.ui.components.TransportReadinessMonitor
import com.disser.kraken.ui.icons.KrakenIcons

@Composable
fun HomeScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    complaints: List<ComplaintEvent>,
    realmSnapshot: RealmSnapshot,
    meshSnapshot: MeshServiceSnapshot,
    onStartMesh: () -> Unit,
    onSyncMeshNow: () -> Unit,
) {
    val context = LocalContext.current
    val readiness = TransportReadinessMonitor.snapshot(context, meshSnapshot)
    val activeContacts = relationships.count { it.state == RelationshipState.ACTIVE }
    val pendingHandshakes = relationships.count {
        it.state == RelationshipState.PENDING_IMPORT || it.state == RelationshipState.PENDING_HANDSHAKE
    }
    val activeRealms = realmSnapshot.realms.count { it.localState == LocalRealmState.ACTIVE }

    ScreenContainer("Главная", navController, showTitle = false, showBack = false) {
        if (localIdentity != null) {
            KrakenListRow(
                title = localIdentity.displayName,
                subtitle = "Kraken · ${localIdentity.fingerprint.take(4)}…${localIdentity.fingerprint.takeLast(4)}",
                leadingIcon = KrakenIcons.Identity,
                trailingText = "профиль",
            )
        } else {
            EmptyState(
                title = "Профиль не создан",
                detail = "Создайте профиль Kraken перед показом QR.",
                actionLabel = "Создать",
                route = KrakenRoute.CreateIdentity,
                navController = navController,
            )
        }

        KrakenSectionHeader("Локальная связь")
        KrakenCompactCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Wi‑Fi/LAN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(readiness.wifi.homeStatusLabel("Wi‑Fi"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Bluetooth", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(readiness.bluetooth.homeStatusLabel("Bluetooth"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    homeTransportAddress(meshSnapshot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Фоновая служба: ${if (meshSnapshot.foregroundServiceEnabled) "включена" else "выключена"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Уведомления: ${notificationPermissionLabel(context)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                meshSnapshot.transportDiagnostics.lastError?.let { error ->
                    Text(
                        "Последняя ошибка: ${friendlyHomeTransportError(error)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                KrakenPrimaryAction(
                    label = if (meshSnapshot.state in setOf(MeshState.OFF, MeshState.ERROR)) {
                        "Включить связь"
                    } else {
                        "Проверить связь"
                    },
                    onClick = {
                        if (meshSnapshot.state in setOf(MeshState.OFF, MeshState.ERROR)) {
                            onStartMesh()
                        } else {
                            onSyncMeshNow()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = KrakenIcons.MeshStatus,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KrakenSecondaryAction(
                        label = "Скан QR",
                        onClick = { navController.navigate(KrakenRoute.QrScanner.route) },
                        modifier = Modifier.weight(1f),
                        icon = KrakenIcons.Import,
                    )
                    KrakenSecondaryAction(
                        label = "Мой QR",
                        onClick = { navController.navigate(KrakenRoute.MyQr.route) },
                        modifier = Modifier.weight(1f),
                        icon = KrakenIcons.Invite,
                    )
                }
            }
        }

        KrakenSectionHeader("Диалоги")
        KrakenCompactCard {
            OverviewMetricRow("Чаты", activeContacts.toString(), "Готовы к переписке") {
                navController.navigate(KrakenRoute.Chat.route)
            }
            OverviewMetricRow("Ожидают QR", pendingHandshakes.toString(), "Нужны следующие шаги") {
                navController.navigate(KrakenRoute.Contacts.route)
            }
            OverviewMetricRow("Активные реалмы", activeRealms.toString(), "Локальные записи") {
                navController.navigate(KrakenRoute.Realms.route)
            }
        }

        KrakenListRow(
            title = "Исследовательский режим",
            subtitle = "Сценарии, метрики и артефакты проверки.",
            leadingIcon = KrakenIcons.Research,
            trailingText = "›",
            onClick = { navController.navigate(KrakenRoute.Research.route) },
        )
    }
}

private fun TransportPathReadiness.homeStatusLabel(pathLabel: String): String =
    when {
        ready -> "активен"
        !permissionGranted -> "нет разрешения"
        !radioEnabled -> "$pathLabel выключен"
        !transportImplemented -> "не реализован"
        !serviceRunning -> "запускается"
        else -> "не активен"
    }

private fun homeTransportAddress(meshSnapshot: MeshServiceSnapshot): String {
    val host = meshSnapshot.transportDiagnostics.localAddresses.firstOrNull()
    val port = meshSnapshot.transportDiagnostics.localPort
    return if (host != null && port != null) {
        "QR-адрес связи: $host:$port"
    } else {
        "QR-адрес связи появится после запуска локальной связи."
    }
}

private fun notificationPermissionLabel(context: Context): String =
    if (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
        "разрешены"
    } else {
        "нет разрешения"
    }

private fun friendlyHomeTransportError(raw: String): String =
    when {
        raw.contains("wifi", ignoreCase = true) ||
            raw.contains("ble", ignoreCase = true) ||
            raw.contains("bluetooth", ignoreCase = true) ||
            raw.contains("peer", ignoreCase = true) ||
            raw.contains("transport", ignoreCase = true) ->
            "Не удалось запустить локальную связь. Проверьте Wi‑Fi и Bluetooth."
        else -> "Не удалось обновить локальную связь."
    }

@Composable
private fun OverviewMetricRow(
    title: String,
    value: String,
    subtitle: String,
    onOpen: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clickable(onClick = onOpen)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
