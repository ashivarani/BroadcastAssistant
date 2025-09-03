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
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Controller for the Broadcast Audio Scan Service (BASS).
 *
 * This class is responsible for:
 * - Establishing GATT connections to Scan Delegator devices.
 * - Discovering the BASS service and Control Point characteristic.
 * - Writing BASS Control Point commands for BIS configuration.
 * - Disconnecting safely after operations.
 *
 * Typical flow:
 * 1. [sendControlPoint] is called with a device address and command.
 * 2. Device connection is established and GATT services are discovered.
 * 3. The Control Point command is written to the device.
 * 4. Connection is closed automatically after the operation.
 */
class BassController(private val context: Context) {

    companion object {
        private val BASS_SERVICE_UUID: UUID =
            UUID.fromString("0000184F-0000-1000-8000-00805f9b34fb")
        private val BASS_CONTROL_POINT_UUID: UUID =
            UUID.fromString("00002B2B-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * Sends a raw Control Point command to a Scan Delegator device.
     *
     * @param deviceAddress The Bluetooth MAC address of the Scan Delegator.
     * @param controlPointData The raw byte array command to be written.
     *
     * Steps:
     * - Validate permissions.
     * - Connect to the device via GATT.
     * - Discover the BASS service and control point characteristic.
     * - Write the control point data.
     * - Disconnect after completion.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun sendControlPoint(deviceAddress: String, controlPointData: ByteArray) {
        try {
            logi("sendControlPoint: Sending control point to $deviceAddress")

            // Get Bluetooth adapter
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            val device: BluetoothDevice? = try {
                adapter?.getRemoteDevice(deviceAddress)
            } catch (e: IllegalArgumentException) {
                loge("sendControlPoint: Invalid device address $deviceAddress", e)
                null
            }

            if (device == null) {
                loge("sendControlPoint: Device not found: $deviceAddress")
                return
            }

            // Check BLUETOOTH_CONNECT permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                loge("sendControlPoint: BLUETOOTH_CONNECT permission not granted")
                return
            }

            // Connect to device
            val gatt = connectGattSuspend(device)
            bluetoothGatt = gatt

            // Find BASS service
            val service: BluetoothGattService? = gatt.getService(BASS_SERVICE_UUID)
            if (service == null) {
                loge("sendControlPoint: BASS service not found on $deviceAddress")
                disconnect()
                return
            }

            // Find Control Point characteristic
            val controlPointChar: BluetoothGattCharacteristic? =
                service.getCharacteristic(BASS_CONTROL_POINT_UUID)
            if (controlPointChar == null) {
                loge("sendControlPoint: BASS Control Point characteristic not found on $deviceAddress")
                disconnect()
                return
            }

            // Write command to Control Point
            writeCharacteristicSuspend(gatt, controlPointChar, controlPointData)
            logi("sendControlPoint: Control Point write completed for $deviceAddress")

            // Always disconnect
            disconnect()
        } catch (e: Exception) {
            loge("sendControlPoint: Unexpected error", e)
            disconnect()
        }
    }

    /**
     * Connects to a Bluetooth device using GATT and waits until services are discovered.
     *
     * @param device The Bluetooth device to connect to.
     * @return A [BluetoothGatt] object if successful.
     *
     * Flow:
     * - Initiates GATT connection.
     * - Waits for `onConnectionStateChange`.
     * - Triggers service discovery and waits for completion.
     * - Returns connected [BluetoothGatt] or throws an exception on failure.
     */
    private suspend fun connectGattSuspend(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {

                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    try {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            loge("connectGattSuspend.onConnectionStateChange: GATT error $status")
                            cont.resumeWithException(RuntimeException("GATT error $status"))
                            return
                        }

                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            logi("connectGattSuspend.onConnectionStateChange: Connected to ${gatt.device.address}")

                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                try {
                                    logd("connectGattSuspend.onConnectionStateChange: Discovering services")
                                    gatt.discoverServices()
                                } catch (se: SecurityException) {
                                    loge("connectGattSuspend.onConnectionStateChange: discoverServices failed", se)
                                    cont.resumeWithException(se)
                                }
                            } else {
                                cont.resumeWithException(SecurityException("BLUETOOTH_CONNECT permission not granted"))
                            }
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            logw("connectGattSuspend.onConnectionStateChange: Disconnected from ${gatt.device.address}")
                        }
                    } catch (e: Exception) {
                        loge("connectGattSuspend.onConnectionStateChange: Unexpected error", e)
                        cont.resumeWithException(e)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    try {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            logi("connectGattSuspend.onServicesDiscovered: Services discovered for ${gatt.device.address}")
                            cont.resume(gatt)
                        } else {
                            loge("connectGattSuspend.onServicesDiscovered: Service discovery failed: $status")
                            cont.resumeWithException(RuntimeException("Service discovery failed: $status"))
                        }
                    } catch (e: Exception) {
                        loge("connectGattSuspend.onServicesDiscovered: Unexpected error", e)
                        cont.resumeWithException(e)
                    }
                }
            }

            try {
                logd("connectGattSuspend: Initiating GATT connection to ${device.address}")
                device.connectGatt(context, false, callback)
            } catch (se: SecurityException) {
                loge("connectGattSuspend: SecurityException during connectGatt", se)
                cont.resumeWithException(se)
            } catch (e: Exception) {
                loge("connectGattSuspend: Unexpected error during connectGatt", e)
                cont.resumeWithException(e)
            }
        }

    /**
     * Writes data to a characteristic and suspends until the operation is initiated.
     *
     * @param gatt The active [BluetoothGatt] instance.
     * @param characteristic The characteristic to write to.
     * @param value The data to be written.
     *
     * Notes:
     * - Currently resumes immediately after initiation.
     * - Does not wait for `onCharacteristicWrite` callback.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun writeCharacteristicSuspend(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) = suspendCancellableCoroutine { cont ->
        try {
            logd("writeCharacteristicSuspend: Writing to characteristic ${characteristic.uuid}")
            val result = gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            logi("writeCharacteristicSuspend: Write initiated, result=$result")

            if (result == BluetoothGatt.GATT_SUCCESS) {
                cont.resume(Unit)
            } else {
                cont.resumeWithException(RuntimeException("writeCharacteristic initiation failed: $result"))
            }
        } catch (se: SecurityException) {
            loge("writeCharacteristicSuspend: SecurityException during write", se)
            cont.resumeWithException(se)
        } catch (e: Exception) {
            loge("writeCharacteristicSuspend: Unexpected error during write", e)
            cont.resumeWithException(e)
        }
    }

    /**
     * Disconnects from the current GATT connection and closes resources.
     *
     * Ensures:
     * - BLUETOOTH_CONNECT permission is checked before close().
     * - Any SecurityException or unexpected errors are logged.
     * - [bluetoothGatt] reference is always cleared.
     */
    fun disconnect() {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    bluetoothGatt?.close()
                    logi("disconnect: Closed GATT connection")
                } catch (se: SecurityException) {
                    loge("disconnect: SecurityException on bluetoothGatt.close()", se)
                }
            } else {
                logw("disconnect: BLUETOOTH_CONNECT permission not granted, skipping close()")
            }
        } catch (e: Exception) {
            loge("disconnect: Unexpected error during disconnect", e)
        } finally {
            bluetoothGatt = null
        }
    }
}
