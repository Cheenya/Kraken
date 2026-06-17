package com.disser.kraken.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class KrakenThemeTokens(
    val colorScheme: ColorScheme,
    val cardRadius: Dp,
    val controlRadius: Dp,
    val screenPadding: Dp,
    val cardSpacing: Dp,
    val cardContentPaddingHorizontal: Dp,
    val cardContentPaddingVertical: Dp,
    val contentSpacing: Dp,
    val listRowMinHeight: Dp,
    val listRowPaddingHorizontal: Dp,
    val listRowPaddingVertical: Dp,
    val listRowGap: Dp,
    val avatarSize: Dp,
    val borderWidth: Dp,
    val densityMode: KrakenDensityMode,
    val surfaceStyle: KrakenSurfaceStyle,
)
