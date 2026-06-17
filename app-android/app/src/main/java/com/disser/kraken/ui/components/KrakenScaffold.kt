package com.disser.kraken.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.disser.kraken.navigation.KrakenRoute

@Composable
fun KrakenScaffold(
    navController: NavHostController,
    onBottomRouteSelected: (KrakenRoute) -> Unit = {},
    showBottomBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                KrakenBottomBar(navController, onRouteSelected = onBottomRouteSelected)
            }
        },
        content = content,
    )
}
