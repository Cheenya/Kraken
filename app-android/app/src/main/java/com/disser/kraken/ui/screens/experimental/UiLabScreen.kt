package com.disser.kraken.ui.screens.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.SectionHeader
import com.disser.kraken.ui.components.WarningCard

@Composable
fun UiLabScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    complaints: List<ComplaintEvent>,
    realmSnapshot: RealmSnapshot,
    onLoadDemoData: () -> Unit,
    onResetDemoData: () -> Unit,
) {
    var demoMessage by remember { mutableStateOf<String?>(null) }

    ScreenContainer("Варианты интерфейса", navController) {
        WarningCard(
            title = "Черновые варианты интерфейса",
            items = listOf(
                "Эти экраны изолированы от основного пользовательского сценария.",
                "Выбор направления потребует отдельной миграции основного интерфейса.",
            ),
        )
        InfoCard(
            title = "Варианты экранов",
            items = listOf(
                "Иконки, главная и чаты собраны здесь только для визуального сравнения.",
                "Основная навигация и поведение протокола этим разделом не меняются.",
                "Внешние ассеты мессенджеров и аккаунтные сценарии не используются.",
            ),
        )
        InfoCard(
            title = "Локальные тестовые данные",
            items = listOf(
                "Создаёт локальные примеры личности, ожидающих и активных контактов, реалма и каналов.",
                "Сброс удаляет только тестовые данные и не стирает существующую личность.",
                "Импорт примера не создаёт активный доступ автоматически.",
            ),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    onLoadDemoData()
                    demoMessage = "Тестовые данные загружены локально."
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Загрузить")
            }
            OutlinedButton(
                onClick = {
                    onResetDemoData()
                    demoMessage = "Тестовые данные сброшены. Личность не удалялась."
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Сбросить")
            }
        }
        demoMessage?.let { Text(it) }
        SectionHeader("Иконки")
        IconLabContent()
        SectionHeader("Варианты главной")
        HomeUxVariants(
            localIdentity = localIdentity,
            pendingInvites = pendingInvites,
            relationships = relationships,
            complaints = complaints,
            realmSnapshot = realmSnapshot,
        )
        SectionHeader("Варианты чатов")
        ChatUxVariants(relationships)
    }
}
