package com.disser.kraken.ui.screens.experimental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.SectionHeader
import com.disser.kraken.ui.icons.experimental.ExperimentalIconConceptSet
import com.disser.kraken.ui.icons.experimental.ExperimentalKrakenIcons

@Composable
fun IconLabScreen(modifier: Modifier = Modifier) {
    ScreenContainer(
        title = "Иконки",
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        IconLabContent()
    }
}

@Composable
fun IconLabContent() {
        InfoCard(
            title = "Только для сравнения",
            items = listOf(
                "Эти варианты изолированы от основной навигации.",
                "Это оригинальные Compose-векторы для визуального просмотра.",
                "Ассеты других мессенджеров не копируются.",
            ),
        )
        ExperimentalKrakenIcons.AllSets.forEach { conceptSet ->
            IconConceptSection(conceptSet)
        }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun IconConceptSection(conceptSet: ExperimentalIconConceptSet) {
    SectionHeader(conceptSet.name)
    Text(
        text = conceptSet.intent,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        conceptSet.icons.forEach { (iconKind, imageVector) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.29f)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = iconKind.label,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = iconKind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                )
            }
        }
    }
}
