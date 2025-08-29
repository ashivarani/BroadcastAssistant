package com.android.broadcastassistant.data

/**
 * Represents a single Broadcast Isochronous Stream (BIS) channel
 * offered by an Auracast broadcaster.
 *
 * @param index BIS index within the broadcaster.
 * @param language Spoken language (e.g., "English", "French").
 * @param audioRole Optional role (e.g., "Main", "Commentary", "Effects").
 * @param streamConfig Optional config metadata (codec, allocation).
 */
data class BisChannel(
    val index: Int,
    val language: String,
    val audioRole: String? = null,
    val streamConfig: String? = null
)
