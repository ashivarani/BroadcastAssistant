package com.android.broadcastassistant.ble

import android.bluetooth.le.ScanRecord
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logw
import androidx.core.util.isEmpty
import androidx.core.util.size

/**
 * Helper object for parsing BIS (Broadcast Isochronous Stream) data
 * from Auracast Extended Advertising packets.
 *
 * Responsibilities:
 * - Parse service data and manufacturer-specific BIS data.
 * - Merge BIS channels to avoid duplicates.
 * - Safely handle parsing errors and log issues.
 */
object BisParserHelper {

    /**
     * Parses raw service data bytes into a [AuracastDevice] with BIS channels.
     *
     * @param address Bluetooth MAC address of the device.
     * @param name Name of the device.
     * @param rssi Signal strength of the advertisement.
     * @param data Raw service data bytes containing BIS info.
     * @param existingDevice Optional existing device to merge BIS channels with.
     * @return A new or updated [AuracastDevice], or null if parsing fails.
     */
    fun parseBisData(
        address: String,
        name: String,
        rssi: Int,
        data: ByteArray,
        existingDevice: AuracastDevice? = null
    ): AuracastDevice? {
        return try {
            val bisInfo = BisParser.parse(data)
            val mergedChannels = mergeBisChannelLists(
                existingDevice?.bisChannels.orEmpty(),
                bisInfo.bisChannels
            )
            val device = AuracastDevice(
                name = name,
                address = address,
                rssi = rssi,
                bisChannels = mergedChannels,
                broadcastId = bisInfo.broadcastId ?: existingDevice?.broadcastId,
                broadcastCode = existingDevice?.broadcastCode
            )
            logd("BisParserHelper: Parsed BIS data for $address, channels=${mergedChannels.size}")
            device
        } catch (e: Exception) {
            loge("BisParserHelper: Failed to parse BIS data for $address", e)
            null
        }
    }

    /**
     * Parses manufacturer-specific BIS data from a [ScanRecord] and merges it
     * with an existing [AuracastDevice].
     *
     * @param record ScanRecord containing manufacturer data.
     * @param address Bluetooth MAC address of the device.
     * @param name Name of the device.
     * @param rssi Signal strength of the advertisement.
     * @param existingDevice Existing [AuracastDevice] to merge BIS data into.
     * @return Updated [AuracastDevice] with merged BIS channels, or null if none found.
     */
    fun parseManufacturerBisData(
        record: ScanRecord,
        address: String,
        name: String,
        rssi: Int,
        existingDevice: AuracastDevice?
    ): AuracastDevice? {
        return try {
            val manufacturerData = record.manufacturerSpecificData
            if (manufacturerData.isEmpty()) return existingDevice

            var updatedDevice = existingDevice
            for (i in 0 until manufacturerData.size) {
                val rawData = manufacturerData.valueAt(i) ?: continue
                try {
                    parseBisData(address, name, rssi, rawData, updatedDevice)?.let { updatedDevice = it }
                } catch (e: Exception) {
                    logw("BisParserHelper: Failed to parse manufacturer BIS data for $address at index $i", e)
                }
            }
            updatedDevice
        } catch (e: Exception) {
            loge("BisParserHelper: Failed to parse manufacturer BIS data for $address", e)
            existingDevice
        }
    }

    /**
     * Merges two lists of [BisChannel], avoiding duplicates and sorting by index.
     *
     * @param oldList Existing list of BIS channels.
     * @param newList New list of BIS channels to merge.
     * @return Merged, sorted list of BIS channels.
     */
    private fun mergeBisChannelLists(
        oldList: List<BisChannel>,
        newList: List<BisChannel>
    ): List<BisChannel> {
        return try {
            val map = oldList.associateBy { it.index }.toMutableMap()
            for (ch in newList) map[ch.index] = ch
            val merged = map.values.sortedBy { it.index }
            logd("BisParserHelper: Merged BIS channels, total=${merged.size}")
            merged
        } catch (e: Exception) {
            loge("BisParserHelper: Failed to merge BIS channels", e)
            oldList // fallback to old list
        }
    }
}
