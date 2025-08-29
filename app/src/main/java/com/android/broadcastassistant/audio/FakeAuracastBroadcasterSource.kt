package com.android.broadcastassistant.audio

import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random

class FakeAuracastBroadcasterSource(
    private val broadcastersFlow: MutableStateFlow<List<AuracastDevice>>,
    private val intervalMs: Long = 3000L
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun startFake() {
        if (job?.isActive == true) return // already running

        job = scope.launch {
            while (isActive) {
                broadcastersFlow.value = generateFakeDevices()
                delay(intervalMs)
            }
        }
    }

    fun stopFake() {
        job?.cancel()
        job = null
    }

    private fun generateFakeDevices(): List<AuracastDevice> {
        val languages = listOf(
            "en" to "English",
            "te" to "Telugu",
            "kn" to "Kannada",
            "ta" to "Tamil",
            "hi" to "Hindi"
        )

        return (1..3).map { index ->
            val selectedLangs = languages.shuffled().take(Random.nextInt(2, 4))
            val bisChannels = selectedLangs.mapIndexed { i, (_, label) ->
                BisChannel(
                    index = i,
                    language = label,
                    audioRole = if (i % 2 == 0) "Stereo L" else "Stereo R",
                    streamConfig = "48kHz"
                )
            }

            AuracastDevice(
                name = "Fake Speaker $index",
                address = "00:11:22:33:44:${index.toString().padStart(2, '0')}",
                rssi = Random.nextInt(-90, -30),
                bisChannels = bisChannels,
                broadcastId = Random.nextInt(1000, 9999),
                broadcastCode = null
            )
        }
    }

    companion object {
        /** ✅ Static helper so Previews can inject fake devices */
        fun fakeBroadcasters(): List<AuracastDevice> {
            return FakeAuracastBroadcasterSource(MutableStateFlow(emptyList()))
                .generateFakeDevices()
        }
    }
}
