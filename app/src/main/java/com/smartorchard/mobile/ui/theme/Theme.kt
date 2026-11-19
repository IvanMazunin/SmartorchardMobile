package com.smartorchard.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val OrchardGreen = Color(0xFF2D573E)
val OrchardBackground = Color(0xFFF5F6F1)
val OrchardInk = Color(0xFF22372A)
val OrchardMuted = Color(0xFF738074)
val OrchardLine = Color(0xFFE1E6DA)
val GoodBackground = Color(0xFFE7EFDF)
val WarningBackground = Color(0xFFFBF0D9)
val DeadBackground = Color(0xFFF7E8E4)
val WaterBlue = Color(0xFF287895)
val ZoneColors = listOf(Color(0xFFDCE9DC), Color(0xFFE8E4F1), Color(0xFFE9EACB))

private val OrchardColorScheme = lightColorScheme(
    primary = OrchardGreen,
    onPrimary = Color.White,
    primaryContainer = GoodBackground,
    onPrimaryContainer = OrchardGreen,
    background = OrchardBackground,
    onBackground = OrchardInk,
    surface = Color.White,
    onSurface = OrchardInk,
    surfaceVariant = Color(0xFFEDF1E8),
    onSurfaceVariant = OrchardMuted,
    outline = OrchardLine,
    error = Color(0xFFB05349)
)

private val OrchardTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontSize = 10.sp
    )
)

@Composable
fun SmartOrchardTheme(content: @Composable () -> Unit) {
    // The approved prototype uses a fixed light palette instead of system dynamic colors.
    MaterialTheme(
        colorScheme = OrchardColorScheme,
        typography = OrchardTypography,
        content = content
    )
}
