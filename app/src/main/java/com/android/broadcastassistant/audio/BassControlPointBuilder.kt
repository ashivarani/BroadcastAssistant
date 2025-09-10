package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.*

/**
 * Utility object for building BASS (Broadcast Audio Scan Service)
 * Control Point commands.
 *
 * Responsibilities:
 * - Construct byte arrays that conform to the BASS Control Point specification.
 * - Provide helper methods for different Control Point opcodes.
 *
 * Currently supports:
 * - Building a Modify Source (Switch BIS) command.
 */
object BassControlPointBuilder {

    /**
     * Builds a **Modify Source (Switch BIS)** command for the BASS Control Point.
     *
     * This command is used to switch BIS channels on an already configured
     * broadcast source in a Scan Delegator.
     *
     * @param sourceId The source ID assigned by the BASS server (default = 1).
     * @param bisChannels List of BIS channels to enable (1-based values).
     * @param broadcastId The 24-bit broadcast identifier.
     * @param broadcastCode Optional broadcast code for encrypted streams (default = empty).
     * @return A [ByteArray] formatted according to BASS Control Point specification.
     */
    fun buildSwitchCommand(
        sourceId: Int = 1,
        bisChannels: List<BisChannel>,
        broadcastId: Int,
        broadcastCode: ByteArray? = null
    ): ByteArray {
        return try {
            logd(
                "buildSwitchCommand: Start | sourceId=$sourceId, " +
                        "broadcastId=$broadcastId, bisCount=${bisChannels.size}"
            )

            // Log BIS details using toDebugString()
            bisChannels.forEach { channel ->
                logv("buildSwitchCommand: Using BIS → ${channel.toDebugString()}")
            }

            val opcode: Byte = 0x03 // 0x03 = Modify Source command

            // Convert BIS indexes into a bitmap
            val bisIndexes = bisChannels.map { it.index }
            val bisBitmap = bisIndexes.fold(0) { acc, idx -> acc or (1 shl (idx - 1)) }
            logv("buildSwitchCommand: Computed BIS bitmap=0x${bisBitmap.toString(16)}")

            // Encode broadcastId (24-bit LE)
            val bc0 = (broadcastId and 0xFF).toByte()
            val bc1 = ((broadcastId shr 8) and 0xFF).toByte()
            val bc2 = ((broadcastId shr 16) and 0xFF).toByte()
            logv("buildSwitchCommand: Encoded broadcastId=[$bc0,$bc1,$bc2]")

            // Add broadcast code if present
            val code = broadcastCode ?: byteArrayOf()
            logv("buildSwitchCommand: Broadcast code length=${code.size}")

            // Build command
            val out = ArrayList<Byte>()
            out.add(opcode)                                // Opcode
            out.add(sourceId.toByte())                     // Source ID
            out.add((bisBitmap and 0xFF).toByte())         // BIS bitmap LSB
            out.add(((bisBitmap shr 8) and 0xFF).toByte()) // BIS bitmap MSB
            out.add(bc0)                                   // BroadcastId[0]
            out.add(bc1)                                   // BroadcastId[1]
            out.add(bc2)                                   // BroadcastId[2]
            out.add(code.size.toByte())                    // Broadcast code length
            code.forEach { out.add(it) }                   // Append code bytes if any

            val result = out.toByteArray()
            logd("buildSwitchCommand: Success | totalLength=${result.size}")
            result
        } catch (e: Exception) {
            loge("buildSwitchCommand: Failed to build command", e)
            byteArrayOf()
        }
    }
}
