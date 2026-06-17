package com.disser.kraken.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StateBadge(label: String, modifier: Modifier = Modifier) {
    val badgeColors = stateBadgeColors(label)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = badgeColors.container,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = badgeColors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private data class BadgeColors(
    val container: Color,
    val content: Color,
)

@Composable
private fun stateBadgeColors(label: String): BadgeColors {
    val normalized = label.uppercase()
    return when {
        normalized.contains("ACTIVE") || normalized.contains("APPROVED") ||
            normalized.contains("АКТИВ") || normalized.contains("ОДОБР") ->
            BadgeColors(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        normalized.contains("PENDING") || normalized.contains("HANDSHAKE") ||
            normalized.contains("ОЖИД") || normalized.contains("РУКОПОЖ") ->
            BadgeColors(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        normalized.contains("BLOCKED") || normalized.contains("UNLINK") ||
            normalized.contains("LEFT") || normalized.contains("REJECTED") ||
            normalized.contains("БЛОК") || normalized.contains("ОТВЯЗ") ||
            normalized.contains("ЗАВЕРШ") || normalized.contains("ОТКЛОН") ->
            BadgeColors(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        normalized.contains("PAUSED") || normalized.contains("ARCHIVED") || normalized.contains("MUTED") ||
            normalized.contains("ПАУЗ") || normalized.contains("АРХИВ") || normalized.contains("ЗАГЛУШ") ->
            BadgeColors(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        else ->
            BadgeColors(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
