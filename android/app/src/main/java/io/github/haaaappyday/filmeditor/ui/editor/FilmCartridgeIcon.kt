package io.github.haaaappyday.filmeditor.ui.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag

/** Original, decorative cartridge illustrations; the selector supplies accessible stock names. */
@Composable
internal fun FilmCartridgeIcon(id: String, modifier: Modifier = Modifier) {
    val stock = when (id) {
        "kodak_portra_400" -> Cartridge("PORTRA", "400", Color(0xFFD7C39D), Color(0xFF7D3844), Color(0xFF373129))
        "kodak_gold_200" -> Cartridge("GOLD", "200", Color(0xFFF0C335), Color(0xFFAD302A), Color(0xFF382E1E))
        "kodak_ektar_100" -> Cartridge("EKTAR", "100", Color(0xFFAE343E), Color(0xFFE0BC65), Color(0xFFFFF8EB))
        else -> Cartridge("FILM", "", Color(0xFFCBCDC8), Color(0xFF68756C), Color(0xFF30342F))
    }
    val text = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    } }
    Canvas(modifier.testTag("stock-icon-$id")) {
        withTransform({ scale(size.width / 100f, size.height / 72f, Offset.Zero) }) {
            val shell = Color(0xFF292D2A)
            val leader = Color(0xFF72513B)
            drawPath(Path().apply {
                moveTo(61f, 22f); lineTo(83f, 22f)
                cubicTo(91f, 22f, 92f, 30f, 88f, 34f)
                lineTo(82f, 40f); lineTo(82f, 57f); lineTo(61f, 57f); close()
            }, leader)
            for (x in listOf(66f, 73f, 80f)) {
                drawRoundRect(Color(0xFFE7DFD0), Offset(x, 25f), Size(4f, 3f), CornerRadius(1f))
                if (x < 80f) drawRoundRect(Color(0xFFE7DFD0), Offset(x, 51f), Size(4f, 3f), CornerRadius(1f))
            }
            drawRoundRect(shell, Offset(37f, 2f), Size(15f, 10f), CornerRadius(3f))
            drawRoundRect(shell, Offset(23f, 10f), Size(44f, 57f), CornerRadius(8f))
            drawRect(stock.paper, Offset(26f, 18f), Size(38f, 40f))
            drawRect(stock.band, Offset(26f, 18f), Size(38f, 7f))
            drawRect(Color.White.copy(alpha = .15f), Offset(27f, 26f), Size(2f, 31f))
            drawRoundRect(Color(0xFF424943), Offset(24f, 9f), Size(42f, 8f), CornerRadius(4f))
            drawRoundRect(Color(0xFF424943), Offset(24f, 59f), Size(42f, 8f), CornerRadius(4f))
            text.color = stock.ink.toArgb()
            text.textSize = 7f
            drawContext.canvas.nativeCanvas.drawText(stock.name, 45f, 36f, text)
            text.textSize = 13f
            drawContext.canvas.nativeCanvas.drawText(stock.iso, 45f, 51f, text)
        }
    }
}

private data class Cartridge(val name: String, val iso: String, val paper: Color, val band: Color, val ink: Color)
