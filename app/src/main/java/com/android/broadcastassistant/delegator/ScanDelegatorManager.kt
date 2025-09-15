package com.android.broadcastassistant.delegator

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.android.broadcastassistant.util.*
import com.android.broadcastassistant.util.UuidUtils
import kotlinx.coroutines.*

/**
 * Manager for discovering Scan Delegators (BLE devices hosting the BASS service).
 *
 * Responsibilities:
 * - Scan for nearby BLE devices advertising the BASS service.
 * - Maintain a live list of discovered devices in [discoveredDelegators].
 * - Start and stop BLE scanning safely with permission and error handling.
 */
@RequiresApi(Build.VERSION_CODES.S)
class ScanDelegatorManager(private val context: Context) {

    companion object {
        // BASS service UUID filter for scanning
        private val BASS_SERVICE_UUID: ParcelUuid = ParcelUuid(UuidUtils.BASS_SERVICE_UUID)
    }

    private val adapter by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private val scanner by lazy { adapter.bluetoothLeScanner }

    /** LiveData of discovered Scan Delegators */
    val discoveredDelegators = MutableLiveData<List<BluetoothDevice>>(emptyList())

    private var scanJob: Job? = null

    /** Internal BLE scan callback */
    private val callback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                logd("ScanDelegatorManager: Found Scan Delegator ${device.address}")

                // Add new device if not already in the list
                val current = discoveredDelegators.value?.toMutableList() ?: mutableListOf()
                if (current.none { it.address == device.address }) {
                    current.add(device)
                    discoveredDelegators.postValue(current)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            loge("ScanDelegatorManager: Scan failed with error code $errorCode")
        }
    }

    /**
     * Starts BLE scanning for Scan Delegators advertising the BASS service.
     *
     * Handles permission checks and logs errors on failure.
     */
    fun startScan() {
        if (scanner == null) {
            loge("ScanDelegatorManager: BluetoothLeScanner is null")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            loge("ScanDelegatorManager: BLUETOOTH_SCAN permission not granted")
            return
        }

        // Filter to only devices advertising the BASS service
        val filter = ScanFilter.Builder()
            .setServiceUuid(BASS_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // aggressive scan for immediate results
            .build()

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                scanner.startScan(listOf(filter), settings, callback)
                logi("ScanDelegatorManager: Scan started for BASS service devices")
            } catch (se: SecurityException) {
                loge("ScanDelegatorManager: SecurityException during startScan", se)
            } catch (e: Exception) {
                loge("ScanDelegatorManager: Unexpected error during startScan", e)
            }
        }
    }

    /**
     * Stops BLE scanning for Scan Delegators.
     *
     * Handles permission checks and safely cancels ongoing scan coroutine.
     */
    fun stopScan() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
            ) {
                scanner?.stopScan(callback)
                scanJob?.cancel()
                scanJob = null
                logi("ScanDelegatorManager: Scan stopped")
            } else {
                logw("ScanDelegatorManager: BLUETOOTH_SCAN permission not granted, cannot stop scan")
            }
        } catch (se: SecurityException) {
            loge("ScanDelegatorManager: SecurityException while stopping scan", se)
        } catch (e: Exception) {
            loge("ScanDelegatorManager: Unexpected error while stopping scan", e)
        }
    }
}
