package com.android.broadcastassistant.ble

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.audio.BassControlPointBuilder
import com.android.broadcastassistant.audio.BassController
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
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
 * Manages BIS channel selection logic and triggers BASS Control Point switching.
 *
 * In this implementation, the **phone itself acts as the receiver**,
 * so the BIS switch is executed directly via the BASS GATT handler.
 *
 * @param onDeviceUpdate Callback to propagate updated device list to UI.
 * @param getCurrentDevices Lambda to fetch the latest known devices list.
 * @param bassController The handler that sends commands to the BASS service.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val onDeviceUpdate: (List<AuracastDevice>) -> Unit,
    private val getCurrentDevices: () -> List<AuracastDevice>,
    private val bassController: BassController
) {

    /**
     * Selects one or more BIS channels for a given [device],
     * updates state, and triggers BIS switch via the BASS Control Point.
     *
     * @param device The target Auracast broadcaster.
     * @param bisIndexes List of BIS indexes (1-based) to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndexes: List<Int>) {
        try {
            // Match requested BIS indexes against available channels
            val selectedChannels: List<BisChannel> =
                device.bisChannels.filter { bisIndexes.contains(it.index) }

            if (selectedChannels.isEmpty()) {
                logw("selectBisChannel: BIS indexes $bisIndexes not found for ${device.address}")
                return
            }

            logi(
                "selectBisChannel: Selecting BIS indexes=$bisIndexes " +
                        "(${selectedChannels.joinToString { it.language }}) for ${device.name}"
            )
            selectedChannels.forEach { channel ->
                logv("selectBisChannel: Using BIS → ${channel.toDebugString()}")
            }

            // Update device state for UI
            val updatedDevices = getCurrentDevices().map { d ->
                if (d.address == device.address) d.copy(selectedBisIndex = bisIndexes.first()) else d
            }
            onDeviceUpdate(updatedDevices)

            // Validate identifiers required for Control Point
            if (device.broadcastId == null) {
                loge("selectBisChannel: Cannot switch BIS — broadcastId is missing for ${device.address}")
                return
            }
            if (device.sourceId == null) {
                loge("selectBisChannel: Cannot switch BIS — sourceId is missing for ${device.address}")
                return
            }

            // Trigger BIS switching asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Build Control Point command with BisChannel list
                    val cpData = BassControlPointBuilder.buildSwitchCommand(
                        sourceId = device.sourceId,
                        bisChannels = selectedChannels,
                        broadcastId = device.broadcastId,
                        broadcastCode = device.broadcastCode
                    )
                    logv("selectBisChannel: Built CP data (len=${cpData.size}) for ${device.address}")

                    try {
                        // Send to BASS
                        bassController.sendControlPoint(device.address, cpData,false)
                        logi("selectBisChannel: BIS switch command sent successfully to ${device.address}")
                    } catch (se: SecurityException) {
                        loge(
                            "selectBisChannel: SecurityException — Missing BLUETOOTH_CONNECT permission",
                            se
                        )
                        onMissingPermissions?.invoke(PermissionHelper.getRequiredPermissions())
                    }
                } catch (e: Exception) {
                    loge("selectBisChannel: Failed to build/send BIS switch command for ${device.address}", e)
                }
            }
        } catch (e: Exception) {
            loge("selectBisChannel: Unexpected error for ${device.address}", e)
        }
    }

    /**
     * Convenience overload for selecting a single BIS index.
     *
     * @param device The target Auracast broadcaster.
     * @param bisIndex Single BIS index (1-based) to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        try {
            logd("selectBisChannel(single): Request to select BIS index=$bisIndex for ${device.address}")
            selectBisChannel(device, listOf(bisIndex))
        } catch (e: Exception) {
            loge("selectBisChannel(single): Failed for ${device.address}", e)
        }
    }

    /**
     * Callback invoked when runtime permissions are missing.
     *
     * Should be assigned by the UI layer to prompt the user.
     */
    var onMissingPermissions: ((Array<String>) -> Unit)? = null
}
