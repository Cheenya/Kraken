package com.disser.kraken.ui.icons.experimental

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

enum class ExperimentalKrakenIcon(val label: String) {
    Home("Home"),
    Contacts("Contacts"),
    Realms("Realms"),
    Settings("Settings"),
    QrInvite("QR / Invite"),
    Import("Import"),
    Chat("Chat"),
    Channels("Channels"),
    MeshStatus("Mesh Status"),
    Research("Research"),
    Identity("Identity"),
    Warning("Warning"),
    LockPrivacy("Lock / Privacy"),
    RelayCourier("Relay / Courier"),
    Back("Back"),
    Copy("Copy"),
    Check("Check"),
    Close("Close"),
    Pause("Pause"),
    Archive("Archive"),
    Leave("Leave"),
    LinkHandshake("Link / Handshake"),
}

data class ExperimentalIconConceptSet(
    val name: String,
    val intent: String,
    val icons: List<Pair<ExperimentalKrakenIcon, ImageVector>>,
)

object ExperimentalKrakenIcons {
    val MinimalLine = ExperimentalIconConceptSet(
        name = "Minimal Line",
        intent = "Thin rounded strokes for readable messenger navigation.",
        icons = ExperimentalKrakenIcon.entries.map { it to minimalLineIcon(it) },
    )

    val GlassGlyph = ExperimentalIconConceptSet(
        name = "Glass Glyph",
        intent = "Filled duotone-like glyphs for dark glassy primary actions.",
        icons = ExperimentalKrakenIcon.entries.map { it to glassGlyphIcon(it) },
    )

    val AbyssGeometry = ExperimentalIconConceptSet(
        name = "Abyss / Kraken Geometry",
        intent = "Geometric marine hints with subtle coils and no mascot.",
        icons = ExperimentalKrakenIcon.entries.map { it to abyssGeometryIcon(it) },
    )

    val AllSets = listOf(MinimalLine, GlassGlyph, AbyssGeometry)
}

private fun minimalLineIcon(icon: ExperimentalKrakenIcon): ImageVector =
    vector("MinimalLine${icon.name}") {
        linePath {
            symbol(icon, filled = false)
        }
    }

private fun glassGlyphIcon(icon: ExperimentalKrakenIcon): ImageVector =
    vector("GlassGlyph${icon.name}") {
        path(fill = SolidColor(Color.Black), fillAlpha = 0.34f) {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            symbol(icon, filled = true)
        }
    }

