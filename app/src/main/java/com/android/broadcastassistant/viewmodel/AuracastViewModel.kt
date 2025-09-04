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
 * ViewModel that manages Auracast scanning, BIS selection, and state updates.
 *
 * Responsibilities:
 * - Manage BLE permissions, scanning state, and discovered devices.
 * - Switch between real device scanning and fake sources when running in emulator.
 * - Handle BIS channel selection and route it to the [BisSelectionManager].
 * - Provide status messages and colors for UI binding.
 *
 * Usage:
 * - Call [startScan] and [stopScan] from UI buttons.
 * - Observe [devices], [isScanning], and [statusMessage] for UI updates.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    /** Flow of currently discovered devices */
    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    /** Scanning status helpers */
    private val _statusColor = MutableStateFlow(Color.Black)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** BLE permissions granted state */
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    /** Status message for UI display */
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Core Auracast components
    private val bassController = BassController(getApplication<Application>().applicationContext)
    private val bisSelectionManager = BisSelectionManager(
        onDeviceUpdate = { updatedDevices -> _devices.value = updatedDevices },
        getCurrentDevices = { _devices.value },
        bassController = bassController
    )

    // Scanner (real devices only)
    private val periodicScanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    // Fake broadcaster generator for emulator
    private val fakeSource = FakeAuracastBroadcasterSource(_devices)

    init {
        viewModelScope.launch {
            try {
                if (isRunningOnEmulator()) {
                    logi(TAG, "Running on emulator → using FakeAuracastBroadcasterSource")
                    fakeSource.startFake()
                } else {
                    logi(TAG, "Running on real device → using AuracastEaScanner")
                    // Collect live scan results
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
     * Updates BLE permission state from Activity/PermissionHelper.
     *
     * @param granted true if permissions granted, false otherwise.
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
     * Uses fake sources when running on emulator.
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

            if (!_permissionsGranted.value) {
                _statusMessage.value = getApplication<Application>().getString(R.string.scan_permissions_required)
                return
            }

            if (_isScanning.value) return

            _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
            _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_scanning))
            periodicScanner.startScanningAuracastEa()
            _isScanning.value = true
        } catch (e: Exception) {
            loge(TAG, "Failed to start scan", e)
        }
    }


    /**
     * Stops ongoing scan (fake or real).
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
     * Toggles scanning state between start and stop.
     */
    fun toggleScan() {
        logd(TAG, "toggleScan() called")
        if (_isScanning.value) {
            stopScan()
        } else {
            startScan()
        }
    }

    /**
     * Selects a BIS channel on a given broadcaster.
     *
     * @param device The target Auracast device.
     * @param bisIndex The BIS index to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        viewModelScope.launch {
            val bis = device.bisChannels.find { it.index == bisIndex }
            val lang = bis?.language ?: "Unknown"

            _statusMessage.value = getApplication<Application>()
                .getString(R.string.switching_language, lang)
            _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_switching))

            try {
                withTimeout(5000) {
                    bisSelectionManager.selectBisChannel(device, bisIndex)
                }
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.connected_language, lang)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_success))

            } catch (_: TimeoutCancellationException) {
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.switch_timeout, lang)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_error))

            } catch (_: Exception) {
                _statusMessage.value = getApplication<Application>()
                    .getString(R.string.switch_failed)
                _statusColor.value = Color(ContextCompat.getColor(getApplication(), R.color.status_error))
            }
        }
    }

    /**
     * Detects if the app is running on an emulator.
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
        // Always stop fake source when ViewModel is destroyed
        fakeSource.stopFake()
    }

    companion object {
        private const val TAG = "AuracastViewModel"
    }
}
