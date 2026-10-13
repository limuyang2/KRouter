package io.github.limuyang2.router.demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF102F60),
    secondary = Color(0xFFB9C7DD),
    background = Color(0xFF10141C),
    surface = Color(0xFF1B2230),
    onSurface = Color(0xFFE3E8F2),
    onSurfaceVariant = Color(0xFFBAC4D6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color.White,
    secondary = Color(0xFF52647E),
    background = Color(0xFFF2F5FA),
    surface = Color.White,
    onSurface = Color(0xFF1B273B),
    onSurfaceVariant = Color(0xFF52647E),
)

@Composable
fun LibraryrouterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
