package io.github.haaaappyday.filmeditor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B5337),
    background = Color(0xFFFAF7F1),
    surface = Color(0xFFFAF7F1),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFDBC3A3),
    background = Color(0xFF191816),
    surface = Color(0xFF191816),
)

@Composable
fun FilmEditorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content)
}
