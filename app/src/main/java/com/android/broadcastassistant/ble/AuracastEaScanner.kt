package com.android.broadcastassistant.ble

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.data.*
import com.android.broadcastassistant.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Auracast Extended Advertising (EA) scanner.
 *
 * Responsibilities:
 * - Scans BLE extended advertisements that carry Auracast broadcast info.
 * - Parses BIS metadata via [BisParserHelper].
 * - Publishes a flow of discovered [AuracastDevice]s sorted by RSSI.
 */
class AuracastEaScanner(private val context: Context) {

    companion object {
        /** Auracast Service UUID filter */
        private val AURACAST_SERVICE_UUID = BisParser.getAuracastUuid()
    }

    /** System BLE scanner instance, lazily initialized */
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bluetoothLeScanner.also {
                logi("bluetoothLeScanner init: Obtained scanner = $it")
            }
        } catch (e: Exception) {
            loge("bluetoothLeScanner init: Failed to get BluetoothLeScanner", e)
            null
        }
    }
    private var hasRetriedAfterFailure = false
    /** Internal storage of discovered broadcasters */
    private val broadcastersMap = mutableMapOf<String, AuracastDevice>()

    /** Backing flow for broadcasters */
    private val _broadcastersFlow = MutableStateFlow<List<AuracastDevice>>(emptyList())

    /** Public immutable flow of broadcasters */
    val broadcasters: StateFlow<List<AuracastDevice>> = _broadcastersFlow

    /**
     * Start scanning for Auracast EA packets.
     */
    fun startScanningAuracastEa() {
        try {
            logd("startScanningAuracastEa: Entered")

            if (!PermissionHelper.hasBlePermissions(context)) {
                loge("startScanningAuracastEa: Missing required scan permissions")
                return
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(AURACAST_SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .setLegacy(false)
                .build()

            bluetoothLeScanner?.startScan(listOf(filter), settings, auracastScanCallback)
            logi("startScanningAuracastEa: Scan started successfully")
        } catch (se: SecurityException) {
            loge("startScanningAuracastEa: SecurityException while starting scan", se)
        } catch (e: Exception) {
            loge("startScanningAuracastEa: Unexpected error while starting scan", e)
        }
    }

    /**
     * Stop scanning for Auracast EA advertisements.
     */
    fun stopScanningAuracastEa() {
        try {
            bluetoothLeScanner?.stopScan(auracastScanCallback)
            logi("stopScanningAuracastEa: Scan stopped successfully")
        } catch (se: SecurityException) {
            loge("stopScanningAuracastEa: SecurityException while stopping scan", se)
        } catch (e: Exception) {
            loge("stopScanningAuracastEa: Unexpected error while stopping scan", e)
        }
    }

    /**
     * Restart scanning by stopping, clearing, and starting again.
     */
    fun restartScanningAuracastEa() {
        logd("restartScanningAuracastEa: Restarting scan")
        stopScanningAuracastEa()
        clearDevices()
        startScanningAuracastEa()
    }

    /**
     * Clears all currently discovered devices.
     */
    fun clearDevices() {
        logd("clearDevices: Resetting broadcasters list")
        broadcastersMap.clear()
        _broadcastersFlow.value = emptyList()
    }

    /**
     * BLE scan callback for handling advertisement results.
     */
    private val auracastScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                logd("onScanResult: MAC=${result.device.address}, RSSI=${result.rssi}")
                hasRetriedAfterFailure = false // reset retry flag if scanning works again
                processAuracastEaPacket(result)
            } catch (e: Exception) {
                loge("onScanResult: Failed to process result", e)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            try {
                results.forEach { processAuracastEaPacket(it) }
                logi("onBatchScanResults: Processed ${results.size} results")
            } catch (e: Exception) {
                loge("onBatchScanResults: Failed to process batch", e)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            loge("Scan failed with errorCode=$errorCode")

            if (errorCode == SCAN_FAILED_INTERNAL_ERROR && !hasRetriedAfterFailure) {
                hasRetriedAfterFailure = true
                logw("Retrying scan in 2s (only once)...")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    restartScanningAuracastEa()
                }
            } else {
                logw("Not retrying scan (already retried or permanent error)")
            }
        }
    }

    /**
     * Process a single Auracast EA packet and update broadcaster list.
     */
    private fun processAuracastEaPacket(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            val address = result.device.address
            val name = getDeviceNameIfAllowed(result)
            if (name.isEmpty() || name == "Unknown") return

            val serviceData = record.serviceData[AURACAST_SERVICE_UUID] ?: return

            // Use BisParserHelper like Version 1
            var updatedDevice = BisParserHelper.parseBisData(
                address,
                name,
                result.rssi,
                serviceData,
                broadcastersMap[address]
            )

            updatedDevice = BisParserHelper.parseManufacturerBisData(
                record,
                address,
                name,
                result.rssi,
                updatedDevice
            )

            // If no BIS info, create a fallback device
            if (updatedDevice == null) {
                updatedDevice = AuracastDevice(
                    name = name,
                    address = address,
                    rssi = result.rssi,
                    bisChannels = emptyList(),
                    broadcastId = 0,
                    broadcastCode = null
                )
            }

            // Update map and flow
            broadcastersMap[address] = updatedDevice
            _broadcastersFlow.value = broadcastersMap.values.sortedByDescending { it.rssi }

            logi("processAuracastEaPacket: Updated $address with ${updatedDevice.bisChannels.size} BIS channels")
        } catch (e: Exception) {
            loge("processAuracastEaPacket: Failed to process packet", e)
        }
    }

    /**
     * Safely get the Bluetooth device name if permissions allow.
     */
    private fun getDeviceNameIfAllowed(result: ScanResult): String {
        return try {
            val device = result.device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                logw("getDeviceNameIfAllowed: BLUETOOTH_CONNECT not granted → Unknown for ${device.address}")
                "Unknown"
            } else {
                device.name ?: "Unknown"
            }
        } catch (e: Exception) {
            loge("getDeviceNameIfAllowed: Unexpected error", e)
            "Unknown"
        }
    }
}
