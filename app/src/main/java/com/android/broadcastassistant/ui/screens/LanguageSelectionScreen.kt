package com.android.broadcastassistant.ui.screens

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
                title = { Text("Available BIS Channels") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(
                text = "Device: ${device.name.ifEmpty { "Unknown Device" }}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (device.bisChannels.isEmpty()) {
                Text("No BIS channels available for this device.")
            } else {
                LazyColumn {
                    items(device.bisChannels) { bis ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onBisSelected(bis.index) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = bis.language,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                bis.audioRole?.let {
                                    Text("Role: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                bis.streamConfig?.let {
                                    Text("Config: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("BIS Index: ${bis.index}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun LanguageSelectionScreenPreview() {
    val fakeDevice = FakeAuracastBroadcasterSource.fakeBroadcasters().first()
    LanguageSelectionScreen(
        device = fakeDevice,
        onBisSelected = {},
        onBack = {}
    )
}

