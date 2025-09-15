package com.android.broadcastassistant.delegator

import com.android.broadcastassistant.util.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Broadcast Receive State (BRS) characteristic from a Scan Delegator.
 *
 * Responsibilities:
 * - Parse the raw BRS byte array received over BLE.
 * - Extract sourceId, broadcastId, and BIS synchronization bitmap.
 * - Return structured [BassSourceInfo] list.
 */
object BassBroadcastStateParser {

    /**
     * Represents a Broadcast Source info from the BRS characteristic.
     *
     * @property sourceId The identifier of the broadcast source.
     * @property broadcastId The 24-bit broadcast ID.
     * @property bisSync List of active BIS indexes for this source.
     */
    data class BassSourceInfo(
        val sourceId: Int,
        val broadcastId: Int,
        val bisSync: List<Int>
    )

    /**
     * Parses a raw Broadcast Receive State byte array into a list of [BassSourceInfo].
     *
     * @param value Raw byte array from BRS characteristic.
     * @return List of parsed [BassSourceInfo]. Returns empty list if parsing fails.
     */
    fun parse(value: ByteArray): List<BassSourceInfo> {
        val sources = mutableListOf<BassSourceInfo>()

        try {
            val buf = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN)

            // Each entry requires at least 5 bytes: sourceId (1) + broadcastId (3) + BIS bitmap (1)
            while (buf.remaining() >= 5) {
                val sourceId = buf.get().toInt() and 0xFF // Source ID (1 byte)
                val b0 = buf.get().toInt() and 0xFF       // Broadcast ID LSB
                val b1 = buf.get().toInt() and 0xFF
                val b2 = buf.get().toInt() and 0xFF       // Broadcast ID MSB

                // Combine bytes into 24-bit broadcast ID (little-endian)
                val broadcastId = (b2 shl 16) or (b1 shl 8) or b0

                val bisBitmap = buf.get().toInt() and 0xFF // 8-bit bitmap of BIS
                // Convert bitmap into list of active BIS indexes (1..8)
                val bisList = (1..8).filter { (bisBitmap and (1 shl (it - 1))) != 0 }

                sources.add(BassSourceInfo(sourceId, broadcastId, bisList))
                logd("Parsed BroadcastReceiveState → sourceId=$sourceId, broadcastId=0x${broadcastId.toString(16)}, bis=$bisList")
            }
        } catch (e: Exception) {
            // Log parsing error but continue; return whatever was parsed
            loge("BassBroadcastStateParser: Failed to parse BRS characteristic", e)
        }

        return sources
    }
}
