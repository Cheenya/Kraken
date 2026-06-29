package com.disser.kraken.ui.screens

import androidx.compose.runtime.Composable
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.formatEpoch

@Composable
fun IdentitySummaryCard(identity: LocalIdentity) {
    InfoCard(
        "Мой профиль",
        listOf(
            "Имя: ${identity.displayName}",
            "Отпечаток: ${identity.fingerprint}",
            "Статус: профиль готов",
            "Создано: ${formatEpoch(identity.createdAtEpochMillis)}",
        )
    )
}
