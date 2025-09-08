package com.android.broadcastassistant.ble

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.audio.BassControlPointBuilder
import com.android.broadcastassistant.audio.BassGattManager
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BassCommand
import com.android.broadcastassistant.data.CommandLog
import com.android.broadcastassistant.util.PermissionHelper
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logv
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages BIS (Broadcast Isochronous Stream) selection and BASS Control Point switching.
 *
 * Responsibilities:
 * 1. Selects one or multiple BIS channels for a given AuracastDevice.
 * 2. Builds BASS Control Point "switch BIS" commands via [BassControlPointBuilder].
 * 3. Sends commands to devices using [BassGattManager].
 * 4. Updates UI via [onDeviceUpdate] and logs actions via [onCommandLog].
 * 5. Handles missing permissions and runtime errors.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val onDeviceUpdate: (List<AuracastDevice>) -> Unit, // UI callback to refresh device list
    private val getCurrentDevices: () -> List<AuracastDevice>,   // Get latest device list
    private val bassGattManager: BassGattManager,               // GATT manager for BASS commands
    private val onCommandLog: (CommandLog) -> Unit              // Push command logs to UI
) {

    /** Called when runtime permissions are missing. Set by UI layer */
    var onMissingPermissions: ((Array<String>) -> Unit)? = null

    /**
     * Select multiple BIS indexes for a device and trigger BASS Control Point switch.
     *
     * @param device AuracastDevice to control
     * @param bisIndexes List of BIS indexes to select
     */
    fun selectBisChannel(device: AuracastDevice, bisIndexes: List<Int>) {
        try {
            // Filter selected BIS channels based on requested indexes
            val selectedChannels = device.bisChannels.filter { bisIndexes.contains(it.index) }

            // Abort if requested indexes are not available on this device
            if (selectedChannels.isEmpty()) {
                logw("selectBisChannel: BIS indexes $bisIndexes not found for ${device.address}")
                onCommandLog(CommandLog("BIS indexes $bisIndexes not found for ${device.name}"))
                return
            }

            logi("selectBisChannel: Selecting BIS indexes=$bisIndexes for ${device.name}")
            selectedChannels.forEach { logv("selectBisChannel: Using BIS → ${it.toDebugString()}") }

            // Push a log entry to UI
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

            // Send BIS switch command asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
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

                }.onFailure { e ->
                    // Handle permission errors
                    when (e) {
                        is SecurityException -> {
                            loge("selectBisChannel: Missing BLUETOOTH_CONNECT permission", e)
                            onMissingPermissions?.invoke(PermissionHelper.getRequiredPermissions())
                            onCommandLog(CommandLog("Failed to send BIS switch — missing permission for ${device.name}"))
                        }
                        else -> {
                            loge("selectBisChannel: Failed to build/send BIS switch command for ${device.address}", e)
                            onCommandLog(CommandLog("Failed to send BIS switch for ${device.name}: ${e.message}"))
                        }
                    }
                }
            }

        } catch (e: Exception) {
            loge("selectBisChannel: Unexpected error for ${device.address}", e)
            onCommandLog(CommandLog("Unexpected error for ${device.name}: ${e.message}"))
        }
    }

    /** Convenience overload to select a single BIS index */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        logd("selectBisChannel(single): Request to select BIS index=$bisIndex for ${device.address}")
        selectBisChannel(device, listOf(bisIndex))
    }
}
