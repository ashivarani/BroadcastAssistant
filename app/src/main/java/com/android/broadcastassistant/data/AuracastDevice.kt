package com.android.broadcastassistant.data

/**
 * Represents a discovered Auracast broadcaster device.
 *
 * @param name Human-readable name (e.g., venue, source).
 * @param address BLE MAC address.
 * @param rssi Signal strength.
 * @param bisChannels List of available BIS channels on this device.
 * @param selectedBisIndex Index of the currently selected BIS channel (-1 if none).
 * @param broadcastId Optional 24-bit broadcast identifier.
 * @param broadcastCode Optional raw bytes for encrypted broadcasts.
 */
data class AuracastDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val bisChannels: List<BisChannel> = emptyList(),
    val selectedBisIndex: Int = -1,
    val broadcastId: Int? = null,
    val broadcastCode: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AuracastDevice

        if (rssi != other.rssi) return false
        if (selectedBisIndex != other.selectedBisIndex) return false
        if (broadcastId != other.broadcastId) return false
        if (name != other.name) return false
        if (address != other.address) return false
        if (bisChannels != other.bisChannels) return false
        if (!broadcastCode.contentEquals(other.broadcastCode)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rssi
        result = 31 * result + selectedBisIndex
        result = 31 * result + (broadcastId ?: 0)
        result = 31 * result + name.hashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + bisChannels.hashCode()
        result = 31 * result + (broadcastCode?.contentHashCode() ?: 0)
        return result
    }
}
