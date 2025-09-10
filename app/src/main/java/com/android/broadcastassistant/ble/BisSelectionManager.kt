package com.android.broadcastassistant.ble

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.audio.BassControlPointBuilder
import com.android.broadcastassistant.audio.BassGattManager
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BassCommand
import com.android.broadcastassistant.data.CommandLog
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logv
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Manages BIS (Broadcast Isochronous Stream) selection for Auracast devices.
 *
 * Responsibilities:
 * 1. Selects one or multiple BIS channels for a given [AuracastDevice].
 * 2. Builds BASS Control Point "switch BIS" commands using [BassControlPointBuilder].
 * 3. Sends commands via [BassGattManager].
 * 4. Updates UI state and logs actions using [onDeviceUpdate] and [onCommandLog].
 * 5. Includes retry mechanism for transient failures.
 *
 * Note:
 * - Currently supports single-BIS selection only (multi-BIS is future-proofed but not used).
 * - Protected broadcasts, PA Sync, or BIG encryption are not supported in this version.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val onDeviceUpdate: (List<AuracastDevice>) -> Unit,
    private val getCurrentDevices: () -> List<AuracastDevice>,
    private val bassGattManager: BassGattManager,
    private val onCommandLog: (CommandLog) -> Unit
) {

    /**
     * Select multiple BIS indexes for a device and trigger a BASS Control Point switch.
     *
     * @param device The [AuracastDevice] to control.
     * @param bisIndexes List of BIS indexes to select. Currently only the first index is applied for single-BIS.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndexes: List<Int>) {
        try {
            // Filter selected BIS channels based on requested indexes
            val selectedChannels = device.bisChannels.filter { bisIndexes.contains(it.index) }

            // Abort if requested indexes are not available
            if (selectedChannels.isEmpty()) {
                logw("selectBisChannel: BIS indexes $bisIndexes not found for ${device.address}")
                onCommandLog(CommandLog("BIS indexes $bisIndexes not found for ${device.name}"))
                return
            }

            logi("selectBisChannel: Selecting BIS indexes=$bisIndexes for ${device.name}")
            selectedChannels.forEach { logv("selectBisChannel: Using BIS → ${it.toDebugString()}") }

            // Push log entry to UI
            onCommandLog(CommandLog("Selecting BIS indexes $bisIndexes for ${device.name}"))

            // Update device state in UI with first selected BIS index
            val updatedDevices = getCurrentDevices().map { d ->
                if (d.address == device.address) d.copy(selectedBisIndex = bisIndexes.first()) else d
            }
            onDeviceUpdate(updatedDevices)

            // Ensure device has required identifiers
            if (device.broadcastId == null || device.sourceId == null) {
                loge("selectBisChannel: Missing broadcastId or sourceId for ${device.address}")
                onCommandLog(CommandLog("Cannot switch BIS — missing broadcastId or sourceId for ${device.name}"))
                return
            }

            // Send BIS switch command asynchronously with retry logic
            CoroutineScope(Dispatchers.IO).launch {
                var attempt = 0
                val maxRetries = 2
                while (attempt <= maxRetries) {
                    try {
                        // Build BASS Control Point "switch BIS" command
                        val cpData = BassControlPointBuilder.buildSwitchCommand(
                            sourceId = device.sourceId,
                            bisChannels = selectedChannels,
                            broadcastId = device.broadcastId,
                            broadcastCode = device.broadcastCode
                        )
                        logv("selectBisChannel: Built CP data (len=${cpData.size}) for ${device.address}")

                        // Prepare command for GATT manager
                        val command = BassCommand(
                            deviceAddress = device.address,
                            controlPointData = cpData,
                            autoDisconnect = false
                        )

                        // Send command to device
                        bassGattManager.sendControlPoint(command)
                        logi("selectBisChannel: BIS switch command sent successfully to ${device.address}")
                        onCommandLog(CommandLog("BIS switch command sent to ${device.name}"))
                        break // success, exit retry loop

                    } catch (e: Exception) {
                        attempt++
                        if (attempt > maxRetries) {
                            loge("selectBisChannel: Failed after $maxRetries attempts for ${device.address}", e)
                            onCommandLog(CommandLog("Failed to send BIS switch for ${device.name}: ${e.message}"))
                        } else {
                            logw("selectBisChannel: Retry $attempt for ${device.address}")
                            onCommandLog(CommandLog("Retrying BIS switch for ${device.name} (attempt $attempt)"))
                            delay(1500L)
                        }
                    }
                }
            }

        } catch (e: Exception) {
            loge("selectBisChannel: Unexpected error for ${device.address}", e)
            onCommandLog(CommandLog("Unexpected error for ${device.name}: ${e.message}"))
        }
    }

    /**
     * Convenience method to select a single BIS index.
     *
     * @param device The [AuracastDevice] to control.
     * @param bisIndex Single BIS index to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        logd("selectBisChannel(single): Request to select BIS index=$bisIndex for ${device.address}")
        selectBisChannel(device, listOf(bisIndex))
    }
}
