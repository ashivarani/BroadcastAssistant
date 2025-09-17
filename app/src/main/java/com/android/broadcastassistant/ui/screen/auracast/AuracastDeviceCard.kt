package com.android.broadcastassistant.ui.screen.auracast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Displays a card representing an Auracast device (broadcaster or receiver).
 *
 * Features:
 * - Shows device name, address, and RSSI.
 * - Shows broadcast ID if the device is a broadcaster.
 * - Shows selected BIS indexes if available.
 * - Highlights the card if [isSelected] is true.
 * - Differentiates broadcasters (blue) and receivers (green).
 *
 * @param device The [AuracastDevice] to display.
 * @param isSelected Whether this device is currently selected (affects card color).
 * @param onClick Callback invoked when the card is clicked.
 */
@Composable
fun AuracastDeviceCard(
    device: AuracastDevice,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    // Determine card background color
    val containerColor = when {
        isSelected -> Color(0xFF1976D2)                 // Selected
        device.broadcastId == null -> Color(0xFF43A047) // Receiver
        else -> Color(0xFF1A73E8)                       // Broadcaster
    }

    // Disable click for receivers
    val clickableModifier = if (device.broadcastId != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(clickableModifier),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Name and address
            Text(
                text = "${device.name} (${device.address})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )

            // RSSI (show unknown if 0 or not fetched)
            Text(
                text = "RSSI: ${if (device.rssi != 0) "${device.rssi} dBm" else "unknown"}",
                fontSize = 14.sp,
                color = Color.White
            )

            // Broadcast ID
            device.broadcastId?.let { broadcastId ->
                Text(
                    text = "Broadcast ID: $broadcastId",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            // Label for receivers
            if (device.broadcastId == null) {
                Text(
                    text = "Receiver",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Selected BIS overlay
            if (device.selectedBisIndexes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(Color(0x33000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Selected BIS: ", color = Color.Yellow, fontSize = 12.sp)
                    device.selectedBisIndexes.forEach { index ->
                        Text("$index ", color = Color.Green, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
