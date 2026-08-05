package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FieldAmber,
    onPrimary = Soil900,
    primaryContainer = Soil600,
    onPrimaryContainer = Soil100,
    secondary = MossMuted,
    onSecondary = Soil900,
    secondaryContainer = MossDeep,
    onSecondaryContainer = Soil100,
    tertiary = Moss,
    onTertiary = Soil900,
    tertiaryContainer = Soil700,
    onTertiaryContainer = Soil100,
    background = Soil900,
    onBackground = Soil100,
    surface = Soil800,
    onSurface = Soil100,
    surfaceVariant = Soil700,
    onSurfaceVariant = Soil400,
    surfaceContainer = Soil700,
    surfaceContainerHigh = Soil600,
    surfaceContainerHighest = Color(0xFF453C34),
    surfaceContainerLow = Soil800,
    surfaceContainerLowest = Soil900,
    outline = Soil600,
    outlineVariant = Soil700,
    scrim = Color.Black.copy(alpha = 0.55f),
    error = Error,
    onError = Soil900,
    inverseOnSurface = Soil900,
    inversePrimary = FieldAmberDim,
)

private val LightColorScheme = lightColorScheme(
    primary = MossDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4E5D6),
    onPrimaryContainer = Soil900,
    secondary = FieldAmberDim,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E2BC),
    onSecondaryContainer = Soil900,
    tertiary = Moss,
    onTertiary = Color.White,
    background = Soil50,
    onBackground = Soil900,
    surface = Color.White,
    onSurface = Soil900,
    surfaceVariant = Soil100,
    onSurfaceVariant = Soil600,
    surfaceContainer = Soil100,
    surfaceContainerHigh = Soil200,
    outline = Soil400,
    outlineVariant = Soil200,
    scrim = Color.Black.copy(alpha = 0.35f),
    error = Error,
    onError = Color.White,
    inverseOnSurface = Soil50,
    inversePrimary = FieldAmber,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
