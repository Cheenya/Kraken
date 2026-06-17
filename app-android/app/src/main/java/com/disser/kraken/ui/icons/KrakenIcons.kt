package com.disser.kraken.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.disser.kraken.navigation.KrakenRoute

object KrakenIcons {
    val Back = icon("KrakenBack") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            lineTo(7.8f, 11f)
            lineTo(13.1f, 5.7f)
            lineTo(11.7f, 4.3f)
            lineTo(4f, 12f)
            lineTo(11.7f, 19.7f)
            lineTo(13.1f, 18.3f)
            lineTo(7.8f, 13f)
            lineTo(20f, 13f)
            close()
        }
    }

    val Home = icon("KrakenHome") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 11f)
            lineTo(12f, 4f)
            lineTo(20f, 11f)
            lineTo(18.2f, 12.8f)
            lineTo(17f, 11.7f)
            lineTo(17f, 20f)
            lineTo(7f, 20f)
            lineTo(7f, 11.7f)
            lineTo(5.8f, 12.8f)
            close()
            moveTo(10f, 20f)
            lineTo(14f, 20f)
            lineTo(14f, 14f)
            lineTo(10f, 14f)
            close()
        }
    }

    val Contacts = icon("KrakenContacts") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.2f, 4f)
            curveTo(6f, 4f, 4.2f, 5.8f, 4.2f, 8f)
            curveTo(4.2f, 10.2f, 6f, 12f, 8.2f, 12f)
            curveTo(10.4f, 12f, 12.2f, 10.2f, 12.2f, 8f)
            curveTo(12.2f, 5.8f, 10.4f, 4f, 8.2f, 4f)
            close()
            moveTo(15.8f, 5.5f)
            curveTo(14.1f, 5.5f, 12.8f, 6.8f, 12.8f, 8.5f)
            curveTo(12.8f, 10.2f, 14.1f, 11.5f, 15.8f, 11.5f)
            curveTo(17.5f, 11.5f, 18.8f, 10.2f, 18.8f, 8.5f)
            curveTo(18.8f, 6.8f, 17.5f, 5.5f, 15.8f, 5.5f)
            close()
            moveTo(2.5f, 20f)
            curveTo(2.7f, 16.1f, 5.2f, 14f, 8.2f, 14f)
            curveTo(11.2f, 14f, 13.7f, 16.1f, 13.9f, 20f)
            close()
            moveTo(13.2f, 20f)
            curveTo(13.6f, 17.8f, 12.9f, 16.1f, 11.8f, 14.8f)
            curveTo(12.9f, 14.1f, 14.3f, 13.7f, 15.8f, 13.7f)
            curveTo(18.6f, 13.7f, 21.1f, 16f, 21.5f, 20f)
            close()
        }
    }

    val Realms = icon("KrakenRealms") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            lineTo(20f, 7.5f)
            lineTo(20f, 16.5f)
            lineTo(12f, 21f)
            lineTo(4f, 16.5f)
            lineTo(4f, 7.5f)
            close()
            moveTo(12f, 6.3f)
            lineTo(7f, 9.1f)
            lineTo(12f, 11.9f)
            lineTo(17f, 9.1f)
            close()
            moveTo(6.5f, 11.3f)
            lineTo(6.5f, 15.3f)
            lineTo(11f, 17.8f)
            lineTo(11f, 13.8f)
            close()
            moveTo(17.5f, 11.3f)
            lineTo(13f, 13.8f)
            lineTo(13f, 17.8f)
            lineTo(17.5f, 15.3f)
            close()
        }
    }

    val Settings = icon("KrakenSettings") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 3f)
            lineTo(13f, 3f)
            lineTo(13.6f, 5.2f)
            lineTo(15.3f, 5.9f)
            lineTo(17.3f, 4.8f)
            lineTo(18.8f, 6.3f)
            lineTo(17.7f, 8.3f)
            lineTo(18.4f, 10f)
            lineTo(20.5f, 10.6f)
            lineTo(20.5f, 12.8f)
            lineTo(18.4f, 13.4f)
            lineTo(17.7f, 15.1f)
            lineTo(18.8f, 17.1f)
            lineTo(17.3f, 18.6f)
            lineTo(15.3f, 17.5f)
            lineTo(13.6f, 18.2f)
            lineTo(13f, 20.5f)
            lineTo(11f, 20.5f)
            lineTo(10.4f, 18.2f)
            lineTo(8.7f, 17.5f)
            lineTo(6.7f, 18.6f)
            lineTo(5.2f, 17.1f)
            lineTo(6.3f, 15.1f)
            lineTo(5.6f, 13.4f)
            lineTo(3.5f, 12.8f)
            lineTo(3.5f, 10.6f)
            lineTo(5.6f, 10f)
            lineTo(6.3f, 8.3f)
            lineTo(5.2f, 6.3f)
            lineTo(6.7f, 4.8f)
            lineTo(8.7f, 5.9f)
            lineTo(10.4f, 5.2f)
            close()
            moveTo(9f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 15f)
            lineTo(9f, 15f)
            close()
        }
    }

    val Search = icon("KrakenSearch") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10.8f, 5f)
            curveTo(7.6f, 5f, 5f, 7.6f, 5f, 10.8f)
            curveTo(5f, 14f, 7.6f, 16.6f, 10.8f, 16.6f)
            curveTo(14f, 16.6f, 16.6f, 14f, 16.6f, 10.8f)
            curveTo(16.6f, 7.6f, 14f, 5f, 10.8f, 5f)
            close()
            moveTo(15f, 15f)
            lineTo(20f, 20f)
        }
    }

    val Close = icon("KrakenClose") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 7f)
            lineTo(17f, 17f)
            moveTo(17f, 7f)
            lineTo(7f, 17f)
        }
    }

    val Language = icon("KrakenLanguage") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 7f)
            lineTo(11.5f, 7f)
            quadTo(10.8f, 10.1f, 8.7f, 12.7f)
            quadTo(10f, 13.8f, 11.8f, 14.7f)
            lineTo(10.8f, 16.4f)
            quadTo(8.8f, 15.4f, 7.3f, 14.1f)
            quadTo(6f, 15.3f, 4.3f, 16.4f)
            lineTo(3.2f, 14.7f)
            quadTo(4.7f, 13.8f, 5.8f, 12.7f)
            quadTo(4.8f, 11.4f, 4.1f, 9.9f)
            lineTo(6.1f, 9.9f)
            quadTo(6.6f, 10.7f, 7.3f, 11.5f)
            quadTo(8.6f, 9.8f, 9.2f, 7f)
            lineTo(4f, 7f)
            close()
            moveTo(15.2f, 8f)
            lineTo(17.5f, 8f)
            lineTo(21f, 19f)
            lineTo(18.9f, 19f)
            lineTo(18.2f, 16.8f)
            lineTo(14.4f, 16.8f)
            lineTo(13.7f, 19f)
            lineTo(11.6f, 19f)
            close()
            moveTo(15f, 15f)
            lineTo(17.6f, 15f)
            lineTo(16.3f, 10.9f)
            close()
        }
    }

    val Invite = icon("KrakenInvite") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 5f)
            lineTo(10f, 5f)
            lineTo(10f, 10f)
            lineTo(5f, 10f)
            close()
            moveTo(14f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 10f)
            lineTo(14f, 10f)
            close()
            moveTo(5f, 14f)
            lineTo(10f, 14f)
            lineTo(10f, 19f)
            lineTo(5f, 19f)
            close()
            moveTo(14f, 14f)
            lineTo(16f, 14f)
            lineTo(16f, 16f)
            lineTo(19f, 16f)
            lineTo(19f, 19f)
            lineTo(17f, 19f)
            lineTo(17f, 18f)
            lineTo(14f, 18f)
            close()
        }
    }

    val Import = icon("KrakenImport") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 4f)
            lineTo(13f, 4f)
            lineTo(13f, 12.4f)
            lineTo(16.2f, 9.2f)
            lineTo(17.6f, 10.6f)
            lineTo(12f, 16.2f)
            lineTo(6.4f, 10.6f)
            lineTo(7.8f, 9.2f)
            lineTo(11f, 12.4f)
            close()
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
        }
    }

    val Chat = icon("KrakenChat") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 16f)
            lineTo(14f, 16f)
            lineTo(9f, 20f)
            lineTo(9.8f, 16f)
            lineTo(4f, 16f)
            close()
            moveTo(7f, 8.5f)
            lineTo(17f, 8.5f)
            lineTo(17f, 10.2f)
            lineTo(7f, 10.2f)
            close()
            moveTo(7f, 12f)
            lineTo(14f, 12f)
            lineTo(14f, 13.7f)
            lineTo(7f, 13.7f)
            close()
        }
    }

    val Channels = icon("KrakenChannels") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 6f)
            lineTo(20f, 3.8f)
            lineTo(20f, 20.2f)
            lineTo(4f, 18f)
            lineTo(4f, 14.5f)
            lineTo(7f, 14.5f)
            lineTo(7f, 9.5f)
            lineTo(4f, 9.5f)
            close()
            moveTo(9f, 9.2f)
            lineTo(9f, 14.8f)
            lineTo(17.5f, 16f)
            lineTo(17.5f, 8f)
            close()
        }
    }

    val MeshStatus = icon("KrakenMeshStatus") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 4f)
            lineTo(14f, 4f)
            lineTo(14f, 8f)
            lineTo(10f, 8f)
            close()
            moveTo(4f, 16f)
            lineTo(8f, 16f)
            lineTo(8f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(16f, 16f)
            lineTo(20f, 16f)
            lineTo(20f, 20f)
            lineTo(16f, 20f)
            close()
            moveTo(11f, 8f)
            lineTo(13f, 8f)
            lineTo(18f, 16f)
            lineTo(16f, 16f)
            lineTo(12f, 10f)
            lineTo(8f, 16f)
            lineTo(6f, 16f)
            close()
        }
    }

    val Research = icon("KrakenResearch") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 4f)
            lineTo(16f, 4f)
            lineTo(16f, 6f)
            lineTo(15f, 6f)
            lineTo(15f, 10f)
            lineTo(20f, 18.8f)
            lineTo(18.8f, 21f)
            lineTo(5.2f, 21f)
            lineTo(4f, 18.8f)
            lineTo(9f, 10f)
            lineTo(9f, 6f)
            lineTo(8f, 6f)
            close()
            moveTo(10.6f, 11f)
            lineTo(7f, 18f)
            lineTo(17f, 18f)
            lineTo(13.4f, 11f)
            close()
        }
    }

    val Identity = icon("KrakenIdentity") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 4f)
            lineTo(16f, 8f)
            lineTo(12f, 12f)
            lineTo(8f, 8f)
            close()
            moveTo(4.5f, 20.5f)
            lineTo(4.5f, 18.8f)
            lineTo(12f, 14f)
            lineTo(19.5f, 18.8f)
            lineTo(19.5f, 20.5f)
            close()
            moveTo(18f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 8f)
            lineTo(22f, 8f)
            lineTo(22f, 10f)
            lineTo(20f, 10f)
            lineTo(20f, 13f)
            lineTo(18f, 13f)
            close()
        }
    }

    val Warning = icon("KrakenWarning") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            lineTo(22f, 20f)
            lineTo(2f, 20f)
            close()
            moveTo(11f, 8f)
            lineTo(13f, 8f)
            lineTo(13f, 14f)
            lineTo(11f, 14f)
            close()
            moveTo(11f, 16f)
            lineTo(13f, 16f)
            lineTo(13f, 18f)
            lineTo(11f, 18f)
            close()
        }
    }

    val Send = icon("KrakenSend") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 4.2f)
            lineTo(21.2f, 12f)
            lineTo(3f, 19.8f)
            lineTo(5.2f, 13.2f)
            lineTo(12.8f, 12f)
            lineTo(5.2f, 10.8f)
            close()
        }
    }

    val ShieldOutline = icon("KrakenShieldOutline") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3.5f)
            lineTo(19f, 6.5f)
            lineTo(18.2f, 14.2f)
            quadTo(17.6f, 18f, 12f, 21f)
            quadTo(6.4f, 18f, 5.8f, 14.2f)
            lineTo(5f, 6.5f)
            close()
        }
    }

    val WifiOutline = icon("KrakenWifiOutline") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4.8f, 10.5f)
            quadTo(12f, 4.4f, 19.2f, 10.5f)
            moveTo(8.1f, 13.8f)
            quadTo(12f, 10.6f, 15.9f, 13.8f)
            moveTo(10.5f, 17.1f)
            quadTo(12f, 15.9f, 13.5f, 17.1f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 20f)
            lineTo(10.6f, 18.6f)
            lineTo(12f, 17.2f)
            lineTo(13.4f, 18.6f)
            close()
        }
    }

    val LockOutline = icon("KrakenLockOutline") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6.8f, 10.4f)
            lineTo(6.8f, 8.2f)
            quadTo(6.8f, 4.9f, 12f, 4.9f)
            quadTo(17.2f, 4.9f, 17.2f, 8.2f)
            lineTo(17.2f, 10.4f)
            moveTo(5f, 10.4f)
            lineTo(19f, 10.4f)
            lineTo(19f, 20f)
            lineTo(5f, 20f)
            close()
            moveTo(12f, 14.1f)
            lineTo(12f, 16.8f)
        }
    }

    val PrivacyLock = icon("KrakenPrivacyLock") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 10f)
            lineTo(7f, 7f)
            lineTo(9f, 5f)
            lineTo(15f, 5f)
            lineTo(17f, 7f)
            lineTo(17f, 10f)
            lineTo(19f, 10f)
            lineTo(19f, 21f)
            lineTo(5f, 21f)
            lineTo(5f, 10f)
            close()
            moveTo(9.2f, 10f)
            lineTo(14.8f, 10f)
            lineTo(14.8f, 8f)
            lineTo(14f, 7.2f)
            lineTo(10f, 7.2f)
            lineTo(9.2f, 8f)
            close()
            moveTo(11f, 15f)
            lineTo(13f, 15f)
            lineTo(13f, 18f)
            lineTo(11f, 18f)
            close()
        }
    }

    val RelayCourier = icon("KrakenRelayCourier") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 7f)
            lineTo(13f, 4f)
            lineTo(13f, 8f)
            lineTo(20f, 8f)
            lineTo(11f, 20f)
            lineTo(11f, 15f)
            lineTo(4f, 15f)
            close()
            moveTo(13f, 10f)
            lineTo(13f, 14f)
            lineTo(15.8f, 10f)
            close()
        }
    }

    val Retry = icon("KrakenRetry") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(18.8f, 8.2f)
            quadTo(15.9f, 4.8f, 11.8f, 5.2f)
            quadTo(7.2f, 5.7f, 5.2f, 9.8f)
            moveTo(18.8f, 8.2f)
            lineTo(18.5f, 4.8f)
            moveTo(18.8f, 8.2f)
            lineTo(15.3f, 8.2f)
            moveTo(5.2f, 15.8f)
            quadTo(8.1f, 19.2f, 12.2f, 18.8f)
            quadTo(16.8f, 18.3f, 18.8f, 14.2f)
            moveTo(5.2f, 15.8f)
            lineTo(5.5f, 19.2f)
            moveTo(5.2f, 15.8f)
            lineTo(8.7f, 15.8f)
        }
    }

    val Reply = icon("KrakenReply") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10f, 7f)
            lineTo(4.5f, 12f)
            lineTo(10f, 17f)
            moveTo(5.2f, 12f)
            lineTo(14.4f, 12f)
            quadTo(18.8f, 12f, 20f, 16.5f)
        }
    }

    val Copy = icon("KrakenCopy") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 8f)
            lineTo(18f, 8f)
            lineTo(18f, 18f)
            lineTo(8f, 18f)
            close()
            moveTo(6f, 15f)
            lineTo(5f, 15f)
            lineTo(5f, 5f)
            lineTo(15f, 5f)
            lineTo(15f, 6f)
        }
    }

    val Star = icon("KrakenStar") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3.8f)
            lineTo(14.4f, 9f)
            lineTo(20f, 9.6f)
            lineTo(15.8f, 13.3f)
            lineTo(17f, 18.8f)
            lineTo(12f, 16f)
            lineTo(7f, 18.8f)
            lineTo(8.2f, 13.3f)
            lineTo(4f, 9.6f)
            lineTo(9.6f, 9f)
            close()
        }
    }

    val Check = icon("KrakenCheck") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 12.6f)
            lineTo(10.1f, 17.6f)
            lineTo(19.5f, 6.8f)
        }
    }

    val Delete = icon("KrakenDelete") {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 7f)
            lineTo(19f, 7f)
            moveTo(9f, 7f)
            lineTo(9.7f, 5f)
            lineTo(14.3f, 5f)
            lineTo(15f, 7f)
            moveTo(7.5f, 9f)
            lineTo(8.4f, 19f)
            lineTo(15.6f, 19f)
            lineTo(16.5f, 9f)
            moveTo(10.3f, 11f)
            lineTo(10.6f, 17f)
            moveTo(13.7f, 11f)
            lineTo(13.4f, 17f)
        }
    }

    val MoreVert = icon("KrakenMoreVert") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 6f)
            curveTo(10.9f, 6f, 10f, 5.1f, 10f, 4f)
            curveTo(10f, 2.9f, 10.9f, 2f, 12f, 2f)
            curveTo(13.1f, 2f, 14f, 2.9f, 14f, 4f)
            curveTo(14f, 5.1f, 13.1f, 6f, 12f, 6f)
            close()
            moveTo(12f, 14f)
            curveTo(10.9f, 14f, 10f, 13.1f, 10f, 12f)
            curveTo(10f, 10.9f, 10.9f, 10f, 12f, 10f)
            curveTo(13.1f, 10f, 14f, 10.9f, 14f, 12f)
            curveTo(14f, 13.1f, 13.1f, 14f, 12f, 14f)
            close()
            moveTo(12f, 22f)
            curveTo(10.9f, 22f, 10f, 21.1f, 10f, 20f)
            curveTo(10f, 18.9f, 10.9f, 18f, 12f, 18f)
            curveTo(13.1f, 18f, 14f, 18.9f, 14f, 20f)
            curveTo(14f, 21.1f, 13.1f, 22f, 12f, 22f)
            close()
        }
    }
}

