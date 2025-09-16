package com.android.broadcastassistant.delegator

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.android.broadcastassistant.util.UuidUtils
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.util.loge
import kotlinx.coroutines.*

/**
 * Fetches bonded or connected Auracast/BASS LE Audio receivers.
 *
 * Only displays already paired or currently connected devices.
 * Does not perform BLE scanning.
 *
 * Handles permissions, security exceptions, and duplicates.
 *
 * @param context Android context for accessing BluetoothManager and checking permissions
 */
@RequiresApi(Build.VERSION_CODES.S)
class ConnectedReceiverManager(private val context: Context) {

    /** LiveData containing all bonded or connected receivers */
    val connectedReceivers = MutableLiveData<List<BluetoothDevice>>(emptyList())

    // Coroutine scope for background fetching
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Fetch all bonded (paired) and currently connected Auracast/BASS receivers.
     *
     * Performs:
     * 1. Permission check for BLUETOOTH_CONNECT
     * 2. Fetch bonded devices containing Auracast/BASS UUIDs
     * 3. Fetch currently connected GATT devices with Auracast/BASS UUIDs
     * 4. Removes duplicate devices by Bluetooth address
     *
     * Logs important steps and handles exceptions.
     */
    fun fetchReceivers() {
        scope.launch {
            // Permission check
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                logw("ConnectedReceiverManager: BLUETOOTH_CONNECT permission not granted")
                connectedReceivers.postValue(emptyList())
                return@launch
            }

            try {
                val devices = mutableListOf<BluetoothDevice>()

                val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

                // Fetch bonded devices containing Auracast/BASS service UUIDs
                val bonded = adapter.bondedDevices.filter { device ->
                    val uuids = try {
                        device.uuids?.map { it.uuid } ?: emptyList()
                    } catch (_: SecurityException) {
                        // Some devices may throw SecurityException when reading UUIDs
                        emptyList()
                    }
                    uuids.contains(UuidUtils.AURACAST_SERVICE_UUID.uuid) ||
                            uuids.contains(UuidUtils.BASS_SERVICE_UUID)
                }
                devices.addAll(bonded)
                logi("ConnectedReceiverManager: Bonded devices found: ${bonded.size}")

                // Fetch currently connected devices via GATT profile
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val connected = try {
                    bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
                } catch (se: SecurityException) {
                    logw("ConnectedReceiverManager: Cannot get connected devices: ${se.message}")
                    emptyList<BluetoothDevice>()
                }

                connected.forEach { device ->
                    val uuids = try { device.uuids?.map { it.uuid } ?: emptyList() } catch (_: SecurityException) { emptyList() }
                    if (uuids.contains(UuidUtils.AURACAST_SERVICE_UUID.uuid) ||
                        uuids.contains(UuidUtils.BASS_SERVICE_UUID)
                    ) devices.add(device)
                }
                logi("ConnectedReceiverManager: Connected devices found: ${connected.size}")

                // Remove duplicates by address and post value
                val uniqueDevices = devices.distinctBy { it.address }
                connectedReceivers.postValue(uniqueDevices)
                logi("ConnectedReceiverManager: Total receivers after deduplication: ${uniqueDevices.size}")

            } catch (e: Exception) {
                // Catch any unexpected exceptions
                loge("ConnectedReceiverManager: Exception fetching receivers", e)
                connectedReceivers.postValue(emptyList())
            }
        }
    }
}
