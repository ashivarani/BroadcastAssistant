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
 * Displays a scrollable list of Auracast devices (broadcasters + connected receivers).
 *
 * Features:
 * - Shows status messages above the list.
 * - Handles permission warnings if Bluetooth permissions are missing.
 * - Highlights the currently selected device.
 *
 * @param devices List of [AuracastDevice]s to display.
 * @param permissionsGranted Whether required Bluetooth permissions are granted.
 * @param statusMessage Optional status message to show above the list.
 * @param onDeviceClick Callback invoked when a broadcaster device is clicked.
 * @param modifier Optional [Modifier] for the outer container.
 * @param listState [LazyListState] for scroll control.
 * @param selectedDeviceAddress Address of the currently selected device (for highlighting).
 */
@Composable
fun AuracastDeviceList(
    devices: List<AuracastDevice>,
    permissionsGranted: Boolean,
    statusMessage: String,
    onDeviceClick: (AuracastDevice) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState,
    selectedDeviceAddress: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status message (optional)
        if (statusMessage.isNotBlank()) {
            Text(
                text = statusMessage,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Permission check
        if (!permissionsGranted) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bluetooth permissions are required.",
                    color = Color.Red
                )
            }
            return@Column
        }

        // Split: broadcasters first, then receivers
        val broadcasters = devices.filter { it.broadcastId != null }
        val receivers = devices.filter { it.broadcastId == null }
        val sortedDevices = broadcasters + receivers

        if (sortedDevices.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                items(sortedDevices) { device ->
                    val isSelected = device.address == selectedDeviceAddress

                    AuracastDeviceCard(
                        device = device,
                        isSelected = isSelected,
                        onClick = {
                            if (device.broadcastId != null) {
                                onDeviceClick(device)
                            }
                        }
                    )
                }
            }
        } else {
            // Fallback text when no devices found
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No broadcasters or connected receivers found",
                    color = AppTextPrimary
                )
            }
        }
    }
}
