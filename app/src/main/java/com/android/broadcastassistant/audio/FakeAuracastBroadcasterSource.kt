package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logv
import com.android.broadcastassistant.util.loge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

/**
 * Fake Auracast broadcaster generator for testing in emulator or preview environments.
 *
 * Periodically emits a list of simulated [AuracastDevice]s into [broadcastersFlow].
 * Useful when real BLE scanning is not possible.
 *
 * @param broadcastersFlow StateFlow to update with fake devices.
 * @param intervalMs Interval between updates in milliseconds.
 */
class FakeAuracastBroadcasterSource(
    private val broadcastersFlow: MutableStateFlow<List<AuracastDevice>>,
    private val intervalMs: Long = 3000L
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Starts periodic fake broadcaster emission.
     * No-op if already running.
     */
    fun startFake() {
        try {
            logd("startFake: Entered")

            if (job?.isActive == true) {
                logd("startFake: Already running, skipping restart")
                return
            }

            logi("startFake: Starting fake Auracast broadcaster updates every $intervalMs ms")

            job = scope.launch {
                while (isActive) {
                    val fakeDevices = generateFakeDevices()
                    broadcastersFlow.value = fakeDevices
                    logv("startFake: Emitted ${fakeDevices.size} fake broadcasters")
                    delay(intervalMs)
                }
            }
        } catch (e: Exception) {
            loge("startFake: Failed to start fake broadcaster source", e)
        }
    }

    /**
     * Stops the fake broadcaster emission.
     * Safe to call even if not running.
     */
    fun stopFake() {
        try {
            logd("stopFake: Entered")

            if (job == null) {
                logd("stopFake: No active job, nothing to stop")
                return
            }
            job?.cancel()
            job = null
            logi("stopFake: Fake broadcaster emission stopped")
        } catch (e: Exception) {
            loge("stopFake: Failed to stop fake broadcaster source", e)
        }
    }

    /**
     * Generates a random list of fake [AuracastDevice]s with random RSSI,
     * broadcastId, and BIS channel info.
     */
    private fun generateFakeDevices(): List<AuracastDevice> {
        return try {
            logd("generateFakeDevices: Entered")

            val languages = listOf(
                "en" to "English",
                "te" to "Telugu",
                "kn" to "Kannada",
                "ta" to "Tamil",
                "hi" to "Hindi"
            )

            val devices = (1..3).map { index ->
                val selectedLangs = languages.shuffled().take(Random.nextInt(2, 4))
                val bisChannels = selectedLangs.mapIndexed { i, (_, label) ->
                    BisChannel(
                        index = i,
                        language = label,
                        audioRole = if (i % 2 == 0) "Stereo L" else "Stereo R",
                        streamConfig = "48kHz"
                    )
                }

                val device = AuracastDevice(
                    name = "Fake Broadcaster $index",
                    address = "00:11:22:33:44:${index.toString().padStart(2, '0')}",
                    rssi = Random.nextInt(-90, -30),
                    bisChannels = bisChannels,
                    broadcastId = Random.nextInt(1000, 9999),
                    broadcastCode = null
                )
                logv("generateFakeDevices: Created $device")
                device
            }
            logi("generateFakeDevices: Generated ${devices.size} devices successfully")
            devices
        } catch (e: Exception) {
            loge("generateFakeDevices: Failed to generate fake devices", e)
            emptyList()
        }
    }

    companion object {
        /**
         * Static helper to generate a one-off fake broadcaster list,
         * useful for UI Previews or testing without coroutine setup.
         */
        fun fakeBroadcasters(): List<AuracastDevice> {
            return try {
                logd("fakeBroadcasters: Entered")
                FakeAuracastBroadcasterSource(MutableStateFlow(emptyList()))
                    .generateFakeDevices()
                    .also { logi("fakeBroadcasters: Generated ${it.size} fake broadcasters") }
            } catch (e: Exception) {
                loge("fakeBroadcasters: Failed to generate static fake devices", e)
                emptyList()
            }
        }
    }
}