private fun abyssGeometryIcon(icon: ExperimentalKrakenIcon): ImageVector =
    vector("AbyssGeometry${icon.name}") {
        path(fill = SolidColor(Color.Black), fillAlpha = 0.28f) {
            moveTo(12f, 3f)
            lineTo(20f, 8f)
            lineTo(18f, 18f)
            lineTo(12f, 21f)
            lineTo(6f, 18f)
            lineTo(4f, 8f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            symbol(icon, filled = true, abyss = true)
        }
    }

private fun Builder.linePath(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.symbol(
    icon: ExperimentalKrakenIcon,
    filled: Boolean,
    abyss: Boolean = false,
) {
    val inset = if (abyss) 1f else 0f
    when (icon) {
        ExperimentalKrakenIcon.Home -> {
            moveTo(5f, 12f)
            lineTo(12f, 6f)
            lineTo(19f, 12f)
            lineTo(17f, 12f)
            lineTo(17f, 19f)
            lineTo(7f, 19f)
            lineTo(7f, 12f)
            closeIfFilled(filled)
        }
        ExperimentalKrakenIcon.Contacts -> {
            diamond(8f, 8f, 3f, filled)
            diamond(16f, 9f, 2.6f, filled)
            moveTo(4f, 19f)
            lineTo(8f, 15f)
            lineTo(12f, 19f)
            moveTo(13f, 19f)
            lineTo(16f, 16f)
            lineTo(20f, 19f)
        }
        ExperimentalKrakenIcon.Realms -> {
            moveTo(12f, 4f + inset)
            lineTo(19f, 8f)
            lineTo(19f, 16f)
            lineTo(12f, 20f - inset)
            lineTo(5f, 16f)
            lineTo(5f, 8f)
            closeIfFilled(filled)
            moveTo(7f, 10f)
            lineTo(12f, 13f)
            lineTo(17f, 10f)
        }
        ExperimentalKrakenIcon.Settings -> {
            diamond(12f, 12f, 7f, filled)
            diamond(12f, 12f, 3f, filled)
        }
        ExperimentalKrakenIcon.QrInvite -> {
            square(5f, 5f, 5f, filled)
            square(14f, 5f, 5f, filled)
            square(5f, 14f, 5f, filled)
            moveTo(14f, 15f)
            lineTo(19f, 15f)
            lineTo(19f, 19f)
            lineTo(16f, 19f)
        }
        ExperimentalKrakenIcon.Import -> {
            moveTo(12f, 4f)
            lineTo(12f, 15f)
            moveTo(7f, 10f)
            lineTo(12f, 15f)
            lineTo(17f, 10f)
            moveTo(5f, 19f)
            lineTo(19f, 19f)
        }
        ExperimentalKrakenIcon.Chat -> {
            moveTo(5f, 6f)
            lineTo(19f, 6f)
            lineTo(19f, 16f)
            lineTo(13f, 16f)
            lineTo(9f, 20f)
            lineTo(9f, 16f)
            lineTo(5f, 16f)
            closeIfFilled(filled)
        }
        ExperimentalKrakenIcon.Channels -> {
            moveTo(5f, 7f)
            lineTo(19f, 4f)
            lineTo(19f, 20f)
            lineTo(5f, 17f)
            closeIfFilled(filled)
            moveTo(8f, 10f)
            lineTo(14f, 9f)
            moveTo(8f, 14f)
            lineTo(14f, 15f)
        }
        ExperimentalKrakenIcon.MeshStatus -> {
            square(10f, 4f, 4f, filled)
            square(4f, 16f, 4f, filled)
            square(16f, 16f, 4f, filled)
            moveTo(12f, 8f)
            lineTo(6f, 16f)
            moveTo(12f, 8f)
            lineTo(18f, 16f)
        }
        ExperimentalKrakenIcon.Research -> {
            moveTo(9f, 5f)
            lineTo(15f, 5f)
            moveTo(10f, 5f)
            lineTo(10f, 10f)
            lineTo(6f, 19f)
            lineTo(18f, 19f)
            lineTo(14f, 10f)
            lineTo(14f, 5f)
        }
        ExperimentalKrakenIcon.Identity -> {
            diamond(12f, 7f, 4f, filled)
            moveTo(5f, 20f)
            lineTo(12f, 14f)
            lineTo(19f, 20f)
        }
        ExperimentalKrakenIcon.Warning -> {
            moveTo(12f, 4f)
            lineTo(21f, 20f)
            lineTo(3f, 20f)
            closeIfFilled(filled)
            moveTo(12f, 9f)
            lineTo(12f, 14f)
            moveTo(12f, 17f)
            lineTo(12.1f, 17f)
        }
        ExperimentalKrakenIcon.LockPrivacy -> {
            moveTo(7f, 11f)
            lineTo(17f, 11f)
            lineTo(17f, 20f)
            lineTo(7f, 20f)
            closeIfFilled(filled)
            moveTo(9f, 11f)
            lineTo(9f, 8f)
            lineTo(11f, 6f)
            lineTo(13f, 6f)
            lineTo(15f, 8f)
            lineTo(15f, 11f)
        }
        ExperimentalKrakenIcon.RelayCourier -> {
            moveTo(4f, 8f)
            lineTo(13f, 5f)
            lineTo(12f, 10f)
            lineTo(20f, 10f)
            lineTo(10f, 20f)
            lineTo(11f, 14f)
            lineTo(4f, 14f)
            closeIfFilled(filled)
        }
        ExperimentalKrakenIcon.Back -> {
            moveTo(16f, 5f)
            lineTo(8f, 12f)
            lineTo(16f, 19f)
        }
        ExperimentalKrakenIcon.Copy -> {
            square(8f, 5f, 10f, filled)
            square(5f, 9f, 10f, filled)
        }
        ExperimentalKrakenIcon.Check -> {
            moveTo(5f, 12f)
            lineTo(10f, 17f)
            lineTo(19f, 7f)
        }
        ExperimentalKrakenIcon.Close -> {
            moveTo(7f, 7f)
            lineTo(17f, 17f)
            moveTo(17f, 7f)
            lineTo(7f, 17f)
        }
        ExperimentalKrakenIcon.Pause -> {
            square(7f, 5f, 4f, filled)
            square(13f, 5f, 4f, filled)
        }
        ExperimentalKrakenIcon.Archive -> {
            moveTo(5f, 8f)
            lineTo(19f, 8f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            closeIfFilled(filled)
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 8f)
            lineTo(4f, 8f)
            closeIfFilled(filled)
        }
        ExperimentalKrakenIcon.Leave -> {
            moveTo(6f, 5f)
            lineTo(13f, 5f)
            lineTo(13f, 19f)
            lineTo(6f, 19f)
            closeIfFilled(filled)
            moveTo(12f, 12f)
            lineTo(20f, 12f)
            moveTo(17f, 9f)
            lineTo(20f, 12f)
            lineTo(17f, 15f)
        }
        ExperimentalKrakenIcon.LinkHandshake -> {
            moveTo(6f, 12f)
            lineTo(10f, 8f)
            lineTo(14f, 12f)
            moveTo(10f, 16f)
            lineTo(14f, 12f)
            lineTo(18f, 16f)
        }
    }
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.closeIfFilled(filled: Boolean) {
    if (filled) close()
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.diamond(
    centerX: Float,
    centerY: Float,
    radius: Float,
    filled: Boolean,
) {
    moveTo(centerX, centerY - radius)
    lineTo(centerX + radius, centerY)
    lineTo(centerX, centerY + radius)
    lineTo(centerX - radius, centerY)
    closeIfFilled(filled)
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.square(
    left: Float,
    top: Float,
    size: Float,
    filled: Boolean,
) {
    moveTo(left, top)
    lineTo(left + size, top)
    lineTo(left + size, top + size)
    lineTo(left, top + size)
    closeIfFilled(filled)
}

private fun vector(
    name: String,
    block: Builder.() -> Unit,
): ImageVector = Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()
