package com.android.broadcastassistant.viewmodel

import com.android.broadcastassistant.R
import android.app.Application
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.broadcastassistant.ble.AuracastEaScanner
import com.android.broadcastassistant.audio.BassController
import com.android.broadcastassistant.ble.BisSelectionManager
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logv
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.util.loge
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.graphics.Color

/**
 * ViewModel for Auracast device scanning, BIS selection, and UI state updates.
 *
 * Responsibilities:
 * - Manage BLE scanning (real devices) and fake broadcasters (emulator).
 * - Maintain discovered devices and scanning status flows.
 * - Handle BIS channel selection via [BisSelectionManager].
 * - Provide UI-friendly status messages and colors.
 *
 * Usage:
 * - Call [startScan], [stopScan], or [toggleScan] from the UI.
 * - Observe [devices], [isScanning], [statusMessage], [statusColor] for UI updates.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    /** Flow holding currently discovered Auracast devices */
    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    /** Flow holding current scanning status */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** Flow holding BLE permission granted state */
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    /** Flow holding UI status message */
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    /** Flow holding UI status color */
    private val _statusColor = MutableStateFlow(Color.Black)
    val statusColor: StateFlow<Color> = _statusColor

    // Core Auracast components
    private val bassController = BassController(getApplication<Application>().applicationContext)
    private val bisSelectionManager = BisSelectionManager(
        onDeviceUpdate = { updatedDevices -> _devices.value = updatedDevices },
        getCurrentDevices = { _devices.value },
        bassController = bassController
    )

    // Scanner for real devices
    private val periodicScanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    // Fake broadcaster generator for emulator
    private val fakeSource = FakeAuracastBroadcasterSource(_devices)

    init {
        // Initialize scanner or fake source based on environment
        viewModelScope.launch {
            try {
                if (isRunningOnEmulator()) {
                    logi(TAG, "Running on emulator → using FakeAuracastBroadcasterSource")
                    fakeSource.startFake()
                } else {
                    logi(TAG, "Running on real device → using AuracastEaScanner")
                    // Collect live scan results from Auracast scanner
                    periodicScanner.broadcasters.collectLatest { scannedDevices ->
                        logv(TAG, "Scanned devices updated: count=${scannedDevices.size}")
                        _devices.value = scannedDevices
                    }
                }
            } catch (e: Exception) {
                loge(TAG, "Error initializing scanner in ViewModel", e)
            }
        }
    }

    /**
     * Updates BLE permission state.
     *
     * @param granted True if BLE permissions granted, false otherwise.
     */
    fun updatePermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        if (!granted) {
            logw(TAG, "Permissions revoked → stopping scan")
            stopScan()
        }
    }

    /**
     * Starts scanning for Auracast devices.
     * Uses fake devices if running on emulator.
     */
    fun startScan() {
        try {
            logd(TAG, "startScan() called")

            if (isRunningOnEmulator()) {
                fakeSource.startFake()
                _isScanning.value = true
                _statusMessage.value = getApplication<Application>().getString(R.string.scan_started_fake)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_scanning))
                return
            }

            // Require BLE permissions
            if (!_permissionsGranted.value) {
                _statusMessage.value = getApplication<Application>().getString(R.string.scan_permissions_required)
                return
            }

            if (_isScanning.value) return

            // Update UI and start real scanner
            _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
            _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_scanning))
            periodicScanner.startScanningAuracastEa()
            _isScanning.value = true
        } catch (e: Exception) {
            loge(TAG, "Failed to start scan", e)
        }
    }

    /**
     * Stops ongoing scanning (real or fake).
     */
    fun stopScan() {
        try {
            if (!_isScanning.value) return

            if (isRunningOnEmulator()) {
                fakeSource.stopFake()
                _isScanning.value = false
                val count = _devices.value.size
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.scan_stopped_fake, count)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_idle))
                return
            }

            periodicScanner.stopScanningAuracastEa()
            _isScanning.value = false
            val count = _devices.value.size
            _statusMessage.value = getApplication<Application>()
                .getString(R.string.scan_stopped_real, count)
            _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_idle))
        } catch (e: Exception) {
            loge(TAG, "Failed to stop scan", e)
        }
    }

    /**
     * Toggles scanning state (start if stopped, stop if running).
     */
    fun toggleScan() {
        logd(TAG, "toggleScan() called")
        try {
            if (_isScanning.value) stopScan() else startScan()
        } catch (e: Exception) {
            loge(TAG, "Error toggling scan", e)
        }
    }

    /**
     * Selects a BIS channel on a given Auracast device.
     *
     * @param device Target Auracast device.
     * @param bisIndex Index of the BIS channel to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        viewModelScope.launch {
            try {
                val bis = device.bisChannels.find { it.index == bisIndex }
                val lang = bis?.language ?: "Unknown"

                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.switching_language, lang)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_switching))

                withTimeout(5000) {
                    bisSelectionManager.selectBisChannel(device, bisIndex)
                }

                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.connected_language, lang)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_success))

            } catch (_: TimeoutCancellationException) {
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.switch_timeout, bisIndex.toString())
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_error))

            } catch (e: Exception) {
                loge(TAG, "BIS channel selection failed", e)
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.switch_failed)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_error))
            }
        }
    }

    /**
     * Detects if the app is running on an emulator.
     *
     * @return True if running on emulator, false otherwise.
     */
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    override fun onCleared() {
        super.onCleared()
        // Stop fake broadcasters when ViewModel is destroyed
        try {
            fakeSource.stopFake()
        } catch (e: Exception) {
            loge(TAG, "Error stopping fake source on ViewModel cleared", e)
        }
    }

    companion object {
        private const val TAG = "AuracastViewModel"
    }
}
