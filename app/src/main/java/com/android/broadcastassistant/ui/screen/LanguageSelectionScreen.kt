package com.android.broadcastassistant.ui.screen

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.android.broadcastassistant.ui.theme.AppTextPrimary
import com.android.broadcastassistant.ui.theme.ButtonTextWhite
import com.android.broadcastassistant.ui.theme.RoyalBlue
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.loge

/**
 * Screen to display all available BIS channels for a selected Auracast device.
 *
 * User can select a BIS channel for audio routing or go back to the previous screen.
 *
 * @param device The AuracastDevice whose BIS channels are displayed
 * @param onBisSelected Callback when a BIS channel is selected (returns BIS index)
 * @param onBack Callback triggered when the user presses the back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionScreen(
    device: AuracastDevice,
    onBisSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available BIS Channels", color = ButtonTextWhite) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            try {
                                logd("LanguageSelectionScreen: Back button clicked")
                                onBack()
                            } catch (e: Exception) {
                                loge("LanguageSelectionScreen: Error in onBack callback", e)
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ButtonTextWhite)
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

            if (device.bisChannels.isEmpty()) {
                // Show message if no BIS channels
                Text(
                    "No BIS channels available for this device.",
                    color = AppTextPrimary
                )
            } else {
                // List available BIS channels
                LazyColumn {
                    items(device.bisChannels) { bis ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    try {
                                        logi("LanguageSelectionScreen: BIS channel clicked → index=${bis.index}, language=${bis.language}")
                                        onBisSelected(bis.index)
                                    } catch (e: Exception) {
                                        loge("LanguageSelectionScreen: Error in onBisSelected callback", e)
                                    }
                                },
                            colors = CardDefaults.cardColors(RoyalBlue),
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
                                    Text(
                                        "Role: $it",
                                        fontSize = 12.sp,
                                        color = ButtonTextWhite.copy(alpha = 0.8f)
                                    )
                                }
                                bis.streamConfig?.let {
                                    Text(
                                        "Config: $it",
                                        fontSize = 12.sp,
                                        color = ButtonTextWhite.copy(alpha = 0.8f)
                                    )
                                }
                                Text(
                                    "BIS Index: ${bis.index}",
                                    fontSize = 12.sp,
                                    color = ButtonTextWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Preview for LanguageSelectionScreen using fake device and BIS channels.
 */
@Preview(showBackground = true)
@Composable
fun LanguageSelectionScreenPreview() {
    val fakeDevice = FakeAuracastBroadcasterSource.fakeBroadcasters().first()
    logd("LanguageSelectionScreenPreview: Rendering preview with ${fakeDevice.bisChannels.size} BIS channels")

    LanguageSelectionScreen(
        device = fakeDevice,
        onBisSelected = { bisIndex ->
            logi("LanguageSelectionScreenPreview: BIS channel selected → index=$bisIndex")
        },
        onBack = {
            logi("LanguageSelectionScreenPreview: Back pressed")
        }
    )

    logi("LanguageSelectionScreenPreview: Preview rendered")
}
