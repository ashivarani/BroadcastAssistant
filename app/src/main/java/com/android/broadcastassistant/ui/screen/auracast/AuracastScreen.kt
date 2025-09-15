package com.android.broadcastassistant.ui.screen.auracast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Main screen displaying Auracast broadcasters and receivers.
 *
 * Features:
 * - Top App Bar with app title and scan toggle button.
 * - Shows merged list of broadcasters and receivers.
 * - Highlights the currently selected device.
 * - Handles permission and status messages.
 * - Restricts clicks for receivers (no BIS channels).
 *
 * @param broadcasters List of broadcaster [AuracastDevice]s discovered or mocked.
 * @param receivers List of receiver [AuracastDevice]s discovered via Scan Delegators.
 * @param isScanning Whether BLE scanning is currently active.
 * @param permissionsGranted Whether Bluetooth permissions are granted.
 * @param statusMessage Optional status message displayed above the list.
 * @param onToggleScan Callback to start/stop scanning.
 * @param onDeviceClick Callback invoked when a broadcaster device is clicked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(
    broadcasters: List<AuracastDevice>,
    receivers: List<AuracastDevice>,
    isScanning: Boolean,
    permissionsGranted: Boolean,
    statusMessage: String,
    onToggleScan: () -> Unit,
    onDeviceClick: (AuracastDevice) -> Unit
) {
    val listState = rememberLazyListState() // For controlling scrolling
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) } // Track selected device

    // Merge broadcasters and receivers for display
    val allDevices = broadcasters + receivers

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Auracast Assistant",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                },
                actions = {
                    // Scan toggle button
                    Button(
                        onClick = onToggleScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) Color(0xFF0D47A1) else Color(0xFF1976D2),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (isScanning) "Stop Scan" else "Start Scan",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        },
        containerColor = Color.White
    ) { padding ->
        // Display merged list of broadcasters and receivers
        AuracastDeviceList(
            devices = allDevices,
            permissionsGranted = permissionsGranted,
            statusMessage = statusMessage,
            onDeviceClick = { device ->
                selectedDeviceAddress = device.address // Highlight selected device

                // Only navigate/call callback if the device is a broadcaster
                if (device.broadcastId != null) {
                    onDeviceClick(device)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            listState = listState,
            selectedDeviceAddress = selectedDeviceAddress
        )
    }
}
