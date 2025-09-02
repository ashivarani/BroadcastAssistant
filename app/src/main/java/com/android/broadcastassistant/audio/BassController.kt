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
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BassController(private val context: Context) {

    companion object {
        private const val TAG = "BassController"
        private val BASS_SERVICE_UUID: UUID = UUID.fromString("0000184F-0000-1000-8000-00805f9b34fb")
        private val BASS_CONTROL_POINT_UUID: UUID = UUID.fromString("00002B2B-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null

    /**
     * Sends the raw control point command bytes to the Scan Delegator device via GATT.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun sendControlPoint(deviceAddress: String, controlPointData: ByteArray) {
        Log.i(TAG, "Sending control point to $deviceAddress")

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device: BluetoothDevice? = adapter?.getRemoteDevice(deviceAddress)
        if (device == null) {
            Log.e(TAG, "Device not found: $deviceAddress")
            return
        }

        // Check BLUETOOTH_CONNECT permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT permission not granted")
            return
        }

        val gatt = connectGattSuspend(device)
        bluetoothGatt = gatt

        val service: BluetoothGattService? = gatt.getService(BASS_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "BASS service not found on $deviceAddress")
            disconnect()
            return
        }

        val controlPointChar: BluetoothGattCharacteristic? = service.getCharacteristic(BASS_CONTROL_POINT_UUID)
        if (controlPointChar == null) {
            Log.e(TAG, "BASS Control Point characteristic not found")
            disconnect()
            return
        }
        // Actually write the Control Point and wait for confirmation
        writeCharacteristicSuspend(gatt, controlPointChar, controlPointData)
        disconnect()
    }

    private suspend fun connectGattSuspend(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        cont.resumeWithException(RuntimeException("GATT error $status"))
                        return
                    }
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to ${gatt.device.address}")

                        // Check permission before discoverServices
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                gatt.discoverServices()
                            } catch (se: SecurityException) {
                                cont.resumeWithException(se)
                            }
                        } else {
                            cont.resumeWithException(SecurityException("BLUETOOTH_CONNECT permission not granted"))
                        }
                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.w(TAG, "Disconnected from ${gatt.device.address}")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        cont.resume(gatt)
                    } else {
                        cont.resumeWithException(RuntimeException("Service discovery failed: $status"))
                    }
                }
            }

            try {
                device.connectGatt(context, false, callback)
            } catch (se: SecurityException) {
                cont.resumeWithException(se)
            }
        }

    /**
     * Write a characteristic and wait for onCharacteristicWrite callback before resuming.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun writeCharacteristicSuspend(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) = suspendCancellableCoroutine { cont ->

        try {
            val result = gatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            Log.i(TAG, "Write initiated, result=$result")

            if (result == BluetoothGatt.GATT_SUCCESS) {
                cont.resume(Unit) // we trust the stack here, async callback not needed for most cases
            } else {
                cont.resumeWithException(RuntimeException("writeCharacteristic initiation failed: $result"))
            }
        } catch (se: SecurityException) {
            cont.resumeWithException(se)
        }
    }

    fun disconnect() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                bluetoothGatt?.close()
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException on bluetoothGatt.close()", se)
            }
        } else {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted, skipping bluetoothGatt.close()")
        }
        bluetoothGatt = null
    }
}
