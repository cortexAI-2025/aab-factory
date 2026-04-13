package com.aabfactory.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

private val DarkColorScheme = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleDark,
    onPrimaryContainer = BrandPurpleLight,
    secondary = BrandTeal,
    onSecondary = Color.Black,
    secondaryContainer = BrandTealDark,
    onSecondaryContainer = BrandTeal,
    tertiary = ProBadgeGold,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = Error,
    onError = Color.White,
    inverseSurface = LightSurface,
    inverseOnSurface = TextOnLight,
    inversePrimary = BrandPurpleDark,
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = Color.White,
    primaryContainer = BrandPurpleLight,
    onPrimaryContainer = BrandPurpleDark,
    secondary = BrandTealDark,
    onSecondary = Color.White,
    secondaryContainer = BrandTeal,
    onSecondaryContainer = Color.Black,
    tertiary = ProBadgeGold,
    background = LightBackground,
    onBackground = TextOnLight,
    surface = LightSurface,
    onSurface = TextOnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = LightBorder,
    error = Error,
    onError = Color.White,
    inverseSurface = DarkSurface,
    inverseOnSurface = TextPrimary,
    inversePrimary = BrandPurpleLight,
)

/**
 * AAB Factory Material3 theme.
 * Defaults to dark mode — the product's design language is dark-first.
 */
@Composable
fun AABFactoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val systemUiController = rememberSystemUiController()
    SideEffect {
        systemUiController.setSystemBarsColor(
            color = if (darkTheme) DarkBackground else LightBackground,
            darkIcons = !darkTheme
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
