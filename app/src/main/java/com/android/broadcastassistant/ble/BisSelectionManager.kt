package com.android.broadcastassistant.ble

import android.os.Build
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.audio.BassControlPointBuilder
import com.android.broadcastassistant.audio.BassControlPointExtensions
import com.android.broadcastassistant.audio.BassGattManager
import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.util.logi

/**
 * Manages BIS channel selection for Auracast devices.
 *
 * Responsibilities:
 * - Selects one or multiple BIS indexes safely.
 * - Applies language-based fallback (single BIS per language).
 * - Sends 0x01 Select BIS for initial join or 0x03 Modify Source for already joined devices.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val bassGattManager: BassGattManager,
) {

    /**
     * Selects BIS channels for a device.
     *
     * @param device The target [AuracastDevice].
     * @param bisIndexes List of requested BIS indexes to select.
     * @return [BisSelectionResult.Success] on success, or [BisSelectionResult.Failure] on error.
     */
    suspend fun selectBisChannels(
        device: AuracastDevice,
        bisIndexes: List<Int>
    ): BisSelectionResult {
        try {
            // Reject encrypted/private broadcasts
            if (device.broadcastCode != null) {
                val reason = "Encrypted/private broadcast not supported: ${device.address}"
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Filter selected BIS channels
            val selectedChannels = device.bisChannels.filter { it.index in bisIndexes }
            if (selectedChannels.isEmpty()) {
                val reason = "BIS indexes $bisIndexes not found for ${device.address}"
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Always fallback to single BIS per language
            val uniqueLanguages = mutableSetOf<String>()
            val channelsToSend = selectedChannels.filter { bis ->
                val lang = bis.language.ifEmpty { "Unknown" }
                if (!uniqueLanguages.contains(lang)) {
                    uniqueLanguages.add(lang)
                    true
                } else false
            }

            if (channelsToSend.isEmpty()) {
                return BisSelectionResult.Failure(device, "No BIS channels available after fallback")
            }

            // Determine whether this is the first join
            val firstJoin = device.selectedBisIndexes.isEmpty()

            // Build Control Point command
            val cpData = if (firstJoin) {
                BassControlPointExtensions.buildSelectBisCommand(
                    sourceId = device.sourceId
                        ?: return BisSelectionResult.Failure(device, "Missing sourceId"),
                    bisChannels = channelsToSend
                )
            } else {
                BassControlPointBuilder.buildSwitchCommand(
                    sourceId = device.sourceId
                        ?: return BisSelectionResult.Failure(device, "Missing sourceId"),
                    bisChannels = channelsToSend,
                    broadcastId = device.broadcastId
                        ?: return BisSelectionResult.Failure(device, "Missing broadcastId")
                )
            }

            val command = BassCommand(
                deviceAddress = device.address,
                controlPointData = cpData,
                autoDisconnect = false
            )

            // Retry logic: attempt 2 times for transient BLE failures
            repeat(2) { attempt ->
                try {
                    bassGattManager.sendControlPoint(command)
                    // Success: update device and return
                    device.selectedBisIndexes = channelsToSend.map { it.index }
                    logi("BIS command succeeded for ${device.address}, indexes=${device.selectedBisIndexes}")
                    return BisSelectionResult.Success(device, device.selectedBisIndexes)
                } catch (e: Exception) {
                    logw("BIS command attempt ${attempt + 1} failed for ${device.address}", e)
                }
            }

            return BisSelectionResult.Failure(device, "Failed to send BIS command after retries")

        } catch (e: Exception) {
            loge("BIS selection failed for ${device.address}", e)
            return BisSelectionResult.Failure(device, e.message ?: "Unknown error")
        }
    }
}
