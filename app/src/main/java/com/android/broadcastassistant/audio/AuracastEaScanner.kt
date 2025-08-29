package com.android.broadcastassistant.audio

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.ble.BisParser
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.data.BisChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.core.util.size

class AuracastEaScanner(private val context: Context) {

    companion object {
        private const val TAG = "AuracastEaScanner"
        private val AURACAST_SERVICE_UUID = BisParser.getAuracastUuid()
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter?.bluetoothLeScanner
    }

    private val broadcastersMap = mutableMapOf<String, AuracastDevice>()
    private val _broadcastersFlow = MutableStateFlow<List<AuracastDevice>>(emptyList())
    val broadcasters: StateFlow<List<AuracastDevice>> = _broadcastersFlow

    // Fake source for emulator mode
    private var fakeSource: FakeAuracastBroadcasterSource? = null

    fun startScanningAuracastEa() {
        if (!hasScanPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission")
            return
        }

        val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("sdk_gphone", ignoreCase = true)

        if (isEmulator) {
            Log.i(TAG, "Running in emulator mode — starting fake Auracast scan")
            fakeSource = FakeAuracastBroadcasterSource(_broadcastersFlow).also { it.startFake() }
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

        try {
            bluetoothLeScanner?.startScan(listOf(filter), settings, auracastScanCallback)
            Log.i(TAG, "Started scanning for Auracast EA packets")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while starting Auracast scan", se)
        }
    }

    fun stopScanningAuracastEa() {
        fakeSource?.stopFake()
        fakeSource = null
        try {
            bluetoothLeScanner?.stopScan(auracastScanCallback)
            Log.i(TAG, "Stopped Auracast EA scan")
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while stopping Auracast scan", se)
        }
    }

    private val auracastScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processAuracastEaPacket(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { processAuracastEaPacket(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Auracast scan failed: $errorCode")
        }
    }

    private fun processAuracastEaPacket(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.serviceData[AURACAST_SERVICE_UUID] ?: return

        val parsedBisInfo = BisParser.parse(serviceData)
        val address = result.device.address

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

        parseManufacturerBisData(record, address)
    }

    private fun parseManufacturerBisData(record: ScanRecord, address: String) {
        val manufacturerData = record.manufacturerSpecificData
        for (i in 0 until manufacturerData.size) {
            val rawData = manufacturerData.valueAt(i) ?: continue
            val extraBisInfo = BisParser.parse(rawData)
            broadcastersMap[address]?.let { existing ->
                broadcastersMap[address] = existing.copy(
                    bisChannels = mergeBisChannelLists(existing.bisChannels, extraBisInfo.bisChannels)
                )
                _broadcastersFlow.value = broadcastersMap.values.sortedByDescending { it.rssi }
            }
        }
    }

    private fun mergeBisChannelLists(oldList: List<BisChannel>, newList: List<BisChannel>): List<BisChannel> {
        val map = oldList.associateBy { it.index }.toMutableMap()
        for (ch in newList) map[ch.index] = ch
        return map.values.sortedBy { it.index }
    }

    private fun getDeviceNameIfAllowed(result: ScanResult): String {
        return try {
            val device = result.device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Unknown"
                } else "Unknown"
            } else {
                device.name ?: "Unknown"
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "No BLUETOOTH_CONNECT permission", se)
            "Unknown"
        }
    }

    private fun hasScanPermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }
}
