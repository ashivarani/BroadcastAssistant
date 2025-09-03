package com.android.broadcastassistant.data

/**
 * Represents a discovered Auracast broadcaster device.
 *
 * Each device may advertise one or more BIS (Broadcast Isochronous Stream) channels.
 * This class holds both the discovery metadata (name, address, RSSI) and parsed BIS details.
 *
 * Equality is defined based on all fields including [bisChannels] and [broadcastCode].
 *
 * @property name Human-readable name (e.g., venue, source).
 * @property address BLE MAC address.
 * @property rssi Signal strength of the scan result.
 * @property bisChannels List of available BIS channels on this device.
 * @property selectedBisIndex Index of the currently selected BIS channel (-1 if none).
 * @property broadcastId Optional 24-bit broadcast identifier (unique per transmitter).
 * @property broadcastCode Optional raw bytes for encrypted broadcasts.
 * @property sourceId ID assigned by the Broadcast Receive State characteristic (needed for BASS control).
 */
data class AuracastDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bisChannels: List<BisChannel> = emptyList(),
    val selectedBisIndex: Int = -1,
    val broadcastId: Int? = null,
    val broadcastCode: ByteArray? = null,
    val sourceId: Int? = null, // 🔹 needed for proper BASS commands
) {

    /**
     * Equality check for [AuracastDevice].
     *
     * - Devices are considered equal if all fields match.
     * - Special handling is required for [broadcastCode] (compared by content, not reference).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuracastDevice

        // Compare scalar fields
        if (rssi != other.rssi) return false
        if (selectedBisIndex != other.selectedBisIndex) return false
        if (broadcastId != other.broadcastId) return false
        if (sourceId != other.sourceId) return false
        if (name != other.name) return false
        if (address != other.address) return false

        // Compare list of BIS channels
        if (bisChannels != other.bisChannels) return false

        // Compare broadcast code safely by content
        if (!broadcastCode.contentEquals(other.broadcastCode)) return false

        return true
    }

    /**
     * Hashcode generator for [AuracastDevice].
     *
     * Ensures that if [equals] matches two objects, they also share the same hashcode.
     * Special handling is required for [broadcastCode] (hash by content).
     */
    override fun hashCode(): Int {
        var result = rssi
        result = 31 * result + selectedBisIndex
        result = 31 * result + (broadcastId ?: 0)
        result = 31 * result + (sourceId ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + bisChannels.hashCode()
        result = 31 * result + (broadcastCode?.contentHashCode() ?: 0)
        return result
    }
}
