package com.android.broadcastassistant.ui

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
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.viewmodel.AuracastViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(viewModel: AuracastViewModel) {
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState(initial = false)
    val permissionsGranted by viewModel.permissionsGranted.collectAsState(initial = false)
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    AuracastScreen(
        devices = devices,
        isScanning = isScanning,
        permissionsGranted = permissionsGranted,
        statusMessage = statusMessage,
        onToggleScan = { viewModel.toggleScan() },
        onSelectBisChannel = { device, bisIndex ->
            viewModel.selectBisChannel(device, bisIndex)
        }
    )
}

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
    val royalBlue = Color(0xFF1E40AF)

    Scaffold(
        topBar = {
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
                    Button(
                        onClick = onToggleScan,
                        enabled = permissionsGranted, // Disable if no permission
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
            // Show status message
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Permission warning
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

            if (devices.isEmpty()) {
                if (isScanning) {
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
                LazyColumn {
                    items(devices) { deviceItem ->
                        AuracastDeviceCardSimple(deviceItem) {
                            // For simplicity, pick first BIS or let VM decide
                            deviceItem.bisChannels.firstOrNull()?.let { bis ->
                                onSelectBisChannel(deviceItem, bis.index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastDeviceCardSimple(
    device: AuracastDevice,
    onSelectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E40AF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Device name + address
            Text(
                text = "${device.name} (${device.address})",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                fontWeight = FontWeight.Bold
            )

            // RSSI
            Text(
                text = "RSSI: ${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )

            // Broadcast ID
            device.broadcastId?.let {
                Text(
                    text = "Broadcast ID: $it",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Button
            Button(
                onClick = onSelectClick,
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

@Preview(showBackground = true, apiLevel = 34)
@Composable
fun AuracastScreenPreview() {
    val dummyDevices = com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource.fakeBroadcasters()

    AuracastScreen(
        devices = dummyDevices,
        isScanning = true,
        permissionsGranted = true,
        statusMessage = "Scanning – ${dummyDevices.size} devices found",
        onToggleScan = {},
        onSelectBisChannel = { _, _ -> }
    )
}
