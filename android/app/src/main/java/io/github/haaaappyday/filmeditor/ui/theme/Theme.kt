package io.github.haaaappyday.filmeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF304D40), onPrimary = Color.White,
    background = Color(0xFFF0F1EF), surface = Color(0xFFFCFDFB),
    onSurface = Color(0xFF202521), onBackground = Color(0xFF202521),
    onSurfaceVariant = Color(0xFF626A64), outlineVariant = Color(0xFFDDE1DC),
    surfaceContainerLow = Color(0xFFF0F2EF), surfaceContainerHighest = Color(0xFFDFE5DE),
    secondaryContainer = Color(0xFFE5ECE5), onSecondaryContainer = Color(0xFF293D30),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1CBB8), onPrimary = Color(0xFF193322),
    background = Color(0xFF111411), surface = Color(0xFF1B1F1B),
    onSurface = Color(0xFFE1E5DF), onBackground = Color(0xFFE1E5DF),
    onSurfaceVariant = Color(0xFFADB5AC), outlineVariant = Color(0xFF343C34),
    surfaceContainerLow = Color(0xFF252B25), surfaceContainerHighest = Color(0xFF3B443C),
    secondaryContainer = Color(0xFF2C3B2F), onSecondaryContainer = Color(0xFFD8E8D9),
)
private val EditorTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun FilmEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = EditorTypography,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp)),
        content = content)
}
