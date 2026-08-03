package com.example.toctoe.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BearColorScheme = lightColorScheme(
    primary = BearPrimary,
    onPrimary = BearCard,
    primaryContainer = BearCard,
    onPrimaryContainer = BearText,
    secondary = BearPrimaryDark,
    onSecondary = BearCard,
    background = BearBackground,
    onBackground = BearText,
    surface = BearCard,
    onSurface = BearText,
    surfaceVariant = BearBackground,
    onSurfaceVariant = BearHint,
    outline = BearHint,
    error = BearBrown,
    onError = BearCard
)

private val BearShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun BearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BearColorScheme,
        typography = Typography,
        shapes = BearShapes,
        content = content
    )
}
