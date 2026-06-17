package com.disser.kraken.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import com.disser.kraken.R

@Composable
fun BrandedLaunchScreen(
    skipLaunch: Boolean = false,
    launchReady: Boolean = false,
    content: @Composable () -> Unit,
) {
    var showLaunch by rememberSaveable(skipLaunch) { mutableStateOf(!skipLaunch) }

    LaunchedEffect(skipLaunch, launchReady) {
        showLaunch = !skipLaunch && !launchReady
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071115)),
    ) {
        content()
        AnimatedVisibility(
            visible = showLaunch,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            KrakenLaunchArtwork()
        }
    }
}

@Composable
private fun KrakenLaunchArtwork() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.kraken_splash_dark),
            contentDescription = "Kraken Messenger",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF0071115),
                            Color(0x77071115),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
