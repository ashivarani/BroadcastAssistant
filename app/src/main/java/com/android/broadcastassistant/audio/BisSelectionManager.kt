package com.android.broadcastassistant.audio

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles BIS channel selection and triggers actual switching logic via BASS.
 * Phone itself acts as the receiver — no external device configuration needed.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val onDeviceUpdate: (List<AuracastDevice>) -> Unit,
    private val getCurrentDevices: () -> List<AuracastDevice>,
    private val bassController: BassController // Our BASS GATT handler
) {

    /**
     * Selects one or more BIS channels for a device, updates state, and triggers BASS switch.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndexes: List<Int>) {
        val selectedChannels: List<BisChannel> =
            device.bisChannels.filter { bisIndexes.contains(it.index) }

        if (selectedChannels.isEmpty()) {
            Log.w(TAG, "BIS indexes $bisIndexes not found for ${device.address}")
            return
        }

        Log.i(
            TAG,
            "Selecting BIS indexes=$bisIndexes (${selectedChannels.joinToString { it.language }}) for ${device.name}"
        )

        // Update UI/device state
        val updatedDevices = getCurrentDevices().map { d ->
            if (d.address == device.address) d.copy(selectedBisIndex = bisIndexes.first()) else d
        }
        onDeviceUpdate(updatedDevices)

        // Ensure broadcastId and sourceId are available
        if (device.broadcastId == null) {
            Log.e(TAG, "Cannot switch BIS: broadcastId is missing for ${device.address}")
            return
        }
        if (device.sourceId == null) {
            Log.e(TAG, "Cannot switch BIS: sourceId is missing for ${device.address}")
            return
        }

        // Trigger actual BIS switching via BASS Control Point
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cpData = BassControlPointBuilder.buildSwitchCommand(
                    sourceId = device.sourceId, // sourceId from Broadcast Receive State
                    bisIndexes = bisIndexes,
                    broadcastId = device.broadcastId,
                    broadcastCode = device.broadcastCode
                )
                try {
                    bassController.sendControlPoint(device.address, cpData)
                    Log.i(TAG, "BIS switch command sent successfully to ${device.address}")
                } catch (se: SecurityException) {
                    Log.e(
                        TAG,
                        "SecurityException: Missing BLUETOOTH_CONNECT permission at runtime",
                        se
                    )
                    onMissingPermissions?.invoke(PermissionHelper.getRequiredPermissions())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch BIS for ${device.address}", e)
            }
        }
    }

    /**
     * Overload for convenience when selecting a single BIS index.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        selectBisChannel(device, listOf(bisIndex))
    }

    /**
     * Callback for notifying UI when permissions are missing.
     */
    var onMissingPermissions: ((Array<String>) -> Unit)? = null

    companion object {
        private const val TAG = "BisSelectionManager"
    }
}
