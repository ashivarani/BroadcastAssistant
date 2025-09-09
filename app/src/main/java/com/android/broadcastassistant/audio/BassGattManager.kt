package com.android.broadcastassistant.audio

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.android.broadcastassistant.data.BassCommand
import com.android.broadcastassistant.util.UuidUtils
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manager for Broadcast Audio Scan Service (BASS) GATT interactions.
 *
 * Responsibilities:
 * - Connect to Scan Delegator devices via GATT.
 * - Discover BASS service and Control Point characteristic.
 * - Send BASS Control Point commands via [BassCommand].
 * - Handle safe disconnection and resource cleanup.
 */
class BassGattManager(private val context: Context) {

    companion object {
        private val BASS_SERVICE_UUID: UUID = UuidUtils.BASS_SERVICE_UUID
        private val BASS_CONTROL_POINT_UUID: UUID = UuidUtils.BASS_CONTROL_POINT_UUID
    }

    private var bluetoothGatt: BluetoothGatt? = null // Holds current GATT connection

    /**
     * Sends a BASS Control Point command to a device.
     *
     * @param command Contains device address, Control Point data, and auto-disconnect flag.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun sendControlPoint(command: BassCommand) {
        try {
            logi("sendControlPoint: Sending command to ${command.deviceAddress}")

            // Get Bluetooth adapter
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            // Attempt to get remote device from address
            val device: BluetoothDevice? = try { adapter?.getRemoteDevice(command.deviceAddress) }
            catch (e: IllegalArgumentException) {
                loge("sendControlPoint: Invalid device address ${command.deviceAddress}", e)
                null
            }

            if (device == null) {
                loge("sendControlPoint: Device not found: ${command.deviceAddress}")
                return
            }

            // Check BLUETOOTH_CONNECT permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                loge("sendControlPoint: BLUETOOTH_CONNECT permission not granted")
                return
            }

            // Connect to GATT device
            val gatt = connectGattSuspend(device)
            bluetoothGatt = gatt

            // Discover BASS service
            val service: BluetoothGattService? = gatt.getService(BASS_SERVICE_UUID)
            if (service == null) {
                loge("sendControlPoint: BASS service not found on ${command.deviceAddress}")
                disconnect()
                return
            }

            // Get Control Point characteristic
            val controlPointChar: BluetoothGattCharacteristic? =
                service.getCharacteristic(BASS_CONTROL_POINT_UUID)
            if (controlPointChar == null) {
                loge("sendControlPoint: BASS Control Point characteristic not found")
                disconnect()
                return
            }

            // Write command to characteristic
            writeCharacteristicSuspend(gatt, controlPointChar, command.controlPointData)
            logi("sendControlPoint: Command write completed for ${command.deviceAddress}")

            // Disconnect if autoDisconnect is true
            if (command.autoDisconnect) {
                logd("sendControlPoint: Auto-disconnect enabled, disconnecting")
                disconnect()
            }

        } catch (e: Exception) {
            loge("sendControlPoint: Unexpected error", e)
            if (command.autoDisconnect) disconnect()
        }
    }

    /**
     * Connects to a device via GATT and suspends until services are discovered.
     *
     * @param device Bluetooth device to connect
     * @return Connected [BluetoothGatt] instance
     */
    private suspend fun connectGattSuspend(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {

                // Called when connection state changes
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    try {
                        // Handle GATT errors
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            loge("GATT error $status on ${gatt.device.address}")
                            cont.resumeWithException(RuntimeException("GATT error $status"))
                            return
                        }

                        when (newState) {
                            BluetoothGatt.STATE_CONNECTED -> {
                                logi("Connected to ${gatt.device.address}")

                                // Discover services after successful connection
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
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

                            BluetoothGatt.STATE_DISCONNECTED -> {
                                logw("Disconnected from ${gatt.device.address}")
                            }
                        }
                    } catch (e: Exception) {
                        loge("Unexpected error during connection state change", e)
                        cont.resumeWithException(e)
                    }
                }

                // Called when services are discovered
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            logi("Services discovered for ${gatt.device.address}")
                            cont.resume(gatt) // Resume coroutine with GATT object
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

            // Initiate GATT connection
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

    /**
     * Writes data to a characteristic and suspends until the write is initiated.
     *
     * @param gatt GATT connection
     * @param characteristic Characteristic to write
     * @param value Byte array to write
     */
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

            // If write successfully initiated, resume coroutine
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

    /** Disconnects and closes the current GATT connection */
    fun disconnect() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    bluetoothGatt?.close() // Close connection
                    logi("Closed GATT connection")
                } catch (se: SecurityException) {
                    loge("SecurityException on bluetoothGatt.close()", se)
                }
            } else {
                logw("BLUETOOTH_CONNECT permission not granted, skipping close()")
            }
        } catch (e: Exception) {
            loge("Unexpected error during disconnect", e)
        } finally {
            bluetoothGatt = null // Clear reference
        }
    }
}
