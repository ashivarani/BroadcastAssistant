package com.android.broadcastassistant.data

/**
 * Represents a BASS (Broadcast Audio Scan Service) Control Point command
 * to be sent to a Scan Delegator device over BLE.
 *
 * This class encapsulates all data needed to write a command:
 * - [deviceAddress]: The Bluetooth MAC address of the target Scan Delegator.
 * - [controlPointData]: The raw byte array for the Control Point command.
 * - [autoDisconnect]: Whether to automatically disconnect after writing (default: true).
 *
 * Notes:
 * - Equality and hashCode are overridden to correctly handle [controlPointData] byte array comparisons.
 * - This object can be safely used in collections or maps.
 */
data class BassCommand(
    val deviceAddress: String,
    val controlPointData: ByteArray,
    val autoDisconnect: Boolean = true
) {

    /**
     * Custom equality check that compares content of [controlPointData] instead of references.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BassCommand

        if (autoDisconnect != other.autoDisconnect) return false
        if (deviceAddress != other.deviceAddress) return false
        if (!controlPointData.contentEquals(other.controlPointData)) return false

        // "BassCommand.equals: Compared $deviceAddress → result=true"
        return true
    }

    /**
     * Custom hashCode calculation to include content of [controlPointData].
     */
    override fun hashCode(): Int {
        var result = autoDisconnect.hashCode()
        result = 31 * result + deviceAddress.hashCode()
        result = 31 * result + controlPointData.contentHashCode()

        // BassCommand.hashCode: Calculated hash for $deviceAddress → $result"
        return result
    }
    /**
     * Debug-friendly string representation.
     */
    override fun toString(): String {
        return "BassCommand(deviceAddress='$deviceAddress', " +
                "controlPointData=[${controlPointData.size} bytes], " +
                "autoDisconnect=$autoDisconnect)"
    }
}
