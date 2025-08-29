package com.android.broadcastassistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource

@Composable
fun BroadcasterListScreen(
    devices: List<AuracastDevice>,
    onDeviceClick: (AuracastDevice) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        items(devices) { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onDeviceClick(device) },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = device.name.ifEmpty { "Unknown Device" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Address: ${device.address}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "RSSI: ${device.rssi} dBm",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun BroadcasterListScreenPreview() {
    val fakeDevices = FakeAuracastBroadcasterSource.fakeBroadcasters()
    BroadcasterListScreen(
        devices = fakeDevices,
        onDeviceClick = {}
    )
}
