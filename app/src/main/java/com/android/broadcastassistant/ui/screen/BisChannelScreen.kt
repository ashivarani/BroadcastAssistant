package com.android.broadcastassistant.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.android.broadcastassistant.audio.PreviewAuracastDevice
import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable screen to display available BIS (Broadcast Isochronous Stream) channels
 * for a selected Auracast device, and show a live command log.
 *
 * Features:
 * - Lists BIS channels with language, audio role, stream config, and index.
 * - Handles BIS selection via clickable cards with animated fade color for switching/selected.
 * - Displays chronological command logs with timestamps.
 * - Auto-scrolls to the latest log whenever [commandLogs] updates.
 *
 * @param device The [AuracastDevice] whose BIS channels are displayed.
 * @param onBisSelected Callback triggered when a BIS channel is selected. Returns the BIS index.
 * @param onBack Callback triggered when the back button is pressed.
 * @param commandLogs List of [CommandLog] entries to display below BIS channels.
 */
@SuppressLint("SimpleDateFormat")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BisChannelScreen(
    device: AuracastDevice,
    onBisSelected: (Int) -> Unit,
    onBack: () -> Unit,
    commandLogs: List<CommandLog> = emptyList()
) {
    val listState = rememberLazyListState()

    // Local state for selected BIS and animation during switching
    var selectedBisIndex by remember { mutableIntStateOf(device.selectedBisIndex) }
    var switchingBisIndex by remember { mutableStateOf<Int?>(null) }
    var pendingBisIndex by remember { mutableStateOf<Int?>(null) }
    var isSwitching by remember { mutableStateOf(false) }

    // Auto-scroll to latest command log
    LaunchedEffect(commandLogs.size) {
        if (commandLogs.isNotEmpty()) {
            listState.animateScrollToItem(commandLogs.size - 1)
        }
    }

    // Handle BIS switching animation & call
    LaunchedEffect(pendingBisIndex) {
        pendingBisIndex?.let { bisIndex ->
            switchingBisIndex = bisIndex
            isSwitching = true
            onBisSelected(bisIndex) // Trigger BIS switch command
            delay(800) // simulate command sending / animation
            selectedBisIndex = bisIndex
            switchingBisIndex = null
            isSwitching = false
            pendingBisIndex = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available BIS Channels", color = ButtonTextWhite) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ButtonTextWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RoyalBlue,
                    titleContentColor = ButtonTextWhite,
                    navigationIconContentColor = ButtonTextWhite
                )
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Display device name
            Text(
                text = "Device: ${device.name.ifEmpty { "Unknown Device" }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // BIS channels and command logs
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                items(device.bisChannels) { bis ->
                    val isSelected = selectedBisIndex == bis.index
                    val isSwitchingThis = switchingBisIndex == bis.index

                    // Animate the card color smoothly
                    val cardColor by animateColorAsState(
                        targetValue = when {
                            isSwitchingThis -> Color.Yellow
                            isSelected -> Color.Green
                            else -> RoyalBlue
                        }
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable(enabled = !isSwitching) { pendingBisIndex = bis.index },
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                bis.language,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = ButtonTextWhite
                            )
                            bis.audioRole?.let {
                                Text("Role: $it", fontSize = 12.sp, color = ButtonTextWhite.copy(alpha = 0.8f))
                            }
                            bis.streamConfig?.let {
                                Text("Config: $it", fontSize = 12.sp, color = ButtonTextWhite.copy(alpha = 0.8f))
                            }
                            Text("BIS Index: ${bis.index}", fontSize = 12.sp, color = ButtonTextWhite)
                        }
                    }
                }

                // Command logs section
                if (commandLogs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Command Logs",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(commandLogs) { log ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = "[${SimpleDateFormat("HH:mm:ss").format(Date(log.timestamp))}] ${log.message}",
                                fontSize = 12.sp,
                                color = Color.Green,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview of [BisChannelScreen] using a fake Auracast device and sample logs.
 */
@Composable
@Preview(showBackground = true)
fun BisChannelScreenPreview() {
    val fakeDevice = PreviewAuracastDevice.fakeBroadcasters().first()
    BisChannelScreen(
        device = fakeDevice,
        onBisSelected = {},
        onBack = {},
        commandLogs = listOf(
            CommandLog("BIS switch sent to index 1"),
            CommandLog("BIS switch sent to index 2"),
            CommandLog("Error: Missing broadcast code")
        )
    )
}
