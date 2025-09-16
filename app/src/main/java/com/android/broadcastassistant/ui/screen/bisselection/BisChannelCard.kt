package com.android.broadcastassistant.ui.screen.bisselection

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.ui.theme.ButtonTextWhite

/**
 * Card for displaying a single BIS channel.
 *
 * Highlights selection and active switching state.
 * - Background color animates based on selection / switching.
 * - Pulsing border for active switching BIS.
 * - Text color adapts automatically to background luminance.
 *
 * @param bis BIS channel data
 * @param isSelected True if currently selected
 * @param switchingActive True if switching animation is active
 * @param onClick Callback when card is clicked
 */
@Composable
fun BisChannelCard(
    bis: BisChannel,
    isSelected: Boolean,
    switchingActive: Boolean,
    onClick: () -> Unit
) {
    // Animate background color for selection/switching
    val cardColor by animateColorAsState(
        targetValue = when {
            switchingActive && isSelected -> Color(0xFFFFEB3B) // bright yellow
            isSelected -> Color(0xFF4CAF50)                     // green
            else -> Color(0xFF1A73E8)                           // blue
        },
        label = "bisCardColorAnim"
    )

    // Pulsing border animation for switching BIS
    val borderColor by rememberInfiniteTransition().animateColor(
        initialValue = Color.Transparent,
        targetValue = if (switchingActive && isSelected) Color.Red else Color.Transparent,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Determine text color based on background luminance
    val textColor = if (cardColor.luminance() > 0.5f) Color.Black else ButtonTextWhite

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(width = 2.dp, color = borderColor, shape = CardDefaults.shape) // pulsing border
            .clickable(enabled = !switchingActive, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = bis.language, fontSize = 16.sp, color = textColor)
            bis.audioRole?.let { role ->
                Text(text = "Role: $role", fontSize = 12.sp, color = textColor.copy(alpha = 0.8f))
            }
            bis.streamConfig?.let { config ->
                Text(text = "Config: $config", fontSize = 12.sp, color = textColor.copy(alpha = 0.8f))
            }
            Text(text = "BIS Index: ${bis.index}", fontSize = 12.sp, color = textColor)
        }
    }
}