fun KrakenRoute.krakenIcon(): ImageVector = when (this) {
    KrakenRoute.Welcome -> KrakenIcons.Home
    KrakenRoute.Home -> KrakenIcons.Home
    KrakenRoute.CreateIdentity -> KrakenIcons.Identity
    KrakenRoute.MyQr -> KrakenIcons.Invite
    KrakenRoute.ImportInvite -> KrakenIcons.Import
    KrakenRoute.QrScanner -> KrakenIcons.Import
    KrakenRoute.Contacts -> KrakenIcons.Contacts
    KrakenRoute.Realms -> KrakenIcons.Realms
    KrakenRoute.RealmManage -> KrakenIcons.Realms
    KrakenRoute.PendingApprovals -> KrakenIcons.PrivacyLock
    KrakenRoute.Chat -> KrakenIcons.Chat
    KrakenRoute.SavedMessages -> KrakenIcons.Star
    KrakenRoute.ContactProfile -> KrakenIcons.Contacts
    KrakenRoute.Channels -> KrakenIcons.Channels
    KrakenRoute.MeshStatus -> KrakenIcons.MeshStatus
    KrakenRoute.TwoPhoneChecklist -> KrakenIcons.MeshStatus
    KrakenRoute.Settings -> KrakenIcons.Settings
    KrakenRoute.ThemePicker -> KrakenIcons.Settings
    KrakenRoute.Research -> KrakenIcons.Research
    KrakenRoute.UiLab -> KrakenIcons.Research
}

private fun icon(
    name: String,
    block: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()
