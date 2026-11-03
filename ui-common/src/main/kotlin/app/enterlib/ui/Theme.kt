package app.enterlib.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF2EC4B6), onPrimary = Color.Black, secondary = Color(0xFFFFB703),
    background = Color(0xFF0B0F14), surface = Color(0xFF141A22), surfaceVariant = Color(0xFF1E2630),
    onBackground = Color(0xFFE8EEF4), onSurface = Color(0xFFE8EEF4), onSurfaceVariant = Color(0xFFA9B4C0),
)
private val Light = lightColorScheme(
    primary = Color(0xFF0B7F75), onPrimary = Color.White, secondary = Color(0xFFB36B00),
    background = Color(0xFFF6F8FA), surface = Color.White, surfaceVariant = Color(0xFFE3E8EE),
)

@Composable
fun EnterLibTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
