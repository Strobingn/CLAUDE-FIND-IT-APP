package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Field-app earth palette: soil, moss, amber accent (not grey-only chrome).

// Accent — warm amber for key actions (export, confirm, open)
val FieldAmber = Color(0xFFE0A84A)
val FieldAmberDim = Color(0xFF8B6914)

// Soil / bark neutrals
val Soil900 = Color(0xFF12100E)
val Soil800 = Color(0xFF1C1916)
val Soil700 = Color(0xFF2A2520)
val Soil600 = Color(0xFF3D352E)
val Soil400 = Color(0xFF9A8F82)
val Soil200 = Color(0xFFD9D0C4)
val Soil100 = Color(0xFFF0EBE3)
val Soil50 = Color(0xFFFAF7F2)

// Moss / canopy greens
val Moss = Color(0xFF6B8F71)
val MossDeep = Color(0xFF3D5C45)
val MossMuted = Color(0xFF8FA896)

// Status
val Success = Color(0xFF5C9E6B)
val Warning = Color(0xFFE0A84A)
val Error = Color(0xFFCF6679)

// Legacy aliases (avoid widespread renames elsewhere)
val GoldAmber = FieldAmber
val Grey900 = Soil900
val Grey800 = Soil700
val Grey700 = Soil600
val Grey600 = Soil400
val Grey500 = Soil400
val Grey400 = Soil200
val Grey300 = Soil200
val Grey200 = Soil100
val Grey100 = Soil50
val Grey50 = Soil50
val DarkBackground = Soil900
val DarkSurface = Soil800
val DarkSurfaceVariant = Soil700
val LightBackground = Soil50
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Soil100
val PrimaryLight = MossDeep
val PrimaryContainerLight = Color(0xFFD4E5D6)
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryContainerLight = Soil900
val PrimaryDark = FieldAmber
val PrimaryContainerDark = Soil600
val OnPrimaryDark = Soil900
val OnPrimaryContainerDark = Soil100
