package com.android.broadcastassistant.ui.screen.bisselection

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.CommandLog
import com.android.broadcastassistant.viewmodel.AuracastViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Screen displaying all BIS channels for a given Auracast device.
 *
 * Allows selection, highlighting, and sending commands via the ViewModel.
 *
 * @param device The selected Auracast device
 * @param viewModel Optional AuracastViewModel for sending BIS commands
 * @param onBack Callback to navigate back
 * @param initialLogs Optional initial command logs to display
 * @param isPreview If true, disables interaction and command sending (used for Compose preview)
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun BisChannelScreen(
    device: AuracastDevice,
    viewModel: AuracastViewModel? = null,
    onBack: () -> Unit,
    initialLogs: List<CommandLog> = emptyList(),
    isPreview: Boolean = false
) {
    val listState = rememberLazyListState()
    var switchingBisIndex by remember { mutableStateOf<Int?>(null) } // currently switching BIS for animation
    val commandLogs = remember { mutableStateListOf<CommandLog>().apply { addAll(initialLogs) } }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Top app bar with back navigation
            BisScreenTopBar(onBack = onBack)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Display device info
                item {
                    Text(
                        text = "Device: ${device.name.ifEmpty { "Unknown Device" }}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // List of BIS channels with selection support
                item {
                    BisChannelList(
                        device = device,
                        switchingActive = switchingBisIndex != null,
                        onBisSelected = { updatedSelection ->
                            device.selectedBisIndexes = updatedSelection
                            switchingBisIndex = updatedSelection.lastOrNull() // highlight last selected
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Command log section (optional)
                if (commandLogs.isNotEmpty()) {
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                    item { BisCommandLogList(commandLogs = commandLogs) }
                }

                // Spacer at the bottom for safe area / button
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // Apply Selection button at the bottom (not shown in preview)
        if (!isPreview) {
            Button(
                onClick = {
                    val selectedIndexes = device.selectedBisIndexes
                    if (selectedIndexes.isNotEmpty()) {
                        switchingBisIndex = selectedIndexes.lastOrNull() // highlight last BIS
                        viewModel?.let { vm ->
                            coroutineScope.launch {
                                vm.selectBisChannels(device, selectedIndexes) // send command via ViewModel
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter),
                enabled = device.selectedBisIndexes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (device.selectedBisIndexes.isNotEmpty())
                        Color(0xFF4CAF50) else Color.LightGray
                )
            ) {
                Text(
                    text = "Apply Selection (${device.selectedBisIndexes.joinToString()})",
                    color = if (device.selectedBisIndexes.isNotEmpty()) Color.White else Color.Gray
                )
            }
        }
    }

    // Auto-reset BIS highlight after animation delay
    if (!isPreview) {
        LaunchedEffect(switchingBisIndex) {
            switchingBisIndex?.let { index ->
                delay(800) // highlight duration
                if (switchingBisIndex == index) switchingBisIndex = null
            }
        }
    }
}

/**
 * Top app bar for BIS channel screen with back navigation.
 *
 * @param onBack Callback triggered when back icon is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BisScreenTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Available BIS Channels", color = Color.White) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF1976D2),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}
