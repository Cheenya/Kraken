package com.disser.kraken.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disser.kraken.invite.InviteQrDisplayModel
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens

@Composable
fun InviteQrCodeCard(model: InviteQrDisplayModel, modifier: Modifier = Modifier) {
    val tokens = LocalKrakenThemeTokens.current
    val qrBitmap = remember(model.qrMatrix, MaterialTheme.colorScheme.onSurface.toArgb()) {
        model.qrMatrix.toBitmap(
            darkColor = android.graphics.Color.BLACK,
            lightColor = android.graphics.Color.WHITE,
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardRadius + 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Одноразовое QR-приглашение", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "Одноразовое QR-приглашение",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledValue("ID приглашения", model.shortInviteId, Modifier.weight(1f))
                LabeledValue("Статус", model.stateLabel, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabeledValue("Создан", formatEpoch(model.createdAtEpochMillis), Modifier.weight(1f))
                LabeledValue(
                    "Истекает через",
                    model.expiresInLabel,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

private fun com.disser.kraken.invite.InviteQrCodeMatrix.toBitmap(
    darkColor: Int,
    lightColor: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bitmap.setPixel(x, y, if (isDark(x, y)) darkColor else lightColor)
        }
    }
    return bitmap
}
