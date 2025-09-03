package com.android.broadcastassistant.viewmodel

import android.app.Application
import android.graphics.Color
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
    private val _statusColor = MutableStateFlow(Color.BLACK)
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
                // Emulator: simulate broadcasters instead of scanning
                fakeSource.startFake()
                _isScanning.value = true
                _statusMessage.value = "Emulator fake scan started"
                logi(TAG, "Started fake scan on emulator")
                logv(TAG, "Fake devices count after start: ${_devices.value.size}")
                return
            }

            // Ensure permissions are granted
            if (!_permissionsGranted.value) {
                logw(TAG, "Cannot start scan — permissions not granted")
                _statusMessage.value = "Please grant Bluetooth permissions to scan."
                return
            }

            // Prevent duplicate scans
            if (_isScanning.value) {
                logw(TAG, "Scan already in progress → ignoring startScan()")
                return
            }

            // Start real scanner
            _statusMessage.value = ""
            periodicScanner.startScanningAuracastEa()
            _isScanning.value = true
            logi(TAG, "Started periodic advertising scan")
        } catch (e: Exception) {
            loge(TAG, "Failed to start scan", e)
        }
    }

    /**
     * Stops ongoing scan (fake or real).
     */
    fun stopScan() {
        try {
            logd(TAG, "stopScan() called")

            if (!_isScanning.value) {
                logw(TAG, "No active scan to stop")
                return
            }

            if (isRunningOnEmulator()) {
                // Emulator → stop fake scan
                fakeSource.stopFake()
                _isScanning.value = false
                val count = _devices.value.size
                _statusMessage.value = "Fake scan stopped — $count devices shown"
                _statusColor.value = Color.BLACK
                logi(TAG, "Stopped fake scan on emulator with $count devices")
                return
            }

            // Stop real scanner
            periodicScanner.stopScanningAuracastEa()
            _isScanning.value = false
            val count = _devices.value.size
            _statusMessage.value = "Scan stopped — $count devices found"
            _statusColor.value = Color.BLACK
            logi(TAG, "Stopped periodic advertising scan with $count devices")
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
            try {
                logi(TAG, "Phone is receiver → selecting BIS $bisIndex on ${device.address}")
                bisSelectionManager.selectBisChannel(device, bisIndex)
                logv(TAG, "Selection successful for device=${device.name}, bisIndex=$bisIndex")
            } catch (e: Exception) {
                logw(TAG, "Failed to select BIS: ${e.message}")
                loge(TAG, "Error selecting BIS", e)
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
