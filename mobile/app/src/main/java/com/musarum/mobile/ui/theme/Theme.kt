package com.musarum.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = GoldAmber,
    onPrimary = GoldAmberOn,
    primaryContainer = GoldAmberBg,
    onPrimaryContainer = GoldAmberOnBg,
    secondary = GoldAmberDim,
    onSecondary = GoldAmberOn,
    tertiary = RoseAccent,
    onTertiary = RoseOn,
    background = LightBg,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightContainer,
    onSurfaceVariant = LightOnVariant,
    surfaceContainer = LightContainer,
    outline = LightOutline,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = GoldAmber,
    onPrimary = GoldAmberOn,
    primaryContainer = GoldAmberBg,
    onPrimaryContainer = GoldAmberOnBg,
    secondary = GoldAmberDim,
    onSecondary = GoldAmberOn,
    tertiary = RoseAccent,
    onTertiary = RoseOn,
    background = Ebony,
    onBackground = WarmWhite,
    surface = EbonyRaised,
    onSurface = WarmWhite,
    surfaceVariant = EbonyContainer,
    onSurfaceVariant = WarmGrey,
    surfaceContainer = EbonyContainer,
    surfaceContainerHigh = EbonyHigh,
    outline = EbonyOutline,
    error = DarkErrorRed
)

@Composable
fun MusarumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MusarumTypography,
        content = content
    )
}
