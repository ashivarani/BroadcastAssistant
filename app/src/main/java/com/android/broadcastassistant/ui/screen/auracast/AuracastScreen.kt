package com.android.broadcastassistant.ui.screen.auracast

import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * Main screen for Auracast Assistant.
 *
 * - Collects state from [AuracastViewModel]
 * - Displays broadcasters + receivers
 * - Handles scan toggle + device click via [onDeviceClick]
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuracastScreen(
    viewModel: AuracastViewModel,
    onDeviceClick: (AuracastDevice) -> Unit
) {
    val broadcasters by viewModel.broadcasters.collectAsStateWithLifecycle()
    val receivers by viewModel.receivers.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    // Merge broadcasters first, then receivers
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
                    Button(
                        onClick = { viewModel.toggleScan() },
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
        AuracastDeviceList(
            devices = allDevices,
            permissionsGranted = permissionsGranted,
            statusMessage = statusMessage,
            onDeviceClick = { device ->
                selectedDeviceAddress = device.address
                onDeviceClick(device) // delegate to AppNavHost
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            listState = listState,
            selectedDeviceAddress = selectedDeviceAddress
        )
    }
}
