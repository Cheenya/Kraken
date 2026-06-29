package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.InviteLifecycleFormatter
import com.disser.kraken.invite.InviteLifecycleState
import com.disser.kraken.invite.InviteQrDisplayModelFactory
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.mesh.MeshServiceSnapshot
import com.disser.kraken.mesh.MeshState
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.InviteQrCodeCard
import com.disser.kraken.ui.components.LabeledValue
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.WarningCard
import com.disser.kraken.ui.components.formatEpoch
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyQrScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    directInviteRecord: IssuedInviteRecord?,
    directInvitePayload: OneTimeInvitePayload?,
    meshSnapshot: MeshServiceSnapshot,
    onCreateDirectInvite: () -> OneTimeInvitePayload?,
    onRevokeInvite: (String) -> Unit,
) {
    var nowEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDetails by remember { mutableStateOf(false) }
    var localPayloadPreview by remember(localIdentity?.identityId, directInvitePayload?.inviteId) {
        mutableStateOf(directInvitePayload)
    }

    LaunchedEffect(localIdentity?.identityId, directInvitePayload?.inviteId) {
        if (localIdentity != null && directInvitePayload == null && localPayloadPreview == null) {
            localPayloadPreview = onCreateDirectInvite()
        }
    }

    LaunchedEffect(localPayloadPreview?.inviteId) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val inviteState = remember(localPayloadPreview, directInviteRecord, nowEpochMillis) {
        localPayloadPreview?.let { payload ->
            val matchingRecord = directInviteRecord?.takeIf { it.inviteId == payload.inviteId }
            InviteLifecycleFormatter.stateFor(
                payload = payload,
                revoked = matchingRecord?.revoked == true,
                consumed = matchingRecord?.consumed == true,
                nowEpochMillis = nowEpochMillis,
            )
        }
    }
    LaunchedEffect(localIdentity?.identityId, localPayloadPreview?.inviteId, inviteState) {
        if (
            localIdentity != null &&
            (localPayloadPreview == null || inviteState in setOf(InviteLifecycleState.EXPIRED, InviteLifecycleState.REVOKED, InviteLifecycleState.CONSUMED))
        ) {
            localPayloadPreview = onCreateDirectInvite()
            showDetails = false
        }
    }
    val inviteJson = remember(localPayloadPreview) {
        localPayloadPreview?.let { InvitePayloadCodec.encode(it) }.orEmpty()
    }
    val qrDisplayModel = remember(localPayloadPreview, inviteJson, inviteState, nowEpochMillis) {
        val payload = localPayloadPreview
        val state = inviteState
        if (payload != null && state != null) {
            InviteQrDisplayModelFactory.create(payload, inviteJson, state, nowEpochMillis)
        } else {
            null
        }
    }
    var copyMessage by remember { mutableStateOf<String?>(null) }

    ScreenContainer("Мой QR", navController) {
        if (localIdentity == null) {
            EmptyState(
                "Нужен профиль Kraken",
                "Создайте профиль перед показом QR.",
                actionLabel = "Создать",
                route = KrakenRoute.CreateIdentity,
                navController = navController,
            )
            return@ScreenContainer
        }

        val payload = localPayloadPreview
        InfoCard(
            "Мой QR",
            listOf("Покажите этот QR второму устройству. После сканирования приложение перейдёт к подтверждению контакта.")
        )
        when {
            qrDisplayModel == null ->
                InviteQrErrorCard("Готовим QR.")
            inviteState != InviteLifecycleState.AVAILABLE ->
                InviteQrErrorCard("Обновляем QR.")
            qrDisplayModel.isSuccess -> {
                InviteQrCodeCard(qrDisplayModel.getOrThrow())
                NearbyHandshakeWaitingStatus(meshSnapshot)
            }
            else ->
                InviteQrErrorCard(qrDisplayModel.exceptionOrNull()?.message ?: "Не удалось создать QR.")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    localPayloadPreview = onCreateDirectInvite()
                    showDetails = false
                    copyMessage = "Новый QR создан."
                },
            ) {
                Text("Обновить QR")
            }
            Button(
                onClick = {
                    localPayloadPreview?.inviteId?.let(onRevokeInvite)
                    copyMessage = "QR локально отозван."
                },
                enabled = localPayloadPreview != null && inviteState == InviteLifecycleState.AVAILABLE,
            ) {
                Text("Отозвать QR")
            }
            Button(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Скрыть детали" else "Детали")
            }
        }
        WarningCard(
            "Локальное подтверждение",
            listOf(
                "QR запускает локальное подтверждение контакта. Если Bluetooth недоступен, завершить сопряжение можно через QR подтверждения.",
            )
        )
        if (showDetails && payload != null) {
            IdentitySummaryCard(localIdentity)
            InfoCard(
                "Детали приглашения",
                listOf(
                    "Идентификатор: ${payload.inviteId}",
                    "Короткий ID: ${InviteLifecycleFormatter.shortInviteId(payload.inviteId)}",
                    "Создан: ${formatEpoch(payload.createdAtEpochMillis)}",
                    "Истекает: ${payload.expiresAtEpochMillis?.let(::formatEpoch) ?: "не задано"}",
                    "Возможности: ${payload.capabilities.joinToString()}",
                )
            )
        }
        copyMessage?.let { Text(it) }
    }
}

@Composable
private fun NearbyHandshakeWaitingStatus(meshSnapshot: MeshServiceSnapshot) {
    val meshRunning = meshSnapshot.state !in setOf(MeshState.OFF, MeshState.ERROR)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StateBadge(if (meshRunning) "ЖДЁМ СКАНИРОВАНИЯ" else "СВЯЗЬ НЕ ЗАПУЩЕНА")
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            if (meshRunning) {
                "После сканирования вторым устройством контакт должен подтвердиться автоматически."
            } else {
                "Запустите локальную связь, чтобы второе устройство могло отправить подтверждение без второго QR."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteSummaryCard(
    inviteId: String,
    state: InviteLifecycleState,
    expiresAtEpochMillis: Long?,
    nowEpochMillis: Long,
) {
    val tokens = LocalKrakenThemeTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StateBadge(InviteLifecycleFormatter.stateLabel(state))
                StateBadge("id ${InviteLifecycleFormatter.shortInviteId(inviteId)}")
            }
            LabeledValue("Истекает через", InviteLifecycleFormatter.expiresInLabel(expiresAtEpochMillis, nowEpochMillis))
        }
    }
}

@Composable
private fun InviteQrErrorCard(message: String) {
    val tokens = LocalKrakenThemeTokens.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardRadius + 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Одноразовое QR-приглашение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(message)
        }
    }
}
