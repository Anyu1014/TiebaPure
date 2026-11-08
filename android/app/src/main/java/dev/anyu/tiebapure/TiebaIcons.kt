package dev.anyu.tiebapure

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object TiebaIcons {
    private fun outline(name: String, mirrored: Boolean = false, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirrored).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
        }.build()
    val Comment = outline("Comment") { moveTo(5f, 3f); lineTo(19f, 3f); quadTo(22f, 3f, 22f, 6f); lineTo(22f, 15f); quadTo(22f, 18f, 19f, 18f); lineTo(10f, 18f); lineTo(4f, 22f); lineTo(4f, 18f); quadTo(2f, 18f, 2f, 15f); lineTo(2f, 6f); quadTo(2f, 3f, 5f, 3f); close() }
    val Microphone = outline("Microphone") {
        moveTo(9f, 5f); curveTo(9f, 1f, 15f, 1f, 15f, 5f); lineTo(15f, 11f); curveTo(15f, 15f, 9f, 15f, 9f, 11f); close()
        moveTo(5f, 10f); lineTo(5f, 12f); curveTo(5f, 20f, 19f, 20f, 19f, 12f); lineTo(19f, 10f)
        moveTo(12f, 18f); lineTo(12f, 22f); moveTo(9f, 22f); lineTo(15f, 22f)
    }
    val Photo = outline("Photo") {
        moveTo(5f, 3f); lineTo(19f, 3f); quadTo(22f, 3f, 22f, 6f); lineTo(22f, 18f); quadTo(22f, 21f, 19f, 21f); lineTo(5f, 21f); quadTo(2f, 21f, 2f, 18f); lineTo(2f, 6f); quadTo(2f, 3f, 5f, 3f); close()
        moveTo(3f, 17f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(17f, 11f); lineTo(22f, 16f)
        moveTo(7f, 7f); lineTo(7.01f, 7f)
    }
    val Replies = outline("Replies") {
        moveTo(6f, 17f); lineTo(3f, 20f); lineTo(3f, 16f); quadTo(1f, 16f, 1f, 13f); lineTo(1f, 5f); quadTo(1f, 2f, 4f, 2f); lineTo(16f, 2f); quadTo(19f, 2f, 19f, 5f)
        moveTo(11f, 7f); lineTo(20f, 7f); quadTo(23f, 7f, 23f, 10f); lineTo(23f, 18f); quadTo(23f, 21f, 20f, 21f); lineTo(20f, 23f); lineTo(16f, 21f); lineTo(11f, 21f); quadTo(8f, 21f, 8f, 18f); lineTo(8f, 10f); quadTo(8f, 7f, 11f, 7f); close()
        moveTo(12f, 11f); lineTo(19f, 11f); moveTo(12f, 15f); lineTo(17f, 15f)
    }
    val Clock = outline("Clock") {
        moveTo(12f, 2f); curveTo(17.5f, 2f, 22f, 6.5f, 22f, 12f); curveTo(22f, 17.5f, 17.5f, 22f, 12f, 22f); curveTo(6.5f, 22f, 2f, 17.5f, 2f, 12f); curveTo(2f, 6.5f, 6.5f, 2f, 12f, 2f); close(); moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f)
    }
    val Back = outline("Back", true) { moveTo(15.5f, 3f); lineTo(6.5f, 12f); lineTo(15.5f, 21f) }
    val Compose = outline("Compose") { moveTo(13f, 4f); lineTo(6f, 4f); quadTo(3f, 4f, 3f, 7f); lineTo(3f, 18f); quadTo(3f, 21f, 6f, 21f); lineTo(17f, 21f); quadTo(20f, 21f, 20f, 18f); lineTo(20f, 11f); moveTo(10f, 15f); lineTo(11f, 11f); lineTo(20f, 2f); lineTo(23f, 5f); lineTo(14f, 14f); close() }
    val ChevronDown = outline("ChevronDown") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val Pin = ImageVector.Builder("Pin", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 2f); lineTo(17f, 2f); lineTo(17f, 4f); lineTo(16f, 4f); lineTo(16f, 9f)
            curveTo(16f, 11f, 19f, 11f, 19f, 13f); lineTo(19f, 14f); lineTo(13f, 14f)
            lineTo(13f, 21f); lineTo(12f, 23f); lineTo(11f, 21f); lineTo(11f, 14f); lineTo(5f, 14f); lineTo(5f, 13f)
            curveTo(5f, 11f, 8f, 11f, 8f, 9f); lineTo(8f, 4f); lineTo(7f, 4f); close()
        }
    }.build()
    val Star = outline("Star") { moveTo(12f, 2f); lineTo(15.1f, 8.2f); lineTo(22f, 9.2f); lineTo(17f, 14.1f); lineTo(18.2f, 21f); lineTo(12f, 17.8f); lineTo(5.8f, 21f); lineTo(7f, 14.1f); lineTo(2f, 9.2f); lineTo(8.9f, 8.2f); close() }
    val Copy = outline("Copy") { moveTo(8f, 7f); lineTo(20f, 7f); lineTo(20f, 22f); lineTo(8f, 22f); close(); moveTo(15f, 4f); lineTo(15f, 2f); lineTo(3f, 2f); lineTo(3f, 17f); lineTo(5f, 17f) }
    val Save = outline("Save") { moveTo(5f, 2f); lineTo(14f, 2f); lineTo(20f, 8f); lineTo(20f, 22f); lineTo(5f, 22f); close(); moveTo(14f, 2f); lineTo(14f, 8f); lineTo(20f, 8f); moveTo(12f, 11f); lineTo(12f, 18f); moveTo(9f, 15f); lineTo(12f, 18f); lineTo(15f, 15f) }
    val Browser = outline("Browser") { moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f); curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); close(); moveTo(15.5f, 8.5f); lineTo(13.5f, 13.5f); lineTo(8.5f, 15.5f); lineTo(10.5f, 10.5f); close() }
    val Share = outline("Share") { moveTo(7f, 9f); lineTo(4f, 9f); lineTo(4f, 22f); lineTo(20f, 22f); lineTo(20f, 9f); lineTo(17f, 9f); moveTo(12f, 15f); lineTo(12f, 2f); moveTo(8f, 6f); lineTo(12f, 2f); lineTo(16f, 6f) }
}
