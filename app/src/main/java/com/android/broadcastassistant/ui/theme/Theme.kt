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
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.R

// Dark theme color scheme (values-night/colors.xml will override)
@Composable
private fun darkColorTheme() = darkColorScheme(
    primary = Color(ContextCompat.getColor(LocalContext.current, R.color.status_switching)),
    onPrimary = Color.White,
    background = Color(ContextCompat.getColor(LocalContext.current, R.color.background_primary)),
    surface = Color(ContextCompat.getColor(LocalContext.current, R.color.surface_primary)),
    onBackground = Color(ContextCompat.getColor(LocalContext.current, R.color.text_primary)),
    onSurface = Color(ContextCompat.getColor(LocalContext.current, R.color.text_secondary))
)

// Light theme color scheme (values/colors.xml)
@Composable
private fun lightColorTheme() = lightColorScheme(
    primary = Color(ContextCompat.getColor(LocalContext.current, R.color.status_switching)),
    onPrimary = Color.White,
    background = Color(ContextCompat.getColor(LocalContext.current, R.color.background_primary)),
    surface = Color(ContextCompat.getColor(LocalContext.current, R.color.surface_primary)),
    onBackground = Color(ContextCompat.getColor(LocalContext.current, R.color.text_primary)),
    onSurface = Color(ContextCompat.getColor(LocalContext.current, R.color.text_secondary))
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
    val context = LocalContext.current

    val colorScheme = when {
        // Use dynamic colors on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorTheme()
        else -> lightColorTheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(),
        content = content
    )
}
