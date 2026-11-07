package nl.contrastlezer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ContrastlezerColors = darkColorScheme(
    primary = Color(0xFFFFD500),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF3A3100),
    onPrimaryContainer = Color(0xFFFFE45C),
    secondary = Color(0xFF9BE7FF),
    onSecondary = Color(0xFF001F27),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF000000),
)

private val ContrastlezerTypography = Typography(
    bodyLarge = TextStyle(
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun ContrastlezerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ContrastlezerColors,
        typography = ContrastlezerTypography,
        content = content,
    )
}
