package com.android.broadcastassistant.ui.screen.bisselection

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.ui.theme.ButtonTextWhite

/**
 * Card UI for displaying a single BIS channel.
 *
 * Highlights selection and switching states with color animations.
 *
 * @param bis The BIS channel data
 * @param isSelected Whether this BIS is currently selected
 * @param switchingActive Whether a BIS switch animation is active (disables clicks)
 * @param onClick Callback invoked when the card is clicked
 */
@Composable
fun BisChannelCard(
    bis: BisChannel,
    isSelected: Boolean,
    switchingActive: Boolean,
    onClick: () -> Unit
) {
    // Animate background color based on selection and switching state
    val cardColor by animateColorAsState(
        targetValue = when {
            switchingActive && isSelected -> Color.Yellow   // Actively switching
            isSelected -> Color(0xFF4CAF50)                 // Selected BIS
            else -> Color(0xFF1A73E8)                       // Default color
        },
        label = "bisCardColorAnim"
    )

    // Card container for BIS details
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(enabled = !switchingActive, onClick = onClick), // Disable click during switching
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Language of the BIS channel
            Text(
                text = bis.language,
                fontSize = 16.sp,
                color = ButtonTextWhite
            )

            // Optional audio role
            bis.audioRole?.let { role ->
                Text(
                    text = "Role: $role",
                    fontSize = 12.sp,
                    color = ButtonTextWhite.copy(alpha = 0.8f)
                )
            }

            // Optional stream configuration
            bis.streamConfig?.let { config ->
                Text(
                    text = "Config: $config",
                    fontSize = 12.sp,
                    color = ButtonTextWhite.copy(alpha = 0.8f)
                )
            }

            // BIS index
            Text(
                text = "BIS Index: ${bis.index}",
                fontSize = 12.sp,
                color = ButtonTextWhite
            )
        }
    }
}
