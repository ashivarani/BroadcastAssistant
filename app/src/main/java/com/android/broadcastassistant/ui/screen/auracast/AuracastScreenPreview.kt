package com.android.broadcastassistant.ui.screen.auracast

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.rememberLazyListState
import com.android.broadcastassistant.audio.PreviewAuracastDevice
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Preview for AuracastScreen using dummy data.
 *
 * - Uses static lists for broadcasters and receivers.
 * - No real ViewModel or BLE scanning is required.
 * - Shows selected BIS and device highlighting in preview.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Preview(showBackground = true, apiLevel = 34)
@Composable
fun AuracastScreenPreview() {
    // Fake broadcasters
    val broadcasters = PreviewAuracastDevice.fakeBroadcasters()

    // Static receiver list
    val receivers = listOf(
        AuracastDevice(
            name = "Receiver 1",
            address = "00:11:22:33:44:01",
            rssi = -60,
            broadcastId = null,
            selectedBisIndexes = listOf(0) // example BIS selected
        ),
        AuracastDevice(
            name = "Receiver 2",
            address = "00:11:22:33:44:02",
            rssi = -55,
            broadcastId = null,
            selectedBisIndexes = emptyList()
        )
    )

    // Display merged devices in preview
    AuracastDeviceList(
        devices = broadcasters + receivers,
        permissionsGranted = true,
        statusMessage = "Preview: ${broadcasters.size + receivers.size} devices found",
        onDeviceClick = { /* No-op for preview */ },
        modifier = Modifier,
        listState = rememberLazyListState(),
        selectedDeviceAddress = null
    )
}
