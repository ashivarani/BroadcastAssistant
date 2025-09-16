package com.android.broadcastassistant.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
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
import com.android.broadcastassistant.delegator.ConnectedReceiverManager
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

@OptIn(ExperimentalCoroutinesApi::class)
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    /** Separate StateFlows for broadcasters and connected receivers */
    private val _broadcasters = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val broadcasters: StateFlow<List<AuracastDevice>> = _broadcasters

    private val _receivers = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val receivers: StateFlow<List<AuracastDevice>> = _receivers

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

    private val connectedReceiverManager =
        ConnectedReceiverManager(getApplication<Application>().applicationContext)

    init {
        // Observe connected receivers
        connectedReceiverManager.connectedReceivers.observeForever { receiversList ->
            logd("ConnectedReceiverManager: ${receiversList.size} receivers found")

            viewModelScope.launch {
                val savedBisIndex = getApplication<Application>().dataStore.data
                    .map { prefs -> prefs[SELECTED_BIS_KEY] ?: -1 }
                    .first()

                val context = getApplication<Application>().applicationContext

                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    logw("BLUETOOTH_CONNECT permission not granted, cannot fetch receivers")
                    return@launch
                }

                _receivers.value = receiversList.map { device ->
                    AuracastDevice(
                        address = device.address,
                        name = device.name ?: "Unknown",
                        broadcastId = null, // Receivers have no broadcastId
                        sourceId = null,
                        selectedBisIndexes = if (savedBisIndex != -1) listOf(savedBisIndex) else emptyList(),
                        rssi = 0
                    )
                }
            }
        }

        // Initial fetch of connected receivers
        connectedReceiverManager.fetchReceivers()
    }

    /** Update Bluetooth permission state */
    fun updatePermissionsGranted(granted: Boolean) {
        logi("Bluetooth permissions updated: $granted")
        _permissionsGranted.value = granted
        if (granted) connectedReceiverManager.fetchReceivers()
    }

    /** Start scanning for broadcasters */
    fun startScan() {
        if (!_permissionsGranted.value || _isScanning.value) return

        logi("Starting Auracast scan")
        _broadcasters.value = emptyList()
        scanner.clearDevices()
        scanner.startScanningAuracastEa()
        _isScanning.value = true
        _statusMessage.value = getApplication<Application>().getString(R.string.scan_starting)
        _statusColor.value = Color.Blue

        // Observe broadcasters from scanner
        viewModelScope.launch {
            scanner.broadcasters.collect { list ->
                _broadcasters.value = list
            }
        }
    }

    /** Stop scanning for broadcasters */
    fun stopScan() {
        if (!_isScanning.value) return

        logi("Stopping Auracast scan")
        scanner.stopScanningAuracastEa()
        _isScanning.value = false
        _statusMessage.value = getApplication<Application>().getString(
            R.string.scan_stopped, _broadcasters.value.size
        )
        _statusColor.value = Color.Black
    }

    /** Toggle scanning state */
    fun toggleScan() {
        if (_isScanning.value) stopScan() else startScan()
    }

    /** Select BIS channels for a device safely */
    fun selectBisChannels(device: AuracastDevice, bisIndexes: List<Int>) {
        if (bisIndexes.isEmpty()) return

        viewModelScope.launch {
            _statusMessage.value = getApplication<Application>().getString(
                R.string.switching_language, bisIndexes.joinToString(", ")
            )
            _statusColor.value = Color.Yellow

            try {
                val targetDevice = _receivers.value.firstOrNull()
                if (targetDevice == null) {
                    _statusMessage.value = getApplication<Application>().getString(R.string.no_scan_delegator)
                    _statusColor.value = Color.Red
                    logw("No connected receiver found for BIS selection")
                    return@launch
                }

                val sources = bassGattManager.readBroadcastReceiveState(targetDevice.address)
                val match = sources.find { it.broadcastId == device.broadcastId }
                if (match == null) {
                    _statusMessage.value = getApplication<Application>().getString(R.string.no_matching_broadcast)
                    _statusColor.value = Color.Red
                    logw("No matching broadcast for device ${device.address}")
                    return@launch
                }

                device.sourceId = match.sourceId
                val result = bisSelectionManager.selectBisChannels(device, bisIndexes)

                when (result) {
                    is BisSelectionResult.Success -> {
                        val indexesStr = result.selectedIndexes.joinToString(", ")
                        _statusMessage.value = getApplication<Application>().getString(
                            R.string.connected_language, indexesStr
                        )
                        _statusColor.value = Color.Green
                        logi("BIS selection successful for ${device.address}: $indexesStr")

                        _broadcasters.value = _broadcasters.value.map { d ->
                            if (d.address == device.address) d.copy(selectedBisIndexes = result.selectedIndexes)
                            else d
                        }

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

    override fun onCleared() {
        super.onCleared()
        try {
            scanner.stopScanningAuracastEa()
            bassGattManager.disconnectAll()
            logi("AuracastViewModel cleared successfully")
        } catch (e: Exception) {
            loge("Error during ViewModel cleared", e)
        }
    }
}
