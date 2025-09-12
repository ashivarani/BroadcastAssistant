package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.*
import kotlin.random.Random

/**
 * Generates fake Auracast broadcasters and BIS channels for Compose previews
 * or testing in environments where BLE scanning is not available.
 *
 * All steps are logged using the centralized logging utility.
 * No scanning or runtime updates occur; purely for UI preview/testing.
 */
class PreviewAuracastDevice {

    companion object {

        /**
         * Generates a static list of fake Auracast devices.
         *
         * Each device will contain:
         * - Stereo broadcaster (Left + Right channels)
         * - Multilingual broadcaster (3 different language BIS channels)
         * - One randomized broadcaster for extra variation
         *
         * This ensures the preview UI always has both single-BIS and multi-BIS cases.
         *
         * @return List of fake [AuracastDevice] objects.
         */
        fun fakeBroadcasters(): List<AuracastDevice> {
            return try {
                logd("fakeBroadcasters: Entered")

                val languages = listOf("English", "Telugu", "Kannada", "Tamil", "Hindi")
                val audioRoles = listOf("Main", "Commentary", "Effects")
                val streamConfigs = listOf("48kHz", "44.1kHz", "32kHz", "24kHz", "16kHz")

                val devices = listOf(

                    // Device 1: Stereo (always has Left + Right channels)
                    AuracastDevice(
                        name = "Broadcaster 1 (Stereo)",
                        address = "00:11:22:33:44:01",
                        rssi = Random.nextInt(-80, -40),
                        bisChannels = listOf(
                            BisChannel(0, "English", "Left", "48kHz"),
                            BisChannel(1, "English", "Right", "48kHz")
                        ),
                        broadcastId = 1234,
                        broadcastCode = null,
                        selectedBisIndexes = emptyList()
                    ).also { logv("fakeBroadcasters: Created device 1 -> $it") },

                    // Device 2: Multilingual (3 BIS channels)
                    AuracastDevice(
                        name = "Broadcaster 2 (Multilingual)",
                        address = "00:11:22:33:44:02",
                        rssi = Random.nextInt(-85, -35),
                        bisChannels = listOf(
                            BisChannel(0, "English", "Main", "44.1kHz"),
                            BisChannel(1, "Telugu", "Main", "44.1kHz"),
                            BisChannel(2, "Hindi", "Commentary", "44.1kHz")
                        ),
                        broadcastId = 5678,
                        broadcastCode = null,
                        selectedBisIndexes = emptyList()
                    ).also { logv("fakeBroadcasters: Created device 2 -> $it") },

                    // Device 3: Randomized channels (2–3 BIS channels)
                    AuracastDevice(
                        name = "Broadcaster 3 (Random)",
                        address = "00:11:22:33:44:03",
                        rssi = Random.nextInt(-90, -30),
                        bisChannels = (languages.shuffled().take(Random.nextInt(2, 4))).mapIndexed { i, lang ->
                            BisChannel(
                                index = i,
                                language = lang,
                                audioRole = audioRoles.random(),
                                streamConfig = streamConfigs.random()
                            )
                        },
                        broadcastId = 9012,
                        broadcastCode = null,
                        selectedBisIndexes = emptyList()
                    ).also { logv("fakeBroadcasters: Created device 3 -> $it") }
                )

                logi("fakeBroadcasters: Successfully generated ${devices.size} fake devices")
                devices

            } catch (e: Exception) {
                loge("fakeBroadcasters: Failed to generate devices", e)
                emptyList()
            }
        }
    }
}
