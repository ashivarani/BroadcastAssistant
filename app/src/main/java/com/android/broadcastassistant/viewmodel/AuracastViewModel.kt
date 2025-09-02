package com.android.broadcastassistant.viewmodel

import android.app.Application
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.broadcastassistant.audio.AuracastEaScanner
import com.android.broadcastassistant.audio.BassController
import com.android.broadcastassistant.audio.BisSelectionManager
import com.android.broadcastassistant.audio.FakeAuracastBroadcasterSource
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class AuracastViewModel(application: Application) : AndroidViewModel(application) {

    private val _devices = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val devices: StateFlow<List<AuracastDevice>> = _devices

    private val _statusColor = MutableStateFlow(Color.BLACK)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    // Core Auracast components
    private val bassController = BassController(getApplication<Application>().applicationContext)
    private val bisSelectionManager = BisSelectionManager(
        onDeviceUpdate = { updatedDevices -> _devices.value = updatedDevices },
        getCurrentDevices = { _devices.value },
        bassController = bassController
    )

    private val periodicScanner = AuracastEaScanner(getApplication<Application>().applicationContext)

    // Fake data generator (for emulator only)
    private val fakeSource = FakeAuracastBroadcasterSource(_devices)

    init {
        viewModelScope.launch {
            if (isRunningOnEmulator()) {
                logi(TAG, "Running on emulator → using FakeAuracastBroadcasterSource")
                fakeSource.startFake()
            } else {
                logi(TAG, "Running on real device → using AuracastEaScanner")
                periodicScanner.broadcasters.collectLatest { scannedDevices ->
                    _devices.value = scannedDevices
                }
            }
        }
    }

    /**
     * Called from Activity/PermissionHelper when BLE permissions are granted/denied.
     */
    fun updatePermissionsGranted(granted: Boolean) {
        _permissionsGranted.value = granted
        if (!granted) {
            stopScan()
        }
    }

    fun startScan() {
        if (isRunningOnEmulator()) {
            // Emulator ignores permissions
            fakeSource.startFake()
            _isScanning.value = true
            _statusMessage.value = "Emulator fake scan started"
            logi(TAG, "Started fake scan on emulator")
            return
        }

        if (!_permissionsGranted.value) {
            logw(TAG, "Cannot start scan — permissions not granted")
            _statusMessage.value = "Please grant Bluetooth permissions to scan."
            return
        }
        if (_isScanning.value) {
            logw(TAG, "Scan already in progress")
            return
        }
        _statusMessage.value = ""
        periodicScanner.startScanningAuracastEa()
        _isScanning.value = true
        logi(TAG, "Started periodic advertising scan")
    }

    fun stopScan() {
        if (!_isScanning.value) {
            logw(TAG, "No active scan to stop")
            return
        }

        if (isRunningOnEmulator()) {
            fakeSource.stopFake()
            _isScanning.value = false
            val count = _devices.value.size
            _statusMessage.value = "Fake scan stopped — $count devices shown"
            _statusColor.value = Color.BLACK
            logi(TAG, "Stopped fake scan on emulator")
            return
        }

        periodicScanner.stopScanningAuracastEa()
        _isScanning.value = false
        val count = _devices.value.size
        _statusMessage.value = "Scan stopped — $count devices found"
        _statusColor.value = Color.BLACK
        logi(TAG, "Stopped periodic advertising scan")
    }

    fun toggleScan() {
        if (_isScanning.value) stopScan() else startScan()
    }

    /**
     * Select BIS from the chosen broadcaster.
     * Since phone is receiver, directly route to BisSelectionManager.
     */
    fun selectBisChannel(device: AuracastDevice, bisIndex: Int) {
        viewModelScope.launch {
            try {
                logi(TAG, "Phone is receiver → selecting BIS $bisIndex")
                bisSelectionManager.selectBisChannel(device, bisIndex)
            } catch (e: Exception) {
                logw(TAG, "Failed to select BIS: ${e.message}")
            }
        }
    }

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
        fakeSource.stopFake()
    }

    companion object {
        private const val TAG = "AuracastViewModel"
    }
}
