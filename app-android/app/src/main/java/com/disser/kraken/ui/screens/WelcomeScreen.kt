package com.disser.kraken.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.disser.kraken.R
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.ui.icons.KrakenIcons
import com.disser.kraken.ui.icons.krakenIcon

@Composable
fun WelcomeScreen(
    navController: NavHostController,
    hasLocalIdentity: Boolean,
) {
    val primaryAction = if (hasLocalIdentity) {
        WelcomeAction(
            label = "ОТКРЫТЬ KRAKEN",
            route = KrakenRoute.Chat,
        )
    } else {
        WelcomeAction(
            label = "СОЗДАТЬ ЛИЧНОСТЬ",
            route = KrakenRoute.CreateIdentity,
        )
    }
    val secondaryAction = WelcomeAction(
        label = if (hasLocalIdentity) "МОЙ QR-КОД" else "ОБЗОР",
        route = if (hasLocalIdentity) KrakenRoute.MyQr else KrakenRoute.Home,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF02080B)),
    ) {
        Image(
            painter = painterResource(R.drawable.kraken_start_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-148).dp)
                .width(390.dp)
                .height(450.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xD402080B),
                            Color(0x8802080B),
                            Color.Transparent,
                        ),
                        radius = 460f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE02080B),
                            Color(0x6602080B),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.52f))
            Column(
                modifier = Modifier
                    .offset(y = (-28).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.kraken_favicon_512),
                    contentDescription = "Kraken",
                    modifier = Modifier
                        .size(132.dp)
                        .clip(RoundedCornerShape(999.dp)),
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "K R A K E N",
                    color = Color(0xFFE9EDF0),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 7.6.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "M E S S E N G E R",
                    color = Color(0xFF22E4DD),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 5.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "ПРИВАТНО  •  ЛОКАЛЬНО  •  СВОБОДНО",
                    color = Color(0xFF23DAD4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(36.dp))
            Button(
                onClick = { navController.navigate(primaryAction.route.route) },
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16BBB8),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = primaryAction.route.krakenIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = primaryAction.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { navController.navigate(secondaryAction.route.route) },
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF28C8C2)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFE9EDF0),
                    containerColor = Color(0x330A1A20),
                ),
            ) {
                Icon(
                    imageVector = secondaryAction.route.krakenIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = secondaryAction.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                )
            }
            Spacer(Modifier.weight(0.78f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                StartPrinciple(
                    icon = KrakenIcons.ShieldOutline,
                    label = "ПОЛНАЯ\nПРИВАТНОСТЬ",
                    modifier = Modifier.width(82.dp),
                )
                StartPrinciple(
                    icon = KrakenIcons.WifiOutline,
                    label = "БЕЗ СЕРВЕРОВ\nИ АККАУНТОВ",
                    modifier = Modifier.width(82.dp),
                )
                StartPrinciple(
                    icon = KrakenIcons.LockOutline,
                    label = "ВАШИ ДАННЫЕ\nТОЛЬКО У ВАС",
                    modifier = Modifier.width(82.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class WelcomeAction(
    val label: String,
    val route: KrakenRoute,
)

@Composable
private fun StartPrinciple(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF20DAD4),
                modifier = Modifier.size(23.dp),
            )
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
        )
    }
}
