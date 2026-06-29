package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InviteImportResult
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer

@Composable
fun ImportInviteScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    onInviteJsonImported: (String) -> InviteImportResult,
) {
    ScreenContainer("Скан QR", navController) {
        InfoCard(
            "Сканирование Kraken QR",
            listOf(
                "Сканируйте QR контакта или QR подтверждения.",
                "Контакт станет активным после локального подтверждения.",
            )
        )
        Button(
            onClick = { navController.navigate(KrakenRoute.QrScanner.route) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сканировать QR")
        }
    }
}
