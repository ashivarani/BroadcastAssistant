package com.android.broadcastassistant.audio

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.data.BassCommand
import com.android.broadcastassistant.delegator.BassBroadcastStateParser
import com.android.broadcastassistant.util.*
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.coroutines.*

/**
 * Manager for Broadcast Audio Scan Service (BASS) GATT interactions.
 * Handles connections to Scan Delegator devices, sending Control Point commands,
 * reading Broadcast Receive State (BRS), and managing multiple simultaneous connections.
 */
class BassGattManager(private val context: Context) {

    companion object {
        private val BASS_SERVICE_UUID: UUID = UuidUtils.BASS_SERVICE_UUID // BASS service UUID
        private val BASS_CONTROL_POINT_UUID: UUID = UuidUtils.BASS_CONTROL_POINT_UUID // Control Point UUID
        private val BASS_BRS_UUID: UUID = UuidUtils.BASS_BROADCAST_RECEIVE_STATE_UUID // Broadcast Receive State UUID
    }

    private val bluetoothGatt = mutableMapOf<String, BluetoothGatt>() // Active GATT connections keyed by device address
    private val gattCallbacks = mutableMapOf<String, BluetoothGattCallback>() // Store callbacks per device

    /**
     * Sends a BASS Control Point command to a device.
     * Handles GATT connection, service discovery, retries, and optional auto-disconnect.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun sendControlPoint(command: BassCommand) {
        try {
            logi("sendControlPoint: Sending command to ${command.deviceAddress}")

            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val device: BluetoothDevice? = try {
                adapter?.getRemoteDevice(command.deviceAddress)
            } catch (e: IllegalArgumentException) {
                loge("Invalid device address: ${command.deviceAddress}", e)
                null
            }

            if (device == null) {
                loge("Device not found: ${command.deviceAddress}")
                return
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                loge("BLUETOOTH_CONNECT permission not granted")
                return
            }

            // Use existing GATT or connect if not present
            val gatt = bluetoothGatt[command.deviceAddress] ?: connectGattSuspend(device).also {
                bluetoothGatt[command.deviceAddress] = it
            }

            val service = gatt.getService(BASS_SERVICE_UUID)
            if (service == null) {
                loge("BASS service not found on ${command.deviceAddress}")
                disconnect(command.deviceAddress)
                return
            }

            val controlPointChar = service.getCharacteristic(BASS_CONTROL_POINT_UUID)
            if (controlPointChar == null) {
                loge("BASS Control Point characteristic not found")
                disconnect(command.deviceAddress)
                return
            }

            // Retry write up to 2 times
            repeat(2) { attempt ->
                try {
                    writeCharacteristicSuspend(gatt, controlPointChar, command.controlPointData)
                    logi("Command write succeeded for ${command.deviceAddress}")
                    return
                } catch (e: Exception) {
                    logw("Attempt ${attempt + 1} failed for ${command.deviceAddress}", e)
                }
            }

            loge("All retries failed for ${command.deviceAddress}")
            if (command.autoDisconnect) disconnect(command.deviceAddress)

        } catch (e: Exception) {
            loge("Unexpected error in sendControlPoint", e)
            if (command.autoDisconnect) disconnect(command.deviceAddress)
        }
    }

    /**
     * Reads the Broadcast Receive State (BRS) from a connected device.
     * Returns a list of [com.android.broadcastassistant.delegator.BassBroadcastStateParser.BassSourceInfo] or empty list on failure.
     */
    @ExperimentalCoroutinesApi
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun readBroadcastReceiveState(deviceAddress: String): List<BassBroadcastStateParser.BassSourceInfo> {
        val gatt = bluetoothGatt[deviceAddress]
            ?: throw IllegalStateException("No GATT connection for $deviceAddress")

        val service = gatt.getService(BASS_SERVICE_UUID)
            ?: throw IllegalStateException("BASS service not found on $deviceAddress")

        val stateChar = service.getCharacteristic(BASS_BRS_UUID)
            ?: throw IllegalStateException("Broadcast Receive State characteristic not found")

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            loge("BLUETOOTH_CONNECT permission not granted")
            return emptyList()
        }

