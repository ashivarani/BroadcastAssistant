package com.android.broadcastassistant.ui.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * Main screen showing discovered Auracast broadcasters,
 * scanning controls, and BIS (stream) selection.
 *
 * Responsibilities:
 * - Observe ViewModel state (devices, scan status, permissions).
 * - Render UI controls for scanning and BIS selection.
 * - Propagate user actions back to ViewModel.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(viewModel: AuracastViewModel) {
    logd("AuracastScreen(ViewModel): Entry → collecting state")

    // Collect reactive state from the ViewModel
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState(initial = false)
    val permissionsGranted by viewModel.permissionsGranted.collectAsState(initial = false)
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    logi("AuracastScreen(ViewModel): State collected → devices=${devices.size}, isScanning=$isScanning")

    // Delegate to stateless version of the screen, passing callbacks
    AuracastScreen(
        devices = devices,
        isScanning = isScanning,
        permissionsGranted = permissionsGranted,
        statusMessage = statusMessage,
        onToggleScan = {
            try {
                logd("AuracastScreen(ViewModel): onToggleScan clicked")
                // Trigger scan toggle in ViewModel
                viewModel.toggleScan()
                logi("AuracastScreen(ViewModel): toggleScan executed")
            } catch (e: Exception) {
                loge("AuracastScreen(ViewModel): toggleScan failed", e)
            }
        },
        onSelectBisChannel = { device, bisIndex ->
            try {
                logd("AuracastScreen(ViewModel): onSelectBisChannel → BIS=$bisIndex for ${device.address}")
                // Forward BIS selection to ViewModel
                viewModel.selectBisChannel(device, bisIndex)
                logi("AuracastScreen(ViewModel): BIS selection triggered for ${device.address}")
            } catch (e: Exception) {
                loge("AuracastScreen(ViewModel): Failed to select BIS for ${device.address}", e)
            }
        }
    )
}

/**
 * Stateless version of [AuracastScreen] with all state and actions passed in.
 *
 * @param devices List of discovered broadcasters.
 * @param isScanning Whether scanning is active.
 * @param permissionsGranted Whether permissions are granted.
 * @param statusMessage Status or error message.
 * @param onToggleScan Callback for Start/Stop scan.
 * @param onSelectBisChannel Callback when BIS is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(
    devices: List<AuracastDevice>,
    isScanning: Boolean,
    permissionsGranted: Boolean,
    statusMessage: String,
    onToggleScan: () -> Unit,
    onSelectBisChannel: (AuracastDevice, Int) -> Unit
) {
    logd("AuracastScreen(Stateless): Entry → devices=${devices.size}, isScanning=$isScanning")

    val royalBlue = Color(0xFF1E40AF)

    // Scaffold provides the main app layout structure
    Scaffold(
        topBar = {
            // Top app bar with title + scan button
            TopAppBar(
                title = {
                    Text(
                        "Auracast Assistant",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Color.Black
                    )
                },
                actions = {
                    // Scan toggle button
                    Button(
                        onClick = {
                            try {
                                logd("AuracastScreen: Scan button clicked (isScanning=$isScanning)")
                                onToggleScan()
                                logi("AuracastScreen: Scan button handled")
                            } catch (e: Exception) {
                                loge("AuracastScreen: Failed to toggle scan", e)
                            }
                        },
                        enabled = permissionsGranted, // Disabled if no permission
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Color(0xFF0F2A72) else royalBlue,
                            contentColor = if (isScanning) Color.Yellow else Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                    }
                }
            )
        }
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
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // If permissions missing, show warning and return
            if (!permissionsGranted) {
                logw("AuracastScreen: Permissions not granted → showing warning")
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

            // If no devices discovered yet
            if (devices.isEmpty()) {
                if (isScanning) {
                    logd("AuracastScreen: Scanning but no devices found yet")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Scanning for Auracast devices...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                // Render list of discovered devices
                logi("AuracastScreen: Rendering ${devices.size} devices")
                LazyColumn {
                    items(devices) { deviceItem ->
                        AuracastDeviceCardSimple(deviceItem) {
                            try {
                                // For simplicity, auto-select first BIS
                                val firstBis = deviceItem.bisChannels.firstOrNull()
                                if (firstBis != null) {
                                    logd("AuracastScreen: Device card clicked → BIS=${firstBis.index} for ${deviceItem.address}")
                                    onSelectBisChannel(deviceItem, firstBis.index)
                                } else {
                                    logw("AuracastScreen: No BIS available for ${deviceItem.address}")
                                }
                            } catch (e: Exception) {
                                loge("AuracastScreen: Failed handling device click for ${deviceItem.address}", e)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simple card UI for displaying an [AuracastDevice].
 *
 * @param device The broadcaster device to show.
 * @param onSelectClick Callback when "Select BIS" button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastDeviceCardSimple(
    device: AuracastDevice,
    onSelectClick: () -> Unit
) {
    logd("AuracastDeviceCardSimple: Rendering card for ${device.address}")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E40AF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Device name and address
            Text(
                text = "${device.name} (${device.address})",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold
            )

            // Signal strength
            Text(
                text = "RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )

            // Broadcast ID if present
            device.broadcastId?.let {
                Text(
                    text = "Broadcast ID: $it",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action button for selecting BIS
            Button(
                onClick = {
                    try {
                        logd("AuracastDeviceCardSimple: Select BIS clicked for ${device.address}")
                        onSelectClick()
                        logi("AuracastDeviceCardSimple: Select BIS handled for ${device.address}")
                    } catch (e: Exception) {
                        loge("AuracastDeviceCardSimple: Failed to handle BIS selection for ${device.address}", e)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Yellow,
                    contentColor = Color.Black
                )
            ) {
                Text("Select BIS")
            }
        }
    }
}

/**
 * Preview of [AuracastScreen] using fake devices for UI testing.
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
        onToggleScan = {},
        onSelectBisChannel = { _, _ -> }
    )
}
