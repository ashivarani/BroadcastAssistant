package com.android.broadcastassistant.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.loge

/**
 * Screen for selecting a Broadcast Isochronous Stream (BIS) channel
 * from a given [AuracastDevice].
 *
 * Responsibilities:
 * - Show the list of BIS channels available for the selected device.
 * - Allow the user to pick a BIS, propagating selection upward.
 * - Provide navigation back to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    device: AuracastDevice,
    onBisSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    logd("LanguageSelectionScreen: Entry → rendering for ${device.address}")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available BIS Channels") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            try {
                                logd("LanguageSelectionScreen: Back clicked for ${device.address}")
                                onBack()
                                logi("LanguageSelectionScreen: Back navigation executed")
                            } catch (e: Exception) {
                                loge("LanguageSelectionScreen: Failed handling back click", e)
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Show device name
            Text(
                text = "Device: ${device.name.ifEmpty { "Unknown Device" }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (device.bisChannels.isEmpty()) {
                logd("LanguageSelectionScreen: No BIS channels for ${device.address}")
                Text("No BIS channels available for this device.")
            } else {
                logi("LanguageSelectionScreen: Rendering ${device.bisChannels.size} BIS channels for ${device.address}")
                LazyColumn {
                    items(device.bisChannels) { bis ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    try {
                                        logd("LanguageSelectionScreen: BIS clicked → index=${bis.index} for ${device.address}")
                                        onBisSelected(bis.index)
                                        logi("LanguageSelectionScreen: BIS ${bis.index} selected for ${device.address}")
                                    } catch (e: Exception) {
                                        loge("LanguageSelectionScreen: Failed handling BIS selection for ${device.address}", e)
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                // Language label
                                Text(
                                    text = bis.language,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                // Optional role
                                bis.audioRole?.let {
                                    Text("Role: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Optional config
                                bis.streamConfig?.let {
                                    Text("Config: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Always show BIS index
                                Text("BIS Index: ${bis.index}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }

    logi("LanguageSelectionScreen: Rendered successfully for ${device.address}")
}

/**
 * Preview of [LanguageSelectionScreen] with fake data for UI testing.
 */
@Preview(showBackground = true)
@Composable
fun LanguageSelectionScreenPreview() {
    val fakeDevice = FakeAuracastBroadcasterSource.fakeBroadcasters().first()
    logd("LanguageSelectionScreenPreview: Rendering preview with ${fakeDevice.bisChannels.size} BIS channels")

    LanguageSelectionScreen(
        device = fakeDevice,
        onBisSelected = {},
        onBack = {}
    )

    logi("LanguageSelectionScreenPreview: Preview rendered")
}
