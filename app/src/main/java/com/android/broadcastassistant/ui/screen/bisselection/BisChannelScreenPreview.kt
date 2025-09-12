package com.android.broadcastassistant.ui.screen.bisselection

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.broadcastassistant.audio.PreviewAuracastDevice
import com.android.broadcastassistant.data.CommandLog

/**
 * Compose preview for [BisChannelScreen].
 *
 * Uses a fake Auracast device with sample BIS channels and command logs.
 * No real BLE operations are performed (ViewModel is null).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
@Preview(showBackground = true)
fun BisChannelScreenPreview() {
    // Use the first fake device from PreviewAuracastDevice
    val fakeDevice = PreviewAuracastDevice.fakeBroadcasters().first()

    BisChannelScreen(
        device = fakeDevice,
        viewModel = null, // Prevent runtime BLE/ViewModel operations in preview
        onBack = {}, // No-op back action
        initialLogs = listOf(
            CommandLog("BIS switch sent to index 1"), // sample log
            CommandLog("BIS switch sent to index 2"), // sample log
            CommandLog("Error: Missing broadcast code") // sample error log
        )
    )
}
