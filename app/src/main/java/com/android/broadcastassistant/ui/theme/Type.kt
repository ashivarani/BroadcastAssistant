package com.android.broadcastassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Custom Material 3 typography styles
val Typography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp, // Large text for prominent headings
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, // Medium headings
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp, // Default body text
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp, // Labels, buttons, or small text
    )
)

/*
Optional: default Typography template for reference
val DefaultTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    // Additional styles can be overridden as needed:
    // titleLarge, labelSmall, etc.
)
*/