        return suspendCancellableCoroutine { cont ->
            try {
                // Temporary callback for this read operation
                val callback = object : BluetoothGattCallback() {
                    override fun onCharacteristicRead(
                        gatt: BluetoothGatt,
                        characteristic: BluetoothGattCharacteristic,
                        status: Int
                    ) {
                        if (characteristic.uuid == BASS_BRS_UUID) {
                            if (status == BluetoothGatt.GATT_SUCCESS) {
                                val value = @Suppress("DEPRECATION") characteristic.value ?: byteArrayOf()
                                val sources = BassBroadcastStateParser.parse(value) // Parse BRS
                                cont.resume(sources) {}
                                logi("BRS read succeeded for ${gatt.device.address}")
                            } else {
                                logw("Failed to read BRS, status=$status")
                                cont.resume(emptyList()) {}
                            }
                        }
                    }
                }

                // Store callback for this device
                gattCallbacks[deviceAddress] = callback

                // Initiate characteristic read
                if (!gatt.readCharacteristic(stateChar)) {
                    loge("readCharacteristic initiation failed for $deviceAddress")
                    cont.resume(emptyList()) {}
                }

                // Ensure coroutine cancellation cleans up if needed
                cont.invokeOnCancellation {
                    try { disconnect(deviceAddress) } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                loge("Failed to read Broadcast Receive State", e)
                cont.resume(emptyList()) {}
            }
        }
    }

    /** Disconnects and cleans up a single device's GATT connection */
    fun disconnect(deviceAddress: String) {
        bluetoothGatt[deviceAddress]?.let { gatt ->
            try {
                if (ContextCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    gatt.close() // Close GATT safely
                    logi("Closed GATT connection for $deviceAddress")
                } else {
                    logw("BLUETOOTH_CONNECT permission not granted; skipping close() for $deviceAddress")
                }
            } catch (se: SecurityException) {
                loge("SecurityException on bluetoothGatt.close() for $deviceAddress", se)
            } finally {
                bluetoothGatt.remove(deviceAddress) // Remove from map
                gattCallbacks.remove(deviceAddress) // Remove callback
            }
        }
    }

    /** Disconnects all active GATT connections */
    fun disconnectAll() {
        val addresses = bluetoothGatt.keys.toList() // Copy keys to avoid concurrent modification
        addresses.forEach { disconnect(it) }
    }

    /** Suspended GATT connection with service discovery */
    private suspend fun connectGattSuspend(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    try {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            loge("GATT error $status on ${gatt.device.address}")
                            cont.resumeWithException(RuntimeException("GATT error $status"))
                            return
                        }

                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            logi("Connected to ${gatt.device.address}")
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    logd("Discovering services on ${gatt.device.address}")
                                    gatt.discoverServices()
                                } catch (se: SecurityException) {
                                    loge("discoverServices failed", se)
                                    cont.resumeWithException(se)
                                }
                            } else {
                                cont.resumeWithException(SecurityException("BLUETOOTH_CONNECT permission not granted"))
                            }
                        }
                    } catch (e: Exception) {
                        loge("Unexpected error during connection state change", e)
                        cont.resumeWithException(e)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            logi("Services discovered for ${gatt.device.address}")
                            cont.resume(gatt)
                        } else {
                            loge("Service discovery failed: $status")
                            cont.resumeWithException(RuntimeException("Service discovery failed: $status"))
                        }
                    } catch (e: Exception) {
                        loge("Unexpected error during service discovery", e)
                        cont.resumeWithException(e)
                    }
                }
            }

            try {
                logd("Initiating GATT connection to ${device.address}")
                device.connectGatt(context, false, callback)
            } catch (se: SecurityException) {
                loge("SecurityException during connectGatt", se)
                cont.resumeWithException(se)
            } catch (e: Exception) {
                loge("Unexpected error during connectGatt", e)
                cont.resumeWithException(e)
            }
        }

    /** Suspended characteristic write with logging and exception handling */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun writeCharacteristicSuspend(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) = suspendCancellableCoroutine { cont ->
        try {
            logd("Writing to characteristic ${characteristic.uuid}")
            val result = gatt.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            logi("Write initiated, result=$result")
            if (result == BluetoothGatt.GATT_SUCCESS) cont.resume(Unit)
            else cont.resumeWithException(RuntimeException("writeCharacteristic initiation failed: $result"))
        } catch (se: SecurityException) {
            loge("SecurityException during write", se)
            cont.resumeWithException(se)
        } catch (e: Exception) {
            loge("Unexpected error during write", e)
            cont.resumeWithException(e)
        }
    }
}
