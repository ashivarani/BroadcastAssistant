package com.android.broadcastassistant.ui.screen.auracast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.ui.theme.AppTextPrimary

/**
 * Displays a scrollable list of Auracast devices.
 *
 * Handles permission checks, status messages, and highlights the currently selected device.
 *
 * @param devices List of [AuracastDevice]s to display.
 * @param permissionsGranted Whether Bluetooth permissions are granted.
 * @param statusMessage Optional status message to show above the list.
 * @param onDeviceClick Callback invoked when a device is clicked.
 * @param modifier Optional [Modifier] for the outer container.
 * @param listState [LazyListState] for scrolling control.
 * @param selectedDeviceAddress Address of the currently selected device for highlighting.
 */
@Composable
fun AuracastDeviceList(
    devices: List<AuracastDevice>,
    permissionsGranted: Boolean,
    statusMessage: String,
    onDeviceClick: (AuracastDevice) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    selectedDeviceAddress: String? = null // highlight selected device
) {
    // Column container with padding and full size
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Show status message if provided
        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Show permissions warning if Bluetooth permissions not granted
        if (!permissionsGranted) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Bluetooth permissions are required.", color = Color.Red)
            }
            return@Column // Exit early if permissions are missing
        }

        // Show device list if devices are available
        if (devices.isNotEmpty()) {
            LazyColumn(state = listState) {
                items(devices) { device ->
                    // Determine if this device is currently selected
                    val isSelected = device.address == selectedDeviceAddress

                    // Display individual device card
                    AuracastDeviceCard(
                        device = device,
                        isSelected = isSelected, // highlight selected device
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}
