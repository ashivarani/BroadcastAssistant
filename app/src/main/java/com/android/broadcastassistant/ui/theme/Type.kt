package com.android.broadcastassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Base custom typography (sizes + weights only, no colors hardcoded)
private val BaseTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

/**
 * Returns Typography that adapts to current MaterialTheme colors
 * so text colors auto-adjust in light/dark/dynamic themes.
 */
@Composable
fun appTypography(): Typography {
    val colors = MaterialTheme.colorScheme
    return BaseTypography.copy(
        displayLarge = BaseTypography.displayLarge.copy(color = colors.onBackground),
        headlineMedium = BaseTypography.headlineMedium.copy(color = colors.onBackground),
        bodyLarge = BaseTypography.bodyLarge.copy(color = colors.onSurface),
        labelLarge = BaseTypography.labelLarge.copy(color = colors.onPrimary)
    )
}
