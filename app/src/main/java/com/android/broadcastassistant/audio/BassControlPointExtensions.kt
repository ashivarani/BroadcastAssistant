package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.*

/**
 * Extension helpers for BassControlPointBuilder to build additional commands.
 *
 * All operations log their steps using the centralized logging utility.
 */
object BassControlPointExtensions {

    /**
     * Builds a Select BIS (0x01) command with explicit BIS indexes.
     *
     * @param sourceId The broadcast source ID (default 1)
     * @param bisChannels List of BIS channels to select
     * @return ByteArray representing the command
     */
    fun buildSelectBisCommand(
        sourceId: Int = 1,
        bisChannels: List<BisChannel>
    ): ByteArray {
        return try {
            val opcode: Byte = 0x01
            val bisList = bisChannels.map { it.index.toByte() }

            val out = ArrayList<Byte>().apply {
                add(opcode)
                add(sourceId.toByte())
                add(bisList.size.toByte())
                bisList.forEach { add(it) }
            }

            logd(
                "buildSelectBisCommand called",
                "sourceId=$sourceId",
                "count=${bisList.size}",
                "indexes=$bisList"
            )

            out.toByteArray()
        } catch (e: Exception) {
            loge("Error building Select BIS command for sourceId=$sourceId", e)
            ByteArray(0) // return empty array on error
        }
    }
}
