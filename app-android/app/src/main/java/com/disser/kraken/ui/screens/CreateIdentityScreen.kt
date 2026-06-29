package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.WarningCard

@Composable
fun CreateIdentityScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    onIdentityCreated: (String) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    ScreenContainer("Создать профиль", navController) {
        localIdentity?.let { IdentitySummaryCard(it) }

        InfoCard(
            "Профиль Kraken",
            listOf(
                "Имя будет видно в контактах.",
                "Новый ключ означает нового пользователя.",
                "Без телефона, почты, логина и пароля.",
            )
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Имя") },
            supportingText = { Text("Изменение имени не меняет отпечаток ключа.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                val cleanDisplayName = displayName.trim()
                if (cleanDisplayName.isEmpty()) {
                    validationMessage = "Введите имя."
                } else {
                    onIdentityCreated(cleanDisplayName)
                    validationMessage = "Профиль создан."
                    navController.navigate(KrakenRoute.Chat.route) {
                        popUpTo(KrakenRoute.Welcome.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
            enabled = localIdentity == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Создать")
        }
        validationMessage?.let { Text(it) }
        WarningCard(
            "Политика ключа",
            listOf(
                "Ключи создаются и хранятся локально в Kraken.",
                "Ключи профиля не должны использовать идентификаторы устройства.",
            )
        )
    }
}
