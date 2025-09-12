package com.android.broadcastassistant.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.broadcastassistant.R
import com.android.broadcastassistant.audio.BassGattManager
import com.android.broadcastassistant.ble.AuracastEaScanner
import com.android.broadcastassistant.ble.BisSelectionManager
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisSelectionResult
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.util.loge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore(name = "auracast_prefs")
private val SELECTED_BIS_KEY = intPreferencesKey("selected_bis_index")

/**
 * ViewModel for Auracast scanning, device list, and BIS selection.
 *
 * Handles:
 * - Device scanning via [AuracastEaScanner]
 * - BIS selection via [BisSelectionManager]
 * - State updates for UI
 * - Persists first selected BIS index for highlight
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    // State flows exposed to the UI
    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _statusColor = MutableStateFlow(Color.Black)

    private val bassGattManager = BassGattManager(getApplication<Application>().applicationContext)
    private val bisSelectionManager = BisSelectionManager(bassGattManager)
    private val scanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    init {
        // Initialize scanner and restore saved BIS selection
        viewModelScope.launch {
            try {
                logd("Initializing Auracast scanner and restoring BIS selection")
                val savedBisIndex = getApplication<Application>().dataStore.data
                    .map { prefs -> prefs[SELECTED_BIS_KEY] ?: -1 }
                    .first()
                logi("Saved BIS index loaded: $savedBisIndex")

                scanner.broadcasters.collect { scannedDevices ->
                    logd("Scanned ${scannedDevices.size} Auracast devices")
                    _devices.value = scannedDevices.map { device ->
                        if (savedBisIndex != -1) device.copy(selectedBisIndexes = listOf(savedBisIndex))
                        else device
                    }
                }
            } catch (e: Exception) {
                loge("Error initializing scanner", e)
            }
        }
    }

    /** Update Bluetooth permissions state and stop scan if revoked */
    fun updatePermissionsGranted(granted: Boolean) {
        logi("Bluetooth permissions updated: $granted")
        _permissionsGranted.value = granted
        if (!granted) {
            logw("Permissions revoked, stopping scan")
            stopScan()
        }
    }

    /** Start Auracast scanning */
    fun startScan() {
        if (!_permissionsGranted.value) {
            logw("Cannot start scan: permissions not granted")
            return
        }
        if (_isScanning.value) {
            logw("Scan already running")
            return
        }

        logi("Starting Auracast scan")
        _devices.value = emptyList() // clear current devices
        scanner.clearDevices()
        scanner.startScanningAuracastEa()
        _isScanning.value = true
        _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
        _statusColor.value = Color.Blue
    }

    /** Stop Auracast scanning */
    fun stopScan() {
        if (!_isScanning.value) {
            logw("Stop scan called but scan not running")
            return
        }

        logi("Stopping Auracast scan")
        scanner.stopScanningAuracastEa()
        _isScanning.value = false
        _statusMessage.value = getApplication<Application>().getString(
            R.string.scan_stopped, _devices.value.size
        )
        _statusColor.value = Color.Black
    }

    /** Toggle scanning state */
    fun toggleScan() {
        logd("Toggling scan. Current state: ${_isScanning.value}")
        if (_isScanning.value) stopScan() else startScan()
    }

    /**
     * Safely select BIS channels for a device.
     * - Updates device state and UI
     * - Persists first selected BIS index
     * - Handles success and failure messages
     */
    fun selectBisChannels(device: AuracastDevice, bisIndexes: List<Int>) {
        if (bisIndexes.isEmpty()) {
            logw("selectBisChannels called with empty list")
            return
        }

        viewModelScope.launch {
            logi("Selecting BIS indexes ${bisIndexes.joinToString()} for device ${device.address}")
            _statusMessage.value = getApplication<Application>().getString(
                R.string.switching_language, bisIndexes.joinToString(", ")
            )
            _statusColor.value = Color.Yellow

            try {
                val result = bisSelectionManager.selectBisChannels(device, bisIndexes)
                when (result) {
                    is BisSelectionResult.Success -> {
                        logi("BIS selection succeeded: ${result.selectedIndexes.joinToString()}")
                        val indexesStr = result.selectedIndexes.joinToString(", ")
                        _statusMessage.value = getApplication<Application>().getString(
                            R.string.connected_language, indexesStr
                        )
                        _statusColor.value = Color.Green

                        _devices.value = _devices.value.map { d ->
                            if (d.address == device.address) d.copy(selectedBisIndexes = result.selectedIndexes)
                            else d
                        }

                        getApplication<Application>().dataStore.edit { prefs ->
                            prefs[SELECTED_BIS_KEY] = result.selectedIndexes.first()
                        }
                    }
                    is BisSelectionResult.Failure -> {
                        logw("BIS selection failed: ${result.reason}")
                        _statusMessage.value = "BIS switch failed: ${result.reason}"
                        _statusColor.value = Color.Red
                    }
                }
            } catch (e: Exception) {
                loge("Exception during BIS selection", e)
                _statusMessage.value = "BIS switch failed: ${e.message}"
                _statusColor.value = Color.Red
            }
        }
    }

    /** Cleanup resources when ViewModel is destroyed */
    override fun onCleared() {
        super.onCleared()
        try {
            logi("ViewModel clearing: stopping scanner and disconnecting GATT")
            scanner.stopScanningAuracastEa()
            bassGattManager.disconnectAll()
        } catch (e: Exception) {
            loge("Error during ViewModel cleared", e)
        }
    }
}
