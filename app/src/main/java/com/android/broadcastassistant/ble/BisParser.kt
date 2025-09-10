package com.android.broadcastassistant.ble

import android.os.ParcelUuid
import java.nio.charset.StandardCharsets
import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.*
import java.nio.*

/**
 * Parser for Auracast BIS (Broadcast Isochronous Stream) service data.
 *
 * Responsibilities:
 * - Extracts Broadcast ID and BIS metadata from raw service data bytes.
 * - Interprets subgroup structures including codec config and metadata.
 * - Builds [BisChannel] objects with language, role, and stream config if available.
 *
 * This parser is intentionally tolerant of malformed or truncated data,
 * as real-world Auracast advertisements may vary.
 */
object BisParser {

    /** UUID for Auracast Broadcast Audio service (16-bit: 0x1852). */
    private val AURACAST_UUID: ParcelUuid = UuidUtils.AURACAST_SERVICE_UUID
    /**
     * Parsed BIS data result.
     *
     * @property broadcastId The 24-bit Broadcast Identifier (nullable if parsing fails).
     * @property bisChannels List of [BisChannel] objects parsed from the packet.
     */
    data class ParsedData(
        val broadcastId: Int?,
        val bisChannels: List<BisChannel>
    )

    /** @return Auracast Broadcast Audio Service UUID. */
    fun getAuracastUuid(): ParcelUuid = AURACAST_UUID

    /**
     * Parse a raw Auracast EA (Extended Advertising) service data payload.
     *
     * @param serviceData Raw byte array containing Auracast service data.
     * @return [ParsedData] containing broadcast ID and BIS channel metadata.
     */
    fun parse(serviceData: ByteArray?): ParsedData {
        if (serviceData == null || serviceData.size < 4) {
            logw("parse: Service data is null or too short (len=${serviceData?.size})")
            return ParsedData(null, emptyList())
        }

        val bisChannels = mutableListOf<BisChannel>()
        return try {
            val buf = ByteBuffer.wrap(serviceData).order(ByteOrder.LITTLE_ENDIAN)

            // Broadcast_ID is 3 bytes (little-endian)
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() and 0xFF
            val broadcastId = (b2 shl 16) or (b1 shl 8) or b0
            logd("parse: BroadcastId=0x${broadcastId.toString(16)}")

            if (!buf.hasRemaining()) {
                logw("parse: No data after broadcastId")
                return ParsedData(broadcastId, emptyList())
            }

            buf.get() // PAwR interval (ignored for now)
            if (!buf.hasRemaining()) {
                logw("parse: No data after PAwR interval")
                return ParsedData(broadcastId, emptyList())
            }

            val numSubgroups = buf.get().toInt() and 0xFF
            logd("parse: Subgroups=$numSubgroups")

            for (i in 0 until numSubgroups) {
                if (!buf.hasRemaining()) {
                    logw("parse: Buffer ended unexpectedly before subgroup $i")
                    break
                }

                // Read BIS bitfield (1 byte → up to 8 BIS channels)
                val bisBitfield = buf.get().toInt() and 0xFF
                val bisIndexes = (1..8).filter { bit -> (bisBitfield and (1 shl (bit - 1))) != 0 }
                logv("parse: Subgroup $i → BIS bitfield=0x${bisBitfield.toString(16)}, indexes=$bisIndexes")

                // Skip codec ID (5 bytes)
                if (buf.remaining() < 5) {
                    logw("parse: Not enough bytes to skip codec id in subgroup $i")
                    break
                }
                buf.position(buf.position() + 5)

                // Codec config
                if (!buf.hasRemaining()) {
                    logw("parse: Buffer ended after codec id in subgroup $i")
                    break
                }
                val codecConfigLen = buf.get().toInt() and 0xFF
                if (buf.remaining() < codecConfigLen) {
                    logw("parse: Not enough bytes for codec config in subgroup $i")
                    break
                }
                buf.position(buf.position() + codecConfigLen)

                // Metadata
                if (!buf.hasRemaining()) {
                    logw("parse: Buffer ended before metadata length in subgroup $i")
                    break
                }
                val metaLen = buf.get().toInt() and 0xFF
                if (buf.remaining() < metaLen) {
                    logw("parse: Not enough bytes for metadata in subgroup $i")
                    break
                }
                val metaBytes = ByteArray(metaLen)
                buf.get(metaBytes)
                val metaStr = String(metaBytes, StandardCharsets.UTF_8)
                logv("parse: Metadata subgroup $i = \"$metaStr\"")

                // Extract metadata fields
                val lang = Regex("lang=([a-z]{2,3})")
                    .find(metaStr)?.groupValues?.get(1)
                    ?.replaceFirstChar { it.uppercase() } ?: "Unknown"

                val audioRole = Regex("audio_role=([a-zA-Z0-9_\\- ]+)")
                    .find(metaStr)?.groupValues?.get(1)

                val streamConfig = Regex("stream_config=([a-zA-Z0-9_\\- ]+)")
                    .find(metaStr)?.groupValues?.get(1)

                // Add BIS channels for this subgroup
                bisIndexes.forEach { idx ->
                    val bis = BisChannel(
                        index = idx,
                        language = lang,
                        audioRole = audioRole,
                        streamConfig = streamConfig,
                    )
                    bisChannels.add(bis)
                    logd("parse: Added BIS → ${bis.toDebugString()}")
                }
            }

            logd("parse: Parsed ${bisChannels.size} BIS channels total")
            ParsedData(broadcastId, bisChannels)
        } catch (t: Throwable) {
            loge("parse: Exception during parsing", t)
            ParsedData(null, emptyList())
        }
    }
}
