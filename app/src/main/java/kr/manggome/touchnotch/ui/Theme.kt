package kr.manggome.touchnotch.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Accent = Color(0xFF0B84D6)
private val AccentDark = Color(0xFF7DD3FC)

private val LightScheme = lightColorScheme(
    primary = Accent,
    secondary = Color(0xFF3F6070),
    tertiary = Color(0xFFB4690E),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6ECF1),
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    secondary = Color(0xFFA8C7D8),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0F1418),
    surface = Color(0xFF161C21),
    surfaceVariant = Color(0xFF232B32),
)

@Composable
fun TouchNotchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
