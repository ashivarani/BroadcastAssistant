package com.android.broadcastassistant.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.PermissionHelper
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Auracast Extended Advertising (EA) scanner.
 *
 * Scans for Auracast broadcasters using BLE Extended Advertising packets.
 * Extracts BIS (Broadcast Isochronous Stream) info via [BisParserHelper] and
 * exposes a live list of discovered devices via a [StateFlow].
 *
 * Only EA packets containing the Auracast UUID are processed.
 */
class AuracastEaScanner(private val context: Context) {

    /** Lazily-initialized system Bluetooth LE scanner */
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as BluetoothManager
            manager.adapter?.bluetoothLeScanner.also {
                logi("BluetoothLeScanner obtained: $it")
            }
        } catch (e: Exception) {
            loge("Failed to get BluetoothLeScanner", e)
            null
        }
    }
    /** Checks if required scan permissions are granted */
    private fun hasScanPermission(): Boolean = PermissionHelper.hasBlePermissions(context)

    /** Map of currently discovered Auracast devices, keyed by MAC address */
    private val broadcastersMap = mutableMapOf<String, AuracastDevice>()

    /** Internal state flow backing the list of devices */
    private val _broadcastersFlow = MutableStateFlow<List<AuracastDevice>>(emptyList())

    /** Public read-only flow of discovered devices */
    val broadcasters: StateFlow<List<AuracastDevice>> = _broadcastersFlow

    /** Auracast-specific service UUID used to identify devices */
    private val auracastUUID = BisParser.getAuracastUuid()

    /** Clears all currently discovered devices */
    fun clearDevices() {
        logd("AuracastEaScanner: Clearing all devices")
        broadcastersMap.clear()
        _broadcastersFlow.value = emptyList()
    }

    /** Start scanning for Auracast broadcasters using BLE Extended Advertising */
    fun startScanningAuracastEa() {
        try {
            if (!hasScanPermission()) {
                loge("Missing scan permissions → cannot start scan")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .setLegacy(false) // Ensure EA packets are captured
                .build()

            bluetoothLeScanner?.startScan(null, settings, auracastScanCallback)
            logi("Auracast scan started (listening for EA packets)")
        } catch (se: SecurityException) {
            loge("SecurityException while starting scan", se)
        } catch (e: Exception) {
            loge("Unexpected error while starting scan", e)
        }
    }

    /** Stops scanning for Auracast EA advertisements */
    fun stopScanningAuracastEa() {
        try {
            bluetoothLeScanner?.stopScan(auracastScanCallback)
            logi("Auracast scan stopped successfully")
        } catch (se: SecurityException) {
            loge("SecurityException while stopping scan", se)
        } catch (e: Exception) {
            loge("Unexpected error while stopping scan", e)
        }
    }

    /** BLE scan callback for handling incoming advertisements */
    private val auracastScanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                processAuracastEaPacket(result)
            } catch (e: Exception) {
                loge("Failed to process scan result", e)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processAuracastEaPacket(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            loge("Auracast scan failed: errorCode=$errorCode")
        }
    }

    /**
     * Processes a single EA packet, extracts BIS info, and updates the device list.
     *
     * Only updates devices that contain the Auracast UUID.
     */
    private fun processAuracastEaPacket(result: ScanResult) {
        val record = result.scanRecord ?: return
        val address = result.device.address
        val name = getDeviceNameIfAllowed(result)
        if (name.isEmpty() || name == "Unknown") return

        val serviceData = record.serviceData
        val auracastData =
            serviceData[auracastUUID] ?: return  // Only EA packets with Auracast UUID

        // Parse BIS data using helper
        var updatedDevice: AuracastDevice? = BisParserHelper.parseBisData(
            address, name, result.rssi, auracastData, broadcastersMap[address]
        )

        // Merge manufacturer-specific BIS data properly
        updatedDevice = BisParserHelper.parseManufacturerBisData(
            record, address, name, result.rssi, updatedDevice
        )

        // If no BIS info, still create a basic device entry
        if (updatedDevice == null) {
            updatedDevice = AuracastDevice(name, address, result.rssi,
                emptyList(), 0, null)
        }

        // Update map and flow only if list changed
        val oldDeviceList = broadcastersMap.values.toList()
        broadcastersMap[address] = updatedDevice
        val newDeviceList = broadcastersMap.values.sortedByDescending { it.rssi }
        if (oldDeviceList != newDeviceList) {
            _broadcastersFlow.value = newDeviceList
        }
    }

    /** Safely retrieves the Bluetooth device name if permissions allow */
    private fun getDeviceNameIfAllowed(result: ScanResult): String {
        val device = result.device
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                logw("BLUETOOTH_CONNECT not granted → Unknown for ${device.address}")
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