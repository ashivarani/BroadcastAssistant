package com.android.broadcastassistant.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.util.size
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logv
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Auracast Extended Advertising (EA) scanner.
 *
 * This class is responsible for:
 * - Scanning BLE extended advertisements that carry Auracast broadcast information.
 * - Parsing BIS (Broadcast Isochronous Stream) metadata via [BisParser].
 * - Maintaining and publishing a flow of discovered [com.android.broadcastassistant.data.AuracastDevice]s sorted by RSSI.
 *
 * Usage:
 * ```
 * val scanner = AuracastEaScanner(context)
 * scanner.startScanningAuracastEa()
 * scanner.broadcasters.collect { devices -> updateUi(devices) }
 * ```
 */
class AuracastEaScanner(private val context: Context) {

    companion object {
        /** Service UUID filter for Auracast BIS packets. */
        private val AURACAST_SERVICE_UUID = BisParser.getAuracastUuid()
    }

    /** System BLE scanner instance, lazily initialized. */
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        try {
            logd("bluetoothLeScanner init: Entered")
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bluetoothLeScanner.also {
                logi("bluetoothLeScanner init: Successfully obtained scanner = $it")
            }
        } catch (e: Exception) {
            loge("bluetoothLeScanner init: Failed to get BluetoothLeScanner", e)
            null
        }
    }

    /** Internal storage for discovered broadcasters, keyed by MAC address. */
    private val broadcastersMap = mutableMapOf<String, AuracastDevice>()

    /** Backing flow for broadcasters. */
    private val _broadcastersFlow = MutableStateFlow<List<AuracastDevice>>(emptyList())

    /** Public immutable view of broadcasters flow. */
    val broadcasters: StateFlow<List<AuracastDevice>> = _broadcastersFlow

    /**
     * Start scanning for Auracast EA packets.
     * starts real BLE scanning with a filter on the Auracast service UUID.
     */
    fun startScanningAuracastEa() {
        try {
            logd("startScanningAuracastEa: Entered")

            // Permission check
            if (!hasScanPermission()) {
                loge("startScanningAuracastEa: Missing required scan permissions")
                return
            }
            // scan setup
            val filter = ScanFilter.Builder()
                .setServiceUuid(AURACAST_SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .setLegacy(false)
                .build()

            bluetoothLeScanner?.startScan(listOf(filter), settings, auracastScanCallback)
            logi("startScanningAuracastEa: Real Auracast EA scan started successfully")
        } catch (se: SecurityException) {
            loge("startScanningAuracastEa: SecurityException while starting scan", se)
        } catch (e: Exception) {
            loge("startScanningAuracastEa: Unexpected error", e)
        }
    }

    /**
     * Stop scanning for Auracast EA advertisements.
     * - Stops BLE scanning otherwise.
     */
    fun stopScanningAuracastEa() {
        try {
            logd("stopScanningAuracastEa: Entered")
            bluetoothLeScanner?.stopScan(auracastScanCallback)
            logi("stopScanningAuracastEa: Scan stopped successfully")
        } catch (se: SecurityException) {
            loge("stopScanningAuracastEa: SecurityException while stopping scan", se)
        } catch (e: Exception) {
            loge("stopScanningAuracastEa: Unexpected error", e)
        }
    }

    /**
     * BLE scan callback implementation for handling advertisement results.
     */
    private val auracastScanCallback = object : ScanCallback() {

        /** Called when a single advertisement result is received. */
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                logd("auracastScanCallback.onScanResult: Entered with MAC=${result.device.address}, RSSI=${result.rssi}")
                processAuracastEaPacket(result)
                logi("auracastScanCallback.onScanResult: Processed successfully for ${result.device.address}")
            } catch (e: Exception) {
                loge("auracastScanCallback.onScanResult: Failed to process result", e)
            }
        }

        /** Called when a batch of results is received. */
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            try {
                logd("auracastScanCallback.onBatchScanResults: Entered with ${results.size} results")
                results.forEach { processAuracastEaPacket(it) }
                logi("auracastScanCallback.onBatchScanResults: Batch processed successfully")
            } catch (e: Exception) {
                loge("auracastScanCallback.onBatchScanResults: Failed to process batch", e)
            }
        }

        /** Called when scanning fails. */
        override fun onScanFailed(errorCode: Int) {
            loge("auracastScanCallback.onScanFailed: Scan failed with errorCode=$errorCode")
        }
    }

    /**
     * Process a single Auracast EA packet and update the broadcaster list.
     *
     * @param result The BLE [ScanResult] containing Auracast EA data.
     */
    private fun processAuracastEaPacket(result: ScanResult) {
        try {
            logd("processAuracastEaPacket: Entered for ${result.device.address}")

            val record = result.scanRecord ?: return
            val serviceData = record.serviceData[AURACAST_SERVICE_UUID] ?: return

            // Parse BIS information
            val parsedBisInfo = BisParser.parse(serviceData)
            val address = result.device.address

            // Update device info
            val updatedDevice = broadcastersMap[address]?.copy(
                name = getDeviceNameIfAllowed(result),
                rssi = result.rssi,
                bisChannels = mergeBisChannelLists(
                    broadcastersMap[address]?.bisChannels.orEmpty(),
                    parsedBisInfo.bisChannels
                ),
                broadcastId = parsedBisInfo.broadcastId ?: broadcastersMap[address]?.broadcastId
            ) ?: AuracastDevice(
                name = getDeviceNameIfAllowed(result),
                address = address,
                rssi = result.rssi,
                bisChannels = parsedBisInfo.bisChannels,
                broadcastId = parsedBisInfo.broadcastId,
                broadcastCode = null
            )

            broadcastersMap[address] = updatedDevice
            _broadcastersFlow.value = broadcastersMap.values.sortedByDescending { it.rssi }

            logi("processAuracastEaPacket: Updated $address with ${updatedDevice.bisChannels.size} BIS channels")

            // Parse manufacturer-specific BIS extensions if available
            parseManufacturerBisData(record, address)
        } catch (e: Exception) {
            loge("processAuracastEaPacket: Failed to process packet", e)
        }
    }

    /**
     * Parse manufacturer-specific BIS extensions and merge into broadcaster records.
     *
     * @param record The [android.bluetooth.le.ScanRecord] containing manufacturer data.
     * @param address The device MAC address.
     */
    private fun parseManufacturerBisData(record: ScanRecord, address: String) {
        try {
            logd("parseManufacturerBisData: Entered for $address")

            val manufacturerData = record.manufacturerSpecificData
            for (i in 0 until manufacturerData.size) {
                val rawData = manufacturerData.valueAt(i) ?: continue
                val extraBisInfo = BisParser.parse(rawData)

                broadcastersMap[address]?.let { existing ->
                    broadcastersMap[address] = existing.copy(
                        bisChannels = mergeBisChannelLists(existing.bisChannels, extraBisInfo.bisChannels)
                    )
                    _broadcastersFlow.value = broadcastersMap.values.sortedByDescending { it.rssi }
                    logv("parseManufacturerBisData: Added ${extraBisInfo.bisChannels.size} channels for $address")
                }
            }

            logi("parseManufacturerBisData: Completed for $address")
        } catch (e: Exception) {
            loge("parseManufacturerBisData: Failed for $address", e)
        }
    }

    /**
     * Merge old and new BIS channel lists, avoiding duplicates.
     *
     * @param oldList The existing list of [com.android.broadcastassistant.data.BisChannel].
     * @param newList The newly discovered [com.android.broadcastassistant.data.BisChannel].
     * @return A merged and sorted list by channel index.
     */
    private fun mergeBisChannelLists(oldList: List<BisChannel>, newList: List<BisChannel>): List<BisChannel> {
        return try {
            logd("mergeBisChannelLists: Entered with old=${oldList.size}, new=${newList.size}")

            val map = oldList.associateBy { it.index }.toMutableMap()
            for (ch in newList) map[ch.index] = ch

            logi("mergeBisChannelLists: Merged into ${map.size} total channels")
            map.values.sortedBy { it.index }
        } catch (e: Exception) {
            loge("mergeBisChannelLists: Failed to merge lists", e)
            oldList
        }
    }

    /**
     * Retrieve the device name if allowed by permissions.
     *
     * @param result The BLE [ScanResult] containing the device.
     * @return The device name, or `"Unknown"` if not available.
     */
    private fun getDeviceNameIfAllowed(result: ScanResult): String {
        return try {
            logd("getDeviceNameIfAllowed: Entered for ${result.device.address}")

            val device = result.device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    device.name ?: "Unknown"
                } else {
                    logw("getDeviceNameIfAllowed: BLUETOOTH_CONNECT not granted → Unknown for ${device.address}")
                    "Unknown"
                }
            } else {
                device.name ?: "Unknown"
            }
        } catch (se: SecurityException) {
            logw("getDeviceNameIfAllowed: SecurityException for ${result.device.address}", se)
            "Unknown"
        } catch (e: Exception) {
            loge("getDeviceNameIfAllowed: Unexpected error", e)
            "Unknown"
        }
    }

    /**
     * Check if required scan permissions are granted.
     *
     * - API 33+: Requires BLUETOOTH_SCAN + NEARBY_WIFI_DEVICES.
     * - API 31–32: Requires BLUETOOTH_SCAN.
     * - Pre-API 31: Always true.
     *
     * @return True if scanning is permitted, false otherwise.
     */
    private fun hasScanPermission(): Boolean {
        return try {
            logd("hasScanPermission: Entered")

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val scan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED
                    val wifi = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
                            PackageManager.PERMISSION_GRANTED
                    val granted = scan && wifi
                    logi("hasScanPermission: API 33+ → scan=$scan, wifi=$wifi, granted=$granted")
                    granted
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                            PackageManager.PERMISSION_GRANTED
                    logi("hasScanPermission: API 31–32 → granted=$granted")
                    granted
                }
                else -> {
                    logv("hasScanPermission: Pre-API 31 → always true")
                    true
                }
            }
        } catch (e: Exception) {
            loge("hasScanPermission: Failed to check permissions", e)
            false
        }
    }
}