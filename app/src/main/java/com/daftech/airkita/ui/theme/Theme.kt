package com.daftech.airkita.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AirKitaColors = lightColorScheme(
    primary = AirBlue,
    onPrimary = CardBg,
    primaryContainer = CyanSoft,
    onPrimaryContainer = Navy,
    secondary = Cyan,
    tertiary = Teal,
    background = AppBg,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = CyanSoft,
    error = Red
)

@Composable
fun AirKitaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirKitaColors,
        typography = AirKitaTypography,
        content = content
    )
}
