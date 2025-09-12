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
 */
@Composable
@Preview(showBackground = true, apiLevel = 34)
fun AuracastScreenPreview() {
    // Generate dummy devices from preview helper
    val dummyDevices: List<AuracastDevice> = PreviewAuracastDevice.fakeBroadcasters().mapIndexed { index, device ->
        // Mark every other device with selected BIS indices for demonstration
        device.copy(
            selectedBisIndexes = if (index % 2 == 0) listOf(1, 2) else emptyList()
        )
    }

    // Display AuracastScreen with preview parameters
    AuracastScreen(
        devices = dummyDevices,
        isScanning = true, // simulate scanning state
        permissionsGranted = true, // simulate permissions granted
        statusMessage = "Scanning – ${dummyDevices.size} devices found", // show status message
        onToggleScan = {}, // no-op for preview
        onDeviceClick = {} // no-op for preview
    )
}
