package com.android.broadcastassistant.ble

import android.os.ParcelUuid
import com.android.broadcastassistant.data.BisChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Parse minimal BAA / BASE service-data bytes for Broadcast_ID + BIS metadata.
 * This parser is intentionally forgiving (real packets can vary); adjust for your transmitter format.
 */
object BisParser {
    private val AURACAST_UUID = ParcelUuid.fromString("00001852-0000-1000-8000-00805f9b34fb")

    data class ParsedData(
        val broadcastId: Int?,
        val bisChannels: List<BisChannel>
    )

    fun getAuracastUuid(): ParcelUuid = AURACAST_UUID

    fun parse(serviceData: ByteArray?): ParsedData {
        if (serviceData == null || serviceData.size < 4) return ParsedData(null, emptyList())

        val bisChannels = mutableListOf<BisChannel>()
        try {
            val buf = ByteBuffer.wrap(serviceData).order(ByteOrder.LITTLE_ENDIAN)

            // Broadcast_ID is 3 bytes (little-endian)
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() and 0xFF
            val broadcastId = (b2 shl 16) or (b1 shl 8) or b0

            if (!buf.hasRemaining()) return ParsedData(broadcastId, emptyList())

            buf.get() // PAwR interval (unused here)
            if (!buf.hasRemaining()) return ParsedData(broadcastId, emptyList())

            val numSubgroups = buf.get().toInt() and 0xFF

            for (i in 0 until numSubgroups) {
                if (!buf.hasRemaining()) {
                    // Log warning or handle incomplete subgroup data
                    println("Buffer ended unexpectedly before parsing subgroup $i")
                    break
                }
                println("Parsing subgroup #$i")

                // Read BIS bitfield (1 byte for up to 8 BIS)
                val bisBitfield = buf.get().toInt() and 0xFF
                val bisIndexes = (1..8).filter { bit -> (bisBitfield and (1 shl (bit - 1))) != 0 }

                // Skip codec id (5 bytes)
                if (buf.remaining() < 5) {
                    println("Not enough bytes to skip codec id in subgroup $i")
                    break
                }
                buf.position(buf.position() + 5)

                if (!buf.hasRemaining()) {
                    println("Buffer ended after codec id in subgroup $i")
                    break
                }
                val codecConfigLen = buf.get().toInt() and 0xFF
                if (buf.remaining() < codecConfigLen) {
                    println("Not enough bytes for codec config in subgroup $i")
                    break
                }
                buf.position(buf.position() + codecConfigLen)

                if (!buf.hasRemaining()) {
                    println("Buffer ended before metadata length in subgroup $i")
                    break
                }
                val metaLen = buf.get().toInt() and 0xFF
                if (buf.remaining() < metaLen) {
                    println("Not enough bytes for metadata in subgroup $i")
                    break
                }
                val metaBytes = ByteArray(metaLen)
                buf.get(metaBytes)
                val metaStr = String(metaBytes, StandardCharsets.UTF_8)

                // Extract language tag, fallback "Unknown"
                val lang = Regex("lang=([a-z]{2,3})")
                    .find(metaStr)
                    ?.groupValues
                    ?.get(1)
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "Unknown"

                // Extract audio role if present (e.g., audio_role=Music)
                val audioRole = Regex("audio_role=([a-zA-Z0-9_\\- ]+)")
                    .find(metaStr)
                    ?.groupValues
                    ?.get(1)

                // Extract stream config if present (e.g., stream_config=Stereo)
                val streamConfig = Regex("stream_config=([a-zA-Z0-9_\\- ]+)")
                    .find(metaStr)
                    ?.groupValues
                    ?.get(1)

                bisIndexes.forEach { idx ->
                    bisChannels.add(
                        BisChannel(
                            index = idx,
                            language = lang,
                            audioRole = audioRole,
                            streamConfig = streamConfig,
                        )
                    )
                }
            }

            return ParsedData(broadcastId, bisChannels)
        } catch (t: Throwable) {
            t.printStackTrace()
            return ParsedData(null, emptyList())
        }
    }
}