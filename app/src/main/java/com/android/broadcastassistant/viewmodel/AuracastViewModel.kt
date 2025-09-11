package com.android.broadcastassistant.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.*
import com.android.broadcastassistant.R
import com.android.broadcastassistant.audio.*
import com.android.broadcastassistant.ble.*
import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// Extension property for DataStore
private val Application.dataStore by preferencesDataStore(name = "auracast_prefs")

// Key to store the selected BIS index
private val SELECTED_BIS_KEY = intPreferencesKey("selected_bis_index")

/**
 * ViewModel for Auracast Assistant app.
 *
 * Responsibilities:
 * - Manage BLE scanning for Auracast broadcasters
 * - Maintain device list & scanning state
 * - Handle BIS selection (single BIS only)
 * - Log commands for UI
 * - Persist last selected BIS for UI highlight (no auto-reconnect)
 *
 * @param application Application context
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    /** List of currently discovered Auracast devices */
    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    /** List of command logs for UI */
    private val _commandLogs = MutableStateFlow<List<CommandLog>>(emptyList())

    /** Current scanning state */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** Bluetooth permission state */
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    /** Status message shown on UI (scanning, connecting, error) */
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    /** Status text color for UI */
    private val _statusColor = MutableStateFlow(Color.Black)

    /** Controller for BASS functionality */
    private val bassGattManager = BassGattManager(getApplication<Application>().applicationContext)

    /** Manager handling BIS selection logic with command logging */
    private val bisSelectionManager = BisSelectionManager(
        onDeviceUpdate = { updatedDevices ->
            logd("Devices updated → ${updatedDevices.size} devices")
            _devices.value = updatedDevices
        },
        getCurrentDevices = { _devices.value },
        bassGattManager = bassGattManager,
        onCommandLog = { commandLog ->
            val updatedList = _commandLogs.value.toMutableList()
            updatedList.add(commandLog)
            _commandLogs.value = updatedList
            logd("Command log added → ${commandLog.message}")
        }
    )

    /** Scanner for Auracast Extended Advertising packets */
    private val scanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    init {
        viewModelScope.launch {
            try {
                logi("Initializing AuracastEaScanner")

                // Load last selected BIS index for UI highlight
                val savedBisIndex = try {
                    getApplication<Application>().dataStore.data
                        .map { prefs -> prefs[SELECTED_BIS_KEY] ?: -1 }
                        .first()
                } catch (e: Exception) {
                    loge("Failed to read saved BIS index", e)
                    -1
                }

                // Collect scanned devices
                scanner.broadcasters.collect { scannedDevices ->
                    _devices.value = scannedDevices.map { device ->
                        if (savedBisIndex != -1) device.copy(selectedBisIndex = savedBisIndex)
                        else device
                    }
                    logd("Collected ${scannedDevices.size} scanned devices")
                }
            } catch (e: Exception) {
                loge("Error initializing scanner", e)
            }
        }
    }

    /** Update Bluetooth permission state */
    fun updatePermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        logd("Permissions updated → granted=$granted")
        if (!granted) stopScan()
    }

    /** Start Auracast scanning */
    fun startScan() {
        try {
            logd("startScan() called")
            if (!_permissionsGranted.value) {
                _statusMessage.value = getApplication<Application>().getString(R.string.scan_permissions_required)
                _statusColor.value = Color.Red
                logw("Cannot start scan, permissions missing")
                return
            }

            if (_isScanning.value) return

            _devices.value = emptyList()
            scanner.clearDevices()
            scanner.startScanningAuracastEa()
            _isScanning.value = true
            _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
            _statusColor.value = Color.Blue
            logi("Scan started successfully")
        } catch (e: Exception) {
            loge("Failed to start scan", e)
            _statusMessage.value = "Scan start failed"
            _statusColor.value = Color.Red
        }
    }

    /** Stop Auracast scanning */
    fun stopScan() {
        try {
            if (!_isScanning.value) return
            scanner.stopScanningAuracastEa()
            _isScanning.value = false
            val count = _devices.value.size
            _statusMessage.value = getApplication<Application>().getString(R.string.scan_stopped, count)
            _statusColor.value = Color.Black
            logi("Scan stopped with $count devices")
        } catch (e: Exception) {
            loge("Failed to stop scan", e)
            _statusMessage.value = "Scan stop failed"
            _statusColor.value = Color.Red
        }
    }

    /** Toggle scanning state */
    fun toggleScan() {
        logd("toggleScan() called")
        try {
            if (_isScanning.value) stopScan() else startScan()
        } catch (e: Exception) {
            loge("Error toggling scan", e)
        }
    }

    /**
     * Select a BIS channel for a device and log command messages.
     * Stores the selected BIS index in DataStore for UI highlight only.
     *
     * @param device AuracastDevice to select BIS for
     * @param bisIndex Index of BIS channel
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        viewModelScope.launch {
            try {
                val bis = device.bisChannels.find { it.index == bisIndex }
                val lang = bis?.language ?: "Unknown"

                // Update status for UI
                _statusMessage.value = getApplication<Application>().getString(R.string.switching_language, lang)
                _statusColor.value = Color.Yellow

                // Perform BIS selection
                bisSelectionManager.selectBisChannel(device, bisIndex)

                // Save selected BIS index for UI highlight only
                getApplication<Application>().dataStore.edit { prefs ->
                    prefs[SELECTED_BIS_KEY] = bisIndex
                }

                _statusMessage.value = getApplication<Application>().getString(R.string.connected_language, lang)
                _statusColor.value = Color.Green

            } catch (_: TimeoutCancellationException) {
                _statusMessage.value = getApplication<Application>().getString(R.string.switch_timeout, bisIndex.toString())
                _statusColor.value = Color.Red

            } catch (e: Exception) {
                loge("BIS channel selection failed", e)
                _statusMessage.value = getApplication<Application>().getString(R.string.switch_failed)
                _statusColor.value = Color.Red
            }
        }
    }

    /** Stop scanner when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        try {
            scanner.stopScanningAuracastEa()
            logi("Scanner stopped on ViewModel cleared")
        } catch (e: Exception) {
            loge("Error stopping scanner on ViewModel cleared", e)
        }
    }
}
