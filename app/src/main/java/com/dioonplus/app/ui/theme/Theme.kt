package com.dioonplus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DioonColorScheme = lightColorScheme(
    primary = DioonBlue,
    onPrimary = CardSurface,
    primaryContainer = DioonBlueSoft,
    onPrimaryContainer = DioonBlueDark,
    secondary = SuccessGreen,
    onSecondary = CardSurface,
    error = DebtRed,
    onError = CardSurface,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor,
)

private val DioonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DioonPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DioonColorScheme,
        typography = DioonTypography,
        shapes = DioonShapes,
        content = content,
    )
}
