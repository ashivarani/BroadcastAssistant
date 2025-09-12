package com.android.broadcastassistant.data

/**
 * Represents a discovered Auracast broadcaster device.
 *
 * Supports both single and multi-BIS selection using [selectedBisIndexes].
 *
 * @property name Device name for display.
 * @property address BLE MAC address of the broadcaster.
 * @property rssi Received signal strength indicator.
 * @property bisChannels List of available BIS channels.
 * @property selectedBisIndexes List of currently selected BIS indices (empty = none).
 * @property broadcastId Optional broadcast identifier.
 * @property broadcastCode Optional broadcast encryption code.
 * @property sourceId Optional source identifier (used for control point commands).
 */
data class AuracastDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bisChannels: List<BisChannel> = emptyList(),
    var selectedBisIndexes: List<Int> = emptyList(), // single or multi
    val broadcastId: Int? = null,
    val broadcastCode: ByteArray? = null,
    val sourceId: Int? = null
) {

    /**
     * Checks equality between this device and another object.
     *
     * Custom implementation ensures:
     * - All primary properties (name, address, rssi, broadcastId, sourceId) are compared.
     * - [bisChannels] and [selectedBisIndexes] are compared.
     * - [broadcastCode] is compared by content (byte array equality) instead of reference.
     *
     * @param other The other object to compare with.
     * @return True if all fields are equal, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuracastDevice

        if (rssi != other.rssi) return false
        if (broadcastId != other.broadcastId) return false
        if (sourceId != other.sourceId) return false
        if (name != other.name) return false
        if (address != other.address) return false
        if (bisChannels != other.bisChannels) return false
        if (selectedBisIndexes != other.selectedBisIndexes) return false
        if (!broadcastCode.contentEquals(other.broadcastCode)) return false

        return true
    }

    /**
     * Generates a hash code for this device.
     *
     * Custom implementation ensures:
     * - Combines primary fields, BIS channels, selected BIS indexes, and broadcast code.
     * - [broadcastCode] uses [contentHashCode] to handle byte arrays correctly.
     *
     * @return Int hash code representing the device.
     */
    override fun hashCode(): Int {
        var result = rssi
        result = 31 * result + (broadcastId ?: 0)
        result = 31 * result + (sourceId ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + bisChannels.hashCode()
        result = 31 * result + selectedBisIndexes.hashCode()
        result = 31 * result + (broadcastCode?.contentHashCode() ?: 0)
        return result
    }
}
