package com.example.philab.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.philab.R

/**
 * Familia tipográfica principal de la aplicación basada en la fuente Poppins.
 *
 * Incluye: Light, Regular, SemiBold y Bold
 */
val Poppins = FontFamily(
    Font(R.font.poppins_light,    FontWeight.Light),
    Font(R.font.poppins_regular,  FontWeight.Normal),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold,     FontWeight.Bold)
)

/**
 * Configuración global de tipografías de la aplicación.
 *
 * Define los estilos de texto utilizados en Material3, incluyendo títulos,
 * encabezados, cuerpo y etiquetas, todos basados en la fuente Poppins.
 */
val AppTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold,     fontSize = 57.sp),
    displayMedium  = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold,     fontSize = 45.sp),
    displaySmall   = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold,     fontSize = 36.sp),
    headlineLarge  = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall  = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge     = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium    = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal,   fontSize = 16.sp),
    titleSmall     = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal,   fontSize = 16.sp),
    bodyMedium     = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal,   fontSize = 14.sp),
    bodySmall      = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Light,    fontSize = 12.sp),
    labelLarge     = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium    = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal,   fontSize = 12.sp),
    labelSmall     = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Light,    fontSize = 11.sp),
)