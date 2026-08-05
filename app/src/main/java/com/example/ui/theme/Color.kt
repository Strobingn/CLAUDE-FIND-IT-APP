package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Greyscale only — no hue in primary, secondary, or surface tokens.

val Grey900 = Color(0xFF212121)
val Grey800 = Color(0xFF424242)
val Grey700 = Color(0xFF616161)
val Grey600 = Color(0xFF757575)
val Grey500 = Color(0xFF9E9E9E)
val Grey400 = Color(0xFFBDBDBD)
val Grey300 = Color(0xFFE0E0E0)
val Grey200 = Color(0xFFEEEEEE)
val Grey100 = Color(0xFFF5F5F5)
val Grey50 = Color(0xFFFAFAFA)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)

val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE0E0E0)

// Subtle warm-neutral accent kept as grey (no gold/amber hue)
val GoldAmber = Color(0xFFBDBDBD)

val PrimaryLight = Grey700
val PrimaryContainerLight = Grey300
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryContainerLight = Grey900

val PrimaryDark = Grey400
val PrimaryContainerDark = Grey800
val OnPrimaryDark = Color(0xFF000000)
val OnPrimaryContainerDark = Color(0xFFFFFFFF)

// Functional status only (not brand chrome)
val Success = Color(0xFF9E9E9E)
val Warning = Color(0xFFBDBDBD)
val Error = Color(0xFFCF6679)
