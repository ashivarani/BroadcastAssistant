package com.android.broadcastassistant.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.CommandLog
import com.android.broadcastassistant.ui.theme.AppTextPrimary
import com.android.broadcastassistant.ui.theme.ButtonTextWhite
import com.android.broadcastassistant.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.*

/**
 * Composable screen to display available BIS (Broadcast Isochronous Stream) channels
 * for a selected Auracast device, and show a live command log.
 *
 * Features:
 * - Lists BIS channels with language, audio role, stream config, and index.
 * - Handles BIS selection via clickable cards.
 * - Displays chronological command logs with timestamps.
 * - Auto-scrolls to the latest log whenever commandLogs updates.
 *
 * @param device The [AuracastDevice] whose BIS channels are displayed.
 * @param onBisSelected Callback triggered when a BIS channel is selected. Returns the BIS index.
 * @param onBack Callback triggered when the back button is pressed.
 * @param commandLogs List of [CommandLog] entries to display below BIS channels.
 */
@SuppressLint("SimpleDateFormat")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    device: AuracastDevice,
    onBisSelected: (Int) -> Unit,
    onBack: () -> Unit,
    commandLogs: List<CommandLog> = emptyList()
) {
    val listState = rememberLazyListState() // State for LazyColumn scrolling

    // Auto-scroll to the latest log whenever commandLogs changes
    LaunchedEffect(commandLogs.size) {
        if (commandLogs.isNotEmpty()) {
            listState.animateScrollToItem(commandLogs.size - 1)
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
                .padding(16.dp) // Outer padding for screen content
        ) {
            // Display device name
            Text(
                text = "Device: ${device.name.ifEmpty { "Unknown Device" }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // LazyColumn to show BIS channels and command logs
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f) // Take remaining vertical space
            ) {
                // Display each BIS channel as a clickable Card
                items(device.bisChannels) { bis ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { onBisSelected(bis.index) }, // Trigger BIS selection
                        colors = CardDefaults.cardColors(RoyalBlue),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Language name
                            Text(
                                bis.language,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = ButtonTextWhite
                            )
                            // Optional audio role
                            bis.audioRole?.let {
                                Text(
                                    "Role: $it",
                                    fontSize = 12.sp,
                                    color = ButtonTextWhite.copy(alpha = 0.8f)
                                )
                            }
                            // Optional stream configuration
                            bis.streamConfig?.let {
                                Text(
                                    "Config: $it",
                                    fontSize = 12.sp,
                                    color = ButtonTextWhite.copy(alpha = 0.8f)
                                )
                            }
                            // BIS index
                            Text(
                                "BIS Index: ${bis.index}",
                                fontSize = 12.sp,
                                color = ButtonTextWhite
                            )
                        }
                    }
                }

                // Command logs section below BIS list
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
                                .background(Color.Black) // Log background
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = "[${SimpleDateFormat("HH:mm:ss").format(Date(log.timestamp))}] ${log.message}",
                                fontSize = 12.sp,
                                color = Color.Green,
                                fontFamily = FontFamily.Monospace // Monospace for log readability
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview of [LanguageSelectionScreen] using fake Auracast device and sample logs.
 */
@Composable
@Preview(showBackground = true)
fun LanguageSelectionScreenPreview() {
    val fakeDevice = FakeAuracastBroadcasterSource.fakeBroadcasters().first()
    LanguageSelectionScreen(
        device = fakeDevice,
        onBisSelected = {}, // No-op for preview
        onBack = {},        // No-op for preview
        commandLogs = listOf(
            CommandLog("BIS switch sent to index 1"),
            CommandLog("BIS switch sent to index 2"),
            CommandLog("Error: Missing broadcast code")
        )
    )
}
