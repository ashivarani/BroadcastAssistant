package com.android.broadcastassistant.util

import android.os.ParcelUuid
import java.util.UUID

/**
 * Utility functions for handling Bluetooth UUIDs.
 *
 * Provides helpers to work with 16-bit UUIDs and convert them to [UUID] or [ParcelUuid].
 * Centralized place for commonly used LE Audio / Auracast UUIDs.
 */
object UuidUtils {

    /** Base UUID used for converting 16-bit UUIDs to full 128-bit form. */
    private val BASE_UUID: UUID = UUID.fromString("00000000-0000-1000-8000-00805F9B34FB")

    /** Auracast Broadcast Audio Service (BIS) UUID (16-bit: 0x1852). */
    val AURACAST_SERVICE_UUID: ParcelUuid = from16BitParcel(0x1852)

    /** Broadcast Audio Scan Service (BASS) UUID (16-bit: 0x184F). */
    val BASS_SERVICE_UUID: UUID = from16Bit(0x184F)

    /** BASS Control Point Characteristic UUID (16-bit: 0x2B2B). */
    val BASS_CONTROL_POINT_UUID: UUID = from16Bit(0x2B2B)

    /**
     * Convert a 16-bit Bluetooth UUID into a full 128-bit [UUID].
     *
     * @param uuid16 The 16-bit Bluetooth UUID (e.g., 0x1852).
     * @return Full 128-bit [UUID] object.
     */
    fun from16Bit(uuid16: Int): UUID {
        val msb = BASE_UUID.mostSignificantBits or ((uuid16.toLong() and 0xFFFFL) shl 32)
        return UUID(msb, BASE_UUID.leastSignificantBits)
    }

    /**
     * Convert a 16-bit Bluetooth UUID into a [ParcelUuid] for use in BLE scan filters.
     *
     * @param uuid16 The 16-bit Bluetooth UUID.
     * @return Corresponding [ParcelUuid].
     */
    fun from16BitParcel(uuid16: Int): ParcelUuid = ParcelUuid(from16Bit(uuid16))
}
