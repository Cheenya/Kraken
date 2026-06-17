package com.disser.kraken.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens
import com.disser.kraken.ui.theme.KrakenSurfaceStyle

@Composable
fun KrakenSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun KrakenCompactCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalKrakenThemeTokens.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.cardRadius),
        colors = CardDefaults.cardColors(containerColor = krakenSurfaceContainer()),
        border = BorderStroke(tokens.borderWidth, krakenSurfaceBorder()),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = tokens.cardContentPaddingHorizontal,
                vertical = tokens.cardContentPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.contentSpacing),
            content = content,
        )
    }
}

@Composable
fun KrakenStateBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    StateBadge(label = label, modifier = modifier)
}

@Composable
fun KrakenPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(LocalKrakenThemeTokens.current.controlRadius),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun KrakenSecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(LocalKrakenThemeTokens.current.controlRadius),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(7.dp))
        }
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun KrakenListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leadingText: String? = null,
    leadingIcon: ImageVector? = null,
    badge: String? = null,
    trailingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tokens = LocalKrakenThemeTokens.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(
                    title,
                    subtitle,
                    badge ?: trailingText,
                    actionLabel?.let { "$it: $title" },
                ).joinToString(". ")
            },
        shape = RoundedCornerShape(tokens.cardRadius),
        color = krakenSurfaceContainer(),
        tonalElevation = 0.dp,
        border = BorderStroke(tokens.borderWidth, krakenSurfaceBorder()),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = tokens.listRowMinHeight)
                .padding(
                    horizontal = tokens.listRowPaddingHorizontal,
                    vertical = tokens.listRowPaddingVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(tokens.listRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null || leadingText != null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(tokens.avatarSize),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (leadingIcon != null) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(tokens.avatarSize * 0.58f),
                            )
                        } else if (leadingText != null) {
                            Text(
                                leadingText.take(2).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (badge != null) {
                    KrakenStateBadge(badge)
                } else if (trailingText != null) {
                    Text(
                        trailingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(actionLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicalDetailsDisclosure(
    title: String = "Технические детали",
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    KrakenCompactCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть" else "Показать")
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(LocalKrakenThemeTokens.current.contentSpacing),
                content = content,
            )
        }
    }
}

@Composable
private fun krakenSurfaceContainer() =
    when (LocalKrakenThemeTokens.current.surfaceStyle) {
        KrakenSurfaceStyle.SOLID -> MaterialTheme.colorScheme.surfaceVariant
        KrakenSurfaceStyle.GLASS_LIKE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        KrakenSurfaceStyle.CONSOLE -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }

@Composable
private fun krakenSurfaceBorder() =
    when (LocalKrakenThemeTokens.current.surfaceStyle) {
        KrakenSurfaceStyle.SOLID -> MaterialTheme.colorScheme.outlineVariant
        KrakenSurfaceStyle.GLASS_LIKE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)
        KrakenSurfaceStyle.CONSOLE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    }
