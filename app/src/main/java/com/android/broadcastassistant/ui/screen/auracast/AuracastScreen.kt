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
 * Main screen displaying Auracast broadcasters.
 *
 * Shows a top app bar with scan toggle button, a list of devices,
 * highlights the selected device, and handles permission & status messages.
 *
 * @param devices List of [AuracastDevice]s discovered or mocked for UI.
 * @param isScanning Whether BLE scanning is currently active.
 * @param permissionsGranted Whether Bluetooth permissions are granted.
 * @param statusMessage Optional status message to display above the list.
 * @param onToggleScan Callback to start/stop scanning.
 * @param onDeviceClick Callback when a device is clicked.
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
    // Remember scroll state for LazyColumn
    val listState = rememberLazyListState()

    // Track the currently selected device by its address
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    Scaffold(
        // Top app bar with title and scan toggle button
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
                    Button(
                        onClick = onToggleScan, // Trigger scan toggle
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
        // Display the list of Auracast devices
        AuracastDeviceList(
            devices = devices,
            permissionsGranted = permissionsGranted,
            statusMessage = statusMessage,
            onDeviceClick = { device ->
                selectedDeviceAddress = device.address // update selected device
                onDeviceClick(device) // propagate click event
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding), // respect Scaffold padding
            listState = listState,
            selectedDeviceAddress = selectedDeviceAddress // highlight selected device
        )
    }
}
