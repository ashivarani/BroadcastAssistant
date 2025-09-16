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
import com.android.broadcastassistant.delegator.ScanDelegatorManager
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Application.dataStore by preferencesDataStore(name = "auracast_prefs")
private val SELECTED_BIS_KEY = intPreferencesKey("selected_bis_index")

/**
 * ViewModel for managing Auracast BLE devices and BIS channel selection.
 *
 * Responsibilities:
 * - Scans for Auracast broadcasters and Scan Delegators
 * - Maintains device list, scan status, and UI state
 * - Handles BIS channel selection using [BisSelectionManager]
 * - Persists selected BIS index in DataStore
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _statusColor = MutableStateFlow(Color.Black)

    private val bassGattManager = BassGattManager(getApplication<Application>().applicationContext)
    private val bisSelectionManager = BisSelectionManager(bassGattManager)
    private val scanDelegatorManager = ScanDelegatorManager(getApplication<Application>().applicationContext)
    private val scanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    init {
        // Observe discovered Scan Delegators for logging
        viewModelScope.launch {
            scanDelegatorManager.discoveredDelegators.observeForever { delegators ->
                logd("Discovered ${delegators.size} Scan Delegators")
            }
        }

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

    /**
     * Update Bluetooth permission state.
     *
     * @param granted true if permissions are granted, false otherwise.
     */
    fun updatePermissionsGranted(granted: Boolean) {
        logi("Bluetooth permissions updated: $granted")
        _permissionsGranted.value = granted
        if (!granted) stopScan() // Stop scanning if permissions are revoked
    }

    /**
     * Start scanning for Auracast broadcasters and Scan Delegators.
     *
     * Clears previous scan results, updates UI state, and logs events.
     */
    fun startScan() {
        if (!_permissionsGranted.value) return
        if (_isScanning.value) return

        logi("Starting Auracast scan")
        _devices.value = emptyList()
        scanner.clearDevices() // Clear previous scan results
        scanner.startScanningAuracastEa()
        _isScanning.value = true
        _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
        _statusColor.value = Color.Blue

        scanDelegatorManager.startScan() // Start Scan Delegator discovery
    }

    /**
     * Stop scanning for Auracast broadcasters and Scan Delegators.
     *
     * Updates scan state, status message, and logs events.
     */
    fun stopScan() {
        if (!_isScanning.value) return

        logi("Stopping Auracast scan")
        scanner.stopScanningAuracastEa()
        _isScanning.value = false
        _statusMessage.value = getApplication<Application>().getString(
            R.string.scan_stopped, _devices.value.size
        )
        _statusColor.value = Color.Black

        scanDelegatorManager.stopScan() // Stop delegator scan
    }

    /**
     * Toggle scanning state (start or stop scan).
     */
    fun toggleScan() {
        if (_isScanning.value) stopScan() else startScan()
    }

    /**
     * Select BIS channels for a device safely.
     *
     * Steps:
     * 1. Pick first available Scan Delegator.
     * 2. Read Broadcast Receive State from delegator.
     * 3. Match device broadcastId to sourceId.
     * 4. Use [BisSelectionManager] to send BIS select/modify command.
     * 5. Update state and persist selected BIS index.
     *
     * @param device Target Auracast device.
     * @param bisIndexes List of requested BIS indexes.
     */
    fun selectBisChannels(device: AuracastDevice, bisIndexes: List<Int>) {
        if (bisIndexes.isEmpty()) return

        viewModelScope.launch {
            _statusMessage.value = getApplication<Application>().getString(
                R.string.switching_language, bisIndexes.joinToString(", ")
            )
            _statusColor.value = Color.Yellow

            try {
                // Pick first available Scan Delegator
                val delegator = scanDelegatorManager.discoveredDelegators.value?.firstOrNull()
                if (delegator == null) {
                    _statusMessage.value = getApplication<Application>().getString(R.string.no_scan_delegator)
                    _statusColor.value = Color.Red
                    logw("No Scan Delegator found for BIS selection")
                    return@launch
                }

                // Read Broadcast Receive State characteristic
                val sources = bassGattManager.readBroadcastReceiveState(delegator.address)
                val match = sources.find { it.broadcastId == device.broadcastId }
                if (match == null) {
                    _statusMessage.value = getApplication<Application>().getString(R.string.no_matching_broadcast)
                    _statusColor.value = Color.Red
                    logw("No matching broadcast for device ${device.address} on delegator ${delegator.address}")
                    return@launch
                }

                // Update device with sourceId
                device.sourceId = match.sourceId

                // Use BisSelectionManager to send BIS command
                val result = bisSelectionManager.selectBisChannels(device, bisIndexes)
                when (result) {
                    is BisSelectionResult.Success -> {
                        val indexesStr = result.selectedIndexes.joinToString(", ")
                        _statusMessage.value = getApplication<Application>().getString(
                            R.string.connected_language, indexesStr
                        )
                        _statusColor.value = Color.Green
                        logi("BIS selection successful for ${device.address}: $indexesStr")

                        // Update devices list state
                        _devices.value = _devices.value.map { d ->
                            if (d.address == device.address) d.copy(selectedBisIndexes = result.selectedIndexes)
                            else d
                        }

                        // Persist first selected BIS index
                        getApplication<Application>().dataStore.edit { prefs ->
                            prefs[SELECTED_BIS_KEY] = result.selectedIndexes.first()
                        }
                    }
                    is BisSelectionResult.Failure -> {
                        _statusMessage.value = getApplication<Application>().getString(R.string.bis_switch_failed)
                        _statusColor.value = Color.Red
                        logw("BIS selection failed for ${device.address}: ${result.reason}")
                    }
                }

            } catch (e: Exception) {
                _statusMessage.value = getApplication<Application>().getString(R.string.bis_switch_failed)
                _statusColor.value = Color.Red
                loge("Exception during BIS selection for ${device.address}", e)
            }
        }
    }

    /**
     * Clean up resources when ViewModel is cleared.
     *
     * Stops scanning, disconnects devices, and logs cleanup.
     */
    override fun onCleared() {
        super.onCleared()
        try {
            scanner.stopScanningAuracastEa()
            bassGattManager.disconnectAll()
            scanDelegatorManager.stopScan()
            logi("AuracastViewModel cleared successfully")
        } catch (e: Exception) {
            loge("Error during ViewModel cleared", e)
        }
    }
}
