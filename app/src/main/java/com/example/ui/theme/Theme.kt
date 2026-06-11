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
    primaryContainer = Color(0xFFE2ECE7),
    onPrimaryContainer = BookPrimary,
    secondaryContainer = Color(0xFFF3EAE5),
    onSecondaryContainer = Color(0xFF5D4037),
    tertiaryContainer = Color(0xFFF7F0EE),
    onTertiaryContainer = Color(0xFF4E342E),
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
    primaryContainer = Color(0xFF1E2E4B),
    onPrimaryContainer = MidnightPrimary,
    secondaryContainer = Color(0xFF23314F),
    onSecondaryContainer = MidnightSecondary,
    tertiaryContainer = Color(0xFF383A20),
    onTertiaryContainer = MidnightTertiary,
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
    primaryContainer = Color(0xFFEEEEEE),
    onPrimaryContainer = SwissPrimary,
    secondaryContainer = Color(0xFFFFEBEE),
    onSecondaryContainer = SwissSecondary,
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = SwissTertiary,
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
    primaryContainer = Color(0xFFF0D5FF),
    onPrimaryContainer = PastelPrimary,
    secondaryContainer = Color(0xFFF7E2F7),
    onSecondaryContainer = Color(0xFF9C27B0),
    tertiaryContainer = Color(0xFFE0F7FA),
    onTertiaryContainer = PastelTertiary,
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
    primaryContainer = Color(0xFF332D1E),
    onPrimaryContainer = ClassicPrimary,
    secondaryContainer = Color(0xFF2E2718),
    onSecondaryContainer = ClassicSecondary,
    tertiaryContainer = Color(0xFF2B220F),
    onTertiaryContainer = ClassicTertiary,
    outline = ClassicSecondary.copy(alpha = 0.5f),
    outlineVariant = ClassicSecondary.copy(alpha = 0.25f)
)

private val PinkColorScheme = lightColorScheme(
    primary = PinkPrimary,
    secondary = PinkSecondary,
    tertiary = PinkTertiary,
    background = PinkBackground,
    surface = PinkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF880E4F),
    onSurface = Color(0xFF880E4F),
    primaryContainer = Color(0xFFFCD3E1),
    onPrimaryContainer = PinkPrimary,
    secondaryContainer = Color(0xFFFFF0F5),
    onSecondaryContainer = PinkSecondary,
    tertiaryContainer = Color(0xFFFFE4E1),
    onTertiaryContainer = PinkTertiary,
    outline = PinkSecondary.copy(alpha = 0.5f),
    outlineVariant = PinkSecondary.copy(alpha = 0.25f)
)

private val GreyColorScheme = lightColorScheme(
    primary = GreyPrimary,
    secondary = GreySecondary,
    tertiary = GreyTertiary,
    background = GreyBackground,
    surface = GreySurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF263238),
    onSurface = Color(0xFF263238),
    primaryContainer = Color(0xFFDCE4E7),
    onPrimaryContainer = GreyPrimary,
    secondaryContainer = Color(0xFFECEFF1),
    onSecondaryContainer = GreySecondary,
    tertiaryContainer = Color(0xFFD3DBDE),
    onTertiaryContainer = GreyTertiary,
    outline = GreySecondary.copy(alpha = 0.5f),
    outlineVariant = GreySecondary.copy(alpha = 0.25f)
)

private val ForestColorScheme = lightColorScheme(
    primary = ForestPrimary,
    secondary = ForestSecondary,
    tertiary = ForestTertiary,
    background = ForestBackground,
    surface = ForestSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1B5E20),
    onSurface = Color(0xFF1B5E20),
    primaryContainer = Color(0xFFD0EDD3),
    onPrimaryContainer = ForestPrimary,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = ForestSecondary,
    tertiaryContainer = Color(0xFFC8E6C9),
    onTertiaryContainer = ForestTertiary,
    outline = ForestSecondary.copy(alpha = 0.5f),
    outlineVariant = ForestSecondary.copy(alpha = 0.25f)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    primaryContainer = Color(0xFF1B382B),
    onPrimaryContainer = DarkPrimary,
    secondaryContainer = Color(0xFF142C21),
    onSecondaryContainer = DarkSecondary,
    tertiaryContainer = Color(0xFF0C2419),
    onTertiaryContainer = DarkTertiary,
    outline = DarkSecondary.copy(alpha = 0.5f),
    outlineVariant = DarkSecondary.copy(alpha = 0.25f)
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
        6 -> PinkColorScheme
        7 -> GreyColorScheme
        8 -> ForestColorScheme
        9 -> DarkColorScheme
        else -> WarmBookstoreColorScheme
    }


    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
