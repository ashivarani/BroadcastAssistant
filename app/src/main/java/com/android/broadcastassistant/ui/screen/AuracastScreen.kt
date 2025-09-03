package com.android.broadcastassistant.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.ui.theme.AppTextPrimary
import com.android.broadcastassistant.ui.theme.ButtonTextWhite
import com.android.broadcastassistant.ui.theme.RoyalBlue
import com.android.broadcastassistant.ui.theme.SkyBlue
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.loge

/**
 * Composable screen displaying a list of Auracast devices, scan status, and controls.
 *
 * Handles scanning toggle, device click actions, and displays Bluetooth permission status.
 *
 * @param devices List of discovered Auracast devices
 * @param isScanning Boolean indicating whether scanning is active
 * @param permissionsGranted Boolean indicating whether Bluetooth permissions are granted
 * @param statusMessage Optional status message displayed at the top of the screen
 * @param onToggleScan Callback triggered when the scan button is pressed
 * @param onDeviceClick Callback triggered when a device card is clicked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(
    devices: List<AuracastDevice>,
    isScanning: Boolean,
    permissionsGranted: Boolean,
    statusMessage: String,
    onToggleScan: () -> Unit,
    onDeviceClick: (AuracastDevice) -> Unit
) {
    logd("AuracastScreen: Rendering screen with ${devices.size} devices, scanning=$isScanning")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Auracast Assistant",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = AppTextPrimary
                    )
                },
                actions = {
                    // Scan toggle button
                    Button(
                        onClick = {
                            try {
                                logd("AuracastScreen: Scan button clicked. Current scanning state: $isScanning")
                                onToggleScan()
                            } catch (e: Exception) {
                                loge("AuracastScreen: Error in onToggleScan callback", e)
                            }
                        },
                        enabled = permissionsGranted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Color(0xFF0D47A1) else RoyalBlue,
                            contentColor = if (isScanning) Color.Yellow else ButtonTextWhite
                        ),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (isScanning) "Stop Scanning" else "Start Scanning",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SkyBlue,
                    titleContentColor = AppTextPrimary
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Show status message if provided
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(color = AppTextPrimary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Handle missing Bluetooth permissions
            if (!permissionsGranted) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bluetooth permissions are required to scan.",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Column
            }

            // Show scanning message if no devices found yet
            if (devices.isEmpty()) {
                if (isScanning) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Scanning for Auracast devices...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = AppTextPrimary)
                        )
                    }
                }
            } else {
                // Display list of devices
                LazyColumn {
                    items(devices) { deviceItem ->
                        AuracastDeviceCardSimple(device = deviceItem) {
                            try {
                                logi("AuracastScreen: Device clicked → ${deviceItem.address}")
                                onDeviceClick(deviceItem)
                            } catch (e: Exception) {
                                loge("AuracastScreen: Error in device click callback", e)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple card displaying Auracast device information.
 *
 * @param device AuracastDevice instance to display
 * @param onClick Callback triggered when card is clicked
 */
@Composable
fun AuracastDeviceCardSimple(
    device: AuracastDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                try {
                    logd("AuracastDeviceCardSimple: Card clicked → ${device.address}")
                    onClick()
                } catch (e: Exception) {
                    loge("AuracastDeviceCardSimple: Error in onClick callback", e)
                }
            },
        colors = CardDefaults.cardColors(RoyalBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "${device.name} (${device.address})",
                style = MaterialTheme.typography.titleMedium.copy(color = ButtonTextWhite),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium.copy(color = ButtonTextWhite)
            )
            device.broadcastId?.let {
                Text(
                    text = "Broadcast ID: $it",
                    style = MaterialTheme.typography.bodyMedium.copy(color = ButtonTextWhite)
                )
            }
        }
    }
}

/**
 * Preview for AuracastScreen with fake devices.
 */
@Preview(showBackground = true, apiLevel = 34)
@Composable
fun AuracastScreenPreview() {
    val dummyDevices = FakeAuracastBroadcasterSource.fakeBroadcasters()
    logd("AuracastScreenPreview: Rendering preview with ${dummyDevices.size} devices")

    AuracastScreen(
        devices = dummyDevices,
        isScanning = true,
        permissionsGranted = true,
        statusMessage = "Scanning – ${dummyDevices.size} devices found",
        onToggleScan = {
            logi("AuracastScreenPreview: Scan toggle pressed")
        },
        onDeviceClick = { device ->
            logi("AuracastScreenPreview: Device clicked → ${device.address}")
        }
    )
}