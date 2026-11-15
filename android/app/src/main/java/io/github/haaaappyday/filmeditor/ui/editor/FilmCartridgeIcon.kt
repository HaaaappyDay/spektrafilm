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
    val stock = cartridgeArt(id)
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

internal data class Cartridge(val name: String, val iso: String, val paper: Color, val band: Color, val ink: Color)

internal fun cartridgeArt(id: String): Cartridge = CARTRIDGES[id]
    ?: Cartridge("FILM", "", Color(0xFFCBCDC8), Color(0xFF68756C), Color(0xFF30342F))

private val CARTRIDGES = mapOf(
    "kodak_portra_400" to Cartridge("PORTRA", "400", Color(0xFFD7C39D), Color(0xFF7D3844), Color(0xFF373129)),
    "kodak_gold_200" to Cartridge("GOLD", "200", Color(0xFFF0C335), Color(0xFFAD302A), Color(0xFF382E1E)),
    "kodak_ektar_100" to Cartridge("EKTAR", "100", Color(0xFFAE343E), Color(0xFFE0BC65), Color(0xFFFFF8EB)),
    "kodak_portra_160" to Cartridge("PORTRA", "160", Color(0xFFE4D2B0), Color(0xFF8C4A3A), Color(0xFF373129)),
    "kodak_portra_800" to Cartridge("PORTRA", "800", Color(0xFFC4A882), Color(0xFF6E3140), Color(0xFF373129)),
    "kodak_portra_800_push1" to Cartridge("800", "+1", Color(0xFFB89468), Color(0xFF5C2838), Color(0xFFFFF4E4)),
    "kodak_portra_800_push2" to Cartridge("800", "+2", Color(0xFF9A7048), Color(0xFF4A2030), Color(0xFFFFF4E4)),
    "kodak_ultramax_400" to Cartridge("ULTRA", "400", Color(0xFFF08A2A), Color(0xFF1F4E9A), Color(0xFF2A2118)),
    "kodak_vision3_50d" to Cartridge("50D", "", Color(0xFFD6E4F0), Color(0xFF1E4D8C), Color(0xFF1A2430)),
    "kodak_vision3_250d" to Cartridge("250D", "", Color(0xFFB7D0EA), Color(0xFF163E78), Color(0xFF1A2430)),
    "kodak_verita_200d" to Cartridge("VERITA", "200", Color(0xFFE7D7C2), Color(0xFF2F5E4A), Color(0xFF241C16)),
    "kodak_vision3_200t" to Cartridge("200T", "", Color(0xFFF0D7A4), Color(0xFF8A3B12), Color(0xFF2A1C12)),
    "kodak_vision3_500t" to Cartridge("500T", "", Color(0xFFE2B56A), Color(0xFF6A2C10), Color(0xFFFFF6E6)),
    "fujifilm_pro_400h" to Cartridge("PRO", "400H", Color(0xFFD7E2C8), Color(0xFF1F6B45), Color(0xFF1C241C)),
    "fujifilm_c200" to Cartridge("C200", "", Color(0xFFE7F0D8), Color(0xFF3E8F4A), Color(0xFF1C241C)),
    "fujifilm_xtra_400" to Cartridge("X-TRA", "400", Color(0xFFB7D45A), Color(0xFF207A38), Color(0xFF172016)),
    "kodak_ektachrome_100" to Cartridge("EKTA", "100", Color(0xFFE8EEF2), Color(0xFF3A6EA5), Color(0xFF1C2430)),
    "kodak_kodachrome_64" to Cartridge("K64", "", Color(0xFFF4E27A), Color(0xFFC4372C), Color(0xFF2A1A14)),
    "fujifilm_velvia_100" to Cartridge("VELVIA", "100", Color(0xFF2E8B57), Color(0xFFE23B2F), Color(0xFFFFF8EF)),
    "fujifilm_provia_100f" to Cartridge("PROVIA", "100", Color(0xFFD5DDE3), Color(0xFF5C6B73), Color(0xFF1E2428)),
)
