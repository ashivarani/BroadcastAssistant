package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.*
import kotlin.random.Random

/**
 * Generates fake Auracast broadcasters and BIS channels for Compose previews
 * or testing in environments where BLE scanning is not available.
 *
 * This is purely for UI preview/testing purposes. No scanning or runtime updates.
 */
class PreviewAuracastDevice {

    companion object {

        /**
         * Generates a static list of fake Auracast devices.
         *
         * Each device will contain:
         * - Randomly selected languages (2–3 per device)
         * - Random audio roles ("Main", "Commentary", "Effects") for each BIS channel
         * - Random stream configuration from a set of supported configs
         * - Random RSSI values (-90 to -30)
         * - Random broadcast ID
         *
         * @return List of fake [AuracastDevice] objects.
         */
        fun fakeBroadcasters(): List<AuracastDevice> {
            return try {
                logd("fakeBroadcasters: Entered")

                // List of possible spoken languages for BIS channels
                val languages = listOf(
                    "English",
                    "Telugu",
                    "Kannada",
                    "Tamil",
                    "Hindi"
                )

                // List of possible audio roles for BIS channels
                val audioRoles = listOf(
                    "Main",
                    "Commentary",
                    "Effects"
                )

                // List of possible stream configurations
                val streamConfigs = listOf(
                    "48kHz",
                    "44.1kHz",
                    "32kHz",
                    "24kHz",
                    "16kHz"
                )

                // Generate 3 fake Auracast devices
                val devices = (1..3).map { index ->

                    // Pick 2–3 random languages per device
                    val selectedLangs = languages.shuffled().take(Random.nextInt(2, 4))

                    // Create BIS channels for each selected language
                    val bisChannels = selectedLangs.mapIndexed { i, lang ->
                        BisChannel(
                            index = i,                        // BIS index (0-based)
                            language = lang,                  // Language of this channel
                            audioRole = audioRoles.random(), // Random audio role
                            streamConfig = streamConfigs.random() // Random stream configuration
                        )
                    }

                    // Create a fake Auracast device
                    AuracastDevice(
                        name = "Broadcaster $index",                          // Device name
                        address = "00:11:22:33:44:${index.toString().padStart(2, '0')}", // MAC-like address
                        rssi = Random.nextInt(-90, -30),                     // Random RSSI
                        bisChannels = bisChannels,                           // List of BIS channels
                        broadcastId = Random.nextInt(1000, 9999),            // Random broadcast ID
                        broadcastCode = null                                  // No broadcast code
                    ).also { logv("fakeBroadcasters: Created $it") }
                }

                logi("fakeBroadcasters: Generated ${devices.size} fake devices successfully")
                devices

            } catch (e: Exception) {
                loge("fakeBroadcasters: Failed to generate devices", e)
                emptyList()
            }
        }
    }
}
