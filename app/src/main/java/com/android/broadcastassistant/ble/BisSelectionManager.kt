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
 * Manages BIS (Broadcast Isochronous Stream) channel selection for Auracast devices.
 *
 * Responsibilities:
 * - Safely select one or multiple BIS indexes for a device.
 * - Apply language-based fallback (only one BIS per language is selected).
 * - Send the appropriate BASS Control Point command:
 *   - 0x01 Select BIS for initial join
 *   - 0x03 Modify Source (Switch) for already joined devices
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BisSelectionManager(
    private val bassGattManager: BassGattManager,
) {

    /**
     * Selects BIS channels for a device and sends the corresponding Control Point command.
     *
     * @param device The target [AuracastDevice].
     * @param bisIndexes List of requested BIS indexes to select.
     * @return [BisSelectionResult.Success] if selection succeeded, or [BisSelectionResult.Failure] if an error occurred.
     */
    suspend fun selectBisChannels(
        device: AuracastDevice,
        bisIndexes: List<Int>
    ): BisSelectionResult {
        try {
            // Reject encrypted or private broadcasts (not supported)
            if (device.broadcastCode != null) {
                val reason = "Encrypted/private broadcast not supported: ${device.address}"
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Ensure sourceId is available (populated by ScanDelegatorManager)
            if (device.sourceId == null) {
                val reason = "Missing sourceId for device ${device.address}. Ensure ScanDelegatorManager has provided it."
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Filter BIS channels that match requested indexes
            val selectedChannels = device.bisChannels.filter { it.index in bisIndexes }
            if (selectedChannels.isEmpty()) {
                val reason = "Requested BIS indexes $bisIndexes not found for ${device.address}"
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Apply language-based fallback: only one BIS per language
            val uniqueLanguages = mutableSetOf<String>()
            val channelsToSend = selectedChannels.filter { bis ->
                val lang = bis.language.ifEmpty { "Unknown" }
                if (!uniqueLanguages.contains(lang)) {
                    uniqueLanguages.add(lang)
                    true
                } else false
            }

            if (channelsToSend.isEmpty()) {
                val reason = "No BIS channels available after language fallback for ${device.address}"
                logw(reason)
                return BisSelectionResult.Failure(device, reason)
            }

            // Determine if this is the first join (Select BIS) or a modification (Switch)
            val firstJoin = device.selectedBisIndexes.isEmpty()

            // Build the appropriate BASS Control Point command
            val cpData = try {
                if (firstJoin) {
                    BassControlPointExtensions.buildSelectBisCommand(
                        sourceId = device.sourceId!!,
                        bisChannels = channelsToSend
                    )
                } else {
                    BassControlPointBuilder.buildSwitchCommand(
                        sourceId = device.sourceId!!,
                        bisChannels = channelsToSend,
                        broadcastId = device.broadcastId
                            ?: return BisSelectionResult.Failure(device, "Missing broadcastId")
                    )
                }
            } catch (e: Exception) {
                loge("Failed to build Control Point command for ${device.address}", e)
                return BisSelectionResult.Failure(device, "Failed to build Control Point command")
            }

            // Create the BASS command object
            val command = BassCommand(
                deviceAddress = device.address,
                controlPointData = cpData,
                autoDisconnect = false
            )

            // Retry sending the Control Point command up to 2 times
            repeat(2) { attempt ->
                try {
                    bassGattManager.sendControlPoint(command)
                    // Success: update device selected BIS indexes and return
                    device.selectedBisIndexes = channelsToSend.map { it.index }
                    logi("BIS command succeeded for ${device.address}, selected indexes=${device.selectedBisIndexes}")
                    return BisSelectionResult.Success(device, device.selectedBisIndexes)
                } catch (e: Exception) {
                    logw("BIS command attempt ${attempt + 1} failed for ${device.address}", e)
                }
            }

            // If all retries failed, return failure
            val reason = "Failed to send BIS command after retries for ${device.address}"
            loge(reason)
            return BisSelectionResult.Failure(device, reason)

        } catch (e: Exception) {
            loge("Unexpected error during BIS selection for ${device.address}", e)
            return BisSelectionResult.Failure(device, e.message ?: "Unknown error")
        }
    }
}
