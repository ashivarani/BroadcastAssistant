package com.android.broadcastassistant.ui.screen.auracast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Displays a card representing an Auracast broadcaster device.
 *
 * Shows device name, address, RSSI, optional broadcast ID, and any selected BIS indexes.
 * Highlights the card if [isSelected] is true.
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
    // Card container with padding, click behavior, and color based on selection
    Card(
        modifier = Modifier
            .fillMaxWidth() // Make card stretch full width
            .padding(vertical = 8.dp) // Vertical spacing between cards
            .clickable { onClick() }, // Handle click events
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1976D2) else Color(0xFF1A73E8) // Highlight selected card
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp) // Card shadow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Display device name and address
            Text(
                text = "${device.name} (${device.address})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )

            // Display device RSSI
            Text(
                text = "RSSI: ${device.rssi} dBm",
                fontSize = 14.sp,
                color = Color.White
            )

            // Display optional Broadcast ID if available
            device.broadcastId?.let {
                Text(
                    text = "Broadcast ID: $it",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }

            // Overlay for selected BIS channels
            if (device.selectedBisIndexes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp) // Top spacing from previous Text
                        .background(Color(0x33000000)) // Semi-transparent background
                        .padding(horizontal = 6.dp, vertical = 2.dp) // Inner padding
                ) {
                    Text(
                        text = "Selected BIS: ",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                    // Display each selected BIS index
                    device.selectedBisIndexes.forEach { index ->
                        Text("$index ", color = Color.Green, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
