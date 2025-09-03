package com.android.broadcastassistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Dark theme color scheme
private val DarkColorScheme = darkColorScheme(
    primary = RoyalBlue,           // Primary color for buttons, highlights
    onPrimary = ButtonTextWhite,   // Text color on primary elements
    background = Color(0xFF121212), // Dark background
    surface = Color(0xFF1E1E1E),   // Surfaces like cards, dialogs
    onBackground = Color.White,    // Text on background
    onSurface = Color.White        // Text on surfaces
)

// Light theme color scheme
private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,           // Primary color for buttons, highlights
    onPrimary = ButtonTextWhite,   // Text color on primary elements
    background = SkyBlue,          // Light background
    surface = Color.White,         // Surfaces like cards, dialogs
    onBackground = AppTextPrimary, // Text on background
    onSurface = AppTextPrimary     // Text on surfaces
)

/**
 * BroadcastAssistantTheme applies colors and typography to the app.
 *
 * @param darkTheme Use dark theme if true, defaults to system setting.
 * @param dynamicColor Use dynamic colors on Android 12+ if true.
 * @param content Composable content to apply the theme to.
 */
@Composable
fun BroadcastAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Use dynamic colors on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
