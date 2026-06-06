package com.example.ui.theme

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

private val WarmBookstoreColorScheme = lightColorScheme(
    primary = BookPrimary,
    secondary = BookSecondary,
    tertiary = BookTertiary,
    background = BookBackground,
    surface = BookSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2E2620),
    onSurface = Color(0xFF2E2620)
)

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    secondary = MidnightSecondary,
    tertiary = MidnightTertiary,
    background = MidnightBackground,
    surface = MidnightSurface,
    onPrimary = Color(0xFF07110B),
    onSecondary = Color.White,
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4)
)

private val SwissColorScheme = lightColorScheme(
    primary = SwissPrimary,
    secondary = SwissSecondary,
    tertiary = SwissTertiary,
    background = SwissBackground,
    surface = SwissSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111)
)

private val PastelColorScheme = lightColorScheme(
    primary = PastelPrimary,
    secondary = PastelSecondary,
    tertiary = PastelTertiary,
    background = PastelBackground,
    surface = PastelSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF4A148C),
    onSurface = Color(0xFF4A148C)
)

private val ClassicColorScheme = darkColorScheme(
    primary = ClassicPrimary,
    secondary = ClassicSecondary,
    tertiary = ClassicTertiary,
    background = ClassicBackground,
    surface = ClassicSurface,
    onPrimary = Color(0xFF08190B),
    onSecondary = Color.White,
    onBackground = Color(0xFFF5E6C4),
    onSurface = Color(0xFFF5E6C4)
)

@Composable
fun MyApplicationTheme(
    themeId: Int = 1,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeId) {
        1 -> WarmBookstoreColorScheme
        2 -> MidnightColorScheme
        3 -> SwissColorScheme
        4 -> PastelColorScheme
        5 -> ClassicColorScheme
        else -> WarmBookstoreColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
