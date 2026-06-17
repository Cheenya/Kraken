package com.disser.kraken.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disser.kraken.message.ChatPreferencesStore

@Composable
fun ChatBackgroundPickerDialog(
    title: String,
    selectedBackgroundKey: String?,
    globalBackgroundKey: String? = null,
    includeSystemDefault: Boolean,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (includeSystemDefault) {
                    ChatBackgroundOptionRow(
                        title = "Как в настройках",
                        description = ChatPreferencesStore.backgroundTitle(globalBackgroundKey),
                        selected = selectedBackgroundKey == null,
                        swatchColor = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { onSelect(null) },
                    )
                }
                ChatPreferencesStore.BACKGROUND_PRESETS.forEach { preset ->
                    ChatBackgroundOptionRow(
                        title = preset.title,
                        description = preset.description,
                        selected = selectedBackgroundKey == preset.key,
                        swatchColor = chatBackgroundSwatchColor(preset.key),
                        onClick = { onSelect(preset.key) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun ChatBackgroundOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    swatchColor: Color,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $description"
            },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = swatchColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    " ",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Text(
                    "Выбран",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun chatBackgroundSwatchColor(backgroundKey: String): Color =
    when (backgroundKey) {
        "solid_dark" -> Color(0xFF101418)
        "amoled_black" -> Color.Black
        "deep_signal" -> Color(0xFF071B1D)
        else -> Color(0xFF101820)
    }
