package io.github.haaaappyday.filmeditor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark camera body. The photo is the brightest surface, so the chrome does not follow a light theme. */
private val CameraColors = darkColorScheme(
    primary = Color(0xFFC6A36A), onPrimary = Color(0xFF1A140C),
    background = Color(0xFF12110E), surface = Color(0xFF1C1914),
    onSurface = Color(0xFFF3EBDD), onBackground = Color(0xFFF3EBDD),
    onSurfaceVariant = Color(0xFFD4C6B0), outlineVariant = Color(0xFF3A342C),
    surfaceContainerLow = Color(0xFF241F1A), surfaceContainerHighest = Color(0xFF3A342C),
    secondaryContainer = Color(0xFF3A3124), onSecondaryContainer = Color(0xFFF3EBDD),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)
private val EditorTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FilmEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CameraColors, typography = EditorTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content)
}
