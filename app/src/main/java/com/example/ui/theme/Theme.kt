package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmBookstoreColorScheme = lightColorScheme(
    primary = BookPrimary,
    secondary = BookSecondary,
    tertiary = BookTertiary,
    background = BookBackground,
    surface = BookSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2E2620),
    onSurface = Color(0xFF2E2620),
    outline = BookSecondary.copy(alpha = 0.5f),
    outlineVariant = BookSecondary.copy(alpha = 0.25f)
)

private val MidnightColorScheme = darkColorScheme(
    primary = MidnightPrimary,
    secondary = MidnightSecondary,
    tertiary = MidnightTertiary,
    background = MidnightBackground,
    surface = MidnightSurface,
    onPrimary = Color(0xFF04101A),
    onSecondary = Color.White,
    onBackground = Color(0xFFECEFF4),
    onSurface = Color(0xFFECEFF4),
    outline = MidnightSecondary.copy(alpha = 0.5f),
    outlineVariant = MidnightSecondary.copy(alpha = 0.25f)
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
    onSurface = Color(0xFF111111),
    outline = SwissTertiary.copy(alpha = 0.5f),
    outlineVariant = SwissTertiary.copy(alpha = 0.25f)
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
    onSurface = Color(0xFF4A148C),
    outline = PastelSecondary.copy(alpha = 0.5f),
    outlineVariant = PastelSecondary.copy(alpha = 0.25f)
)

private val ClassicColorScheme = darkColorScheme(
    primary = ClassicPrimary,
    secondary = ClassicSecondary,
    tertiary = ClassicTertiary,
    background = ClassicBackground,
    surface = ClassicSurface,
    onPrimary = Color(0xFF1A1508),
    onSecondary = Color(0xFF1A1508),
    onBackground = Color(0xFFE5D5B3),
    onSurface = Color(0xFFE5D5B3),
    outline = ClassicSecondary.copy(alpha = 0.5f),
    outlineVariant = ClassicSecondary.copy(alpha = 0.25f)
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
