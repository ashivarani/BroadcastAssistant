package com.android.broadcastassistant.viewmodel

import com.android.broadcastassistant.R
import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.broadcastassistant.ble.AuracastEaScanner
import com.android.broadcastassistant.audio.BassController
import com.android.broadcastassistant.ble.BisSelectionManager
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import androidx.compose.ui.graphics.Color

/**
 * ViewModel responsible for:
 * - Scanning Auracast broadcasters via BLE.
 * - Maintaining a list of discovered devices.
 * - Handling BIS (Broadcast Isochronous Stream) channel selection.
 * - Updating UI with scanning/connection status and colors.
 *
 * This ViewModel uses:
 * - [AuracastEaScanner] → handles BLE scanning for Auracast broadcasters.
 * - [BassController] & [BisSelectionManager] → manages BIS channel switching.
 *
 * UI Observables:
 * - [devices] → currently discovered Auracast devices.
 * - [isScanning] → scanning state (true/false).
 * - [permissionsGranted] → BLE permissions status.
 * - [statusMessage] → message describing current scan/connection state.
 * - [statusColor] → color corresponding to the state (for UI feedback).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    /** Flow holding currently discovered Auracast devices. */
    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    /** Flow holding current scanning status (true if scanning). */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** Flow holding BLE permission granted state. */
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    /** Flow holding a human-readable status message for UI. */
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    /** Flow holding a color representing the current state (scanning, success, error, etc). */
    private val _statusColor = MutableStateFlow(Color.Black)
    val statusColor: StateFlow<Color> = _statusColor

    // Core Auracast components:
    private val bassController = BassController(getApplication<Application>().applicationContext)

    /** BIS selection manager → used to switch BIS channels on selected devices. */
    private val bisSelectionManager = BisSelectionManager(
        onDeviceUpdate = { updatedDevices -> _devices.value = updatedDevices },
        getCurrentDevices = { _devices.value },
        bassController = bassController
    )

    /** BLE scanner for Auracast broadcasters. */
    private val periodicScanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    init {
        // Collect live scan results as they arrive
        viewModelScope.launch {
            try {
                logi("AuracastViewModel: Initializing AuracastEaScanner for real devices")
                periodicScanner.broadcasters.collectLatest { scannedDevices ->
                    logv("AuracastViewModel: Scanned devices updated → count=${scannedDevices.size}")
                    _devices.value = scannedDevices
                }
            } catch (e: Exception) {
                loge("AuracastViewModel: Error initializing scanner", e)
            }
        }
    }

    /**
     * Updates BLE permission state.
     * If permissions are revoked, scanning will stop automatically.
     *
     * @param granted true if permissions granted, false otherwise
     */
    fun updatePermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        if (!granted) {
            logw("AuracastViewModel: Permissions revoked → stopping scan")
            stopScan()
        }
    }

    /**
     * Starts scanning for Auracast devices.
     * Requires BLE permissions to be granted.
     */
    fun startScan() {
        try {
            logd("AuracastViewModel: startScan() called")

            // Ensure permissions are available
            if (!_permissionsGranted.value) {
                _statusMessage.value =
                    getApplication<Application>().getString(R.string.scan_permissions_required)
                return
            }

            // Prevent duplicate scanning
            if (_isScanning.value) return

            // Update UI and start scanner
            _statusMessage.value =
                getApplication<Application>().getString(R.string.scan_starting)
            _statusColor.value =
                Color(ContextCompat.getColor(getApplication(), R.color.status_scanning))
            periodicScanner.startScanningAuracastEa()
            _isScanning.value = true
        } catch (e: Exception) {
            loge("AuracastViewModel: Failed to start scan", e)
        }
    }

    /**
     * Stops ongoing BLE scanning.
     * Updates UI with number of devices discovered so far.
     */
    fun stopScan() {
        try {
            if (!_isScanning.value) return

            periodicScanner.stopScanningAuracastEa()
            _isScanning.value = false
            val count = _devices.value.size

            _statusMessage.value =
                getApplication<Application>().getString(R.string.scan_stopped_real, count)
            _statusColor.value =
                Color(ContextCompat.getColor(getApplication(), R.color.status_idle))
        } catch (e: Exception) {
            loge("AuracastViewModel: Failed to stop scan", e)
        }
    }

    /**
     * Toggles scanning state.
     * - If scanning → stops.
     * - If stopped → starts scanning.
     */
    fun toggleScan() {
        logd("AuracastViewModel: toggleScan() called")
        try {
            if (_isScanning.value) stopScan() else startScan()
        } catch (e: Exception) {
            loge("AuracastViewModel: Error toggling scan", e)
        }
    }

    /**
     * Selects a BIS (Broadcast Isochronous Stream) channel on a given Auracast device.
     *
     * @param device The target Auracast device.
     * @param bisIndex Index of the BIS channel to select.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        viewModelScope.launch {
            try {
                val bis = device.bisChannels.find { it.index == bisIndex }
                val lang = bis?.language ?: "Unknown"

                // Update UI → starting language switch
                _statusMessage.value =
                    getApplication<Application>().getString(R.string.switching_language, lang)
                _statusColor.value =
                    Color(ContextCompat.getColor(getApplication(), R.color.status_switching))

                // Attempt BIS selection with timeout
                withTimeout(5000) {
                    bisSelectionManager.selectBisChannel(device, bisIndex)
                }

                // Success → update UI
                _statusMessage.value =
                    getApplication<Application>().getString(R.string.connected_language, lang)
                _statusColor.value =
                    Color(ContextCompat.getColor(getApplication(), R.color.status_success))

            } catch (_: TimeoutCancellationException) {
                // Timeout → show error
                _statusMessage.value =
                    getApplication<Application>().getString(R.string.switch_timeout, bisIndex.toString())
                _statusColor.value =
                    Color(ContextCompat.getColor(getApplication(), R.color.status_error))

            } catch (e: Exception) {
                // General failure
                loge("AuracastViewModel: BIS channel selection failed", e)
                _statusMessage.value =
                    getApplication<Application>().getString(R.string.switch_failed)
                _statusColor.value =
                    Color(ContextCompat.getColor(getApplication(), R.color.status_error))
            }
        }
    }

    /**
     * Called when the ViewModel is destroyed.
     * Ensures scanning is stopped to release resources.
     */
    override fun onCleared() {
        super.onCleared()
        try {
            periodicScanner.stopScanningAuracastEa()
        } catch (e: Exception) {
            loge("AuracastViewModel: Error stopping scanner on ViewModel cleared", e)
        }
    }
}
