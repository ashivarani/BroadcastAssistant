package com.android.broadcastassistant.ui.screen.auracast

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.broadcastassistant.audio.PreviewAuracastDevice
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Preview for [AuracastScreen] using fake Auracast devices.
 *
 * Demonstrates:
 * - Scan active state
 * - Permissions granted
 * - Status message display
 * - Selected BIS highlighting for alternate devices
 * - Broadcasters vs Receivers differentiation
 */
@Composable
@Preview(showBackground = true, apiLevel = 34)
fun AuracastScreenPreview() {
    // Generate dummy broadcasters
    val broadcasterDevices: List<AuracastDevice> = PreviewAuracastDevice.fakeBroadcasters().mapIndexed { index, device ->
        device.copy(
            selectedBisIndexes = if (index % 2 == 0) listOf(1, 2) else emptyList()
        )
    }

    // Generate dummy receivers manually (broadcastId = null)
    val receiverDevices: List<AuracastDevice> = List(3) { index ->
        AuracastDevice(
            name = "Receiver $index",
            address = "00:11:22:33:44:${index}5",
            rssi = -50 + index,
            broadcastId = null, // null = receiver
            selectedBisIndexes = if (index % 2 != 0) listOf(1) else emptyList()
        )
    }

    // Display AuracastScreen with separate broadcasters and receivers
    AuracastScreen(
        broadcasters = broadcasterDevices,
        receivers = receiverDevices,
        isScanning = true,
        permissionsGranted = true,
        statusMessage = "Scanning – ${broadcasterDevices.size + receiverDevices.size} devices found",
        onToggleScan = {},
        onDeviceClick = { device ->
            // Only simulate navigation for broadcasters in preview
            if (device.broadcastId != null) {
                println("Broadcaster clicked: ${device.name} – navigate to BIS selection")
            } else {
                println("Receiver clicked: ${device.name} – no BIS selection")
            }
        }
    )
}
