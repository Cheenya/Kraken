package com.disser.kraken

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.disser.kraken.mesh.KrakenMessageNotifier
import com.disser.kraken.navigation.KrakenApp
import com.disser.kraken.ui.screens.BrandedLaunchScreen
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var currentIntentState: MutableState<Intent?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Kraken)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        Locale.setDefault(Locale("ru", "RU"))
        setContent {
            val intentState = remember { mutableStateOf(intent) }
            val skipLaunch = KrakenMessageNotifier.isMessageNotificationIntent(intentState.value)
            var launchReady by rememberSaveable { mutableStateOf(skipLaunch) }
            currentIntentState = intentState
            BrandedLaunchScreen(
                skipLaunch = skipLaunch,
                launchReady = launchReady,
            ) {
                KrakenApp(
                    launchIntent = intentState.value,
                    onLaunchReady = { launchReady = true },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState?.value = intent
    }
}
