package com.android.broadcastassistant.data

/**
 * Represents a single Broadcast Isochronous Stream (BIS) channel
 * offered by an Auracast broadcaster.
 *
 * Each BIS corresponds to an independent audio stream within the broadcast.
 * Examples:
 * - Different languages (English, Hindi, etc.)
 * - Different roles (Main program, Commentary, Effects)
 * - Different audio allocations (Stereo L/R, Mono)
 *
 * @property index BIS index within the broadcaster (1-based).
 * @property language Spoken language (e.g., "English", "French").
 * @property audioRole Optional role of this stream (e.g., "Main", "Commentary", "Effects").
 * @property streamConfig Optional codec/stream configuration (e.g., "48kHz", "Stereo L").
 */
data class BisChannel(
    val index: Int,
    val language: String,
    val audioRole: String? = null,
    val streamConfig: String? = null
) {
    /**
     * Provides a compact debug-friendly string representation of the BIS channel.
     *
     * Example output:
     * ```
     * BIS[1]: English (role=Main, config=48kHz)
     * ```
     */
    fun toDebugString(): String {
        val rolePart = audioRole?.let { "role=$it" } ?: "role=Unknown"
        val configPart = streamConfig?.let { "config=$it" } ?: "config=Unknown"
        return "BIS[$index]: $language ($rolePart, $configPart)"
    }
}
