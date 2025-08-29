package com.android.broadcastassistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {

    /**
     * Dynamically builds the list of required BLE-related permissions
     * depending on the Android version.
     */
    private val blePermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>()

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    // Android 13+ requires Nearby Wi-Fi Devices for Wi-Fi-based discovery
                    permissions += Manifest.permission.BLUETOOTH_SCAN
                    permissions += Manifest.permission.BLUETOOTH_CONNECT
                    permissions += Manifest.permission.NEARBY_WIFI_DEVICES
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+ requires explicit scan/connect permissions
                    permissions += Manifest.permission.BLUETOOTH_SCAN
                    permissions += Manifest.permission.BLUETOOTH_CONNECT
                }
                else -> {
                    // Pre-Android 12 still needs location for BLE scanning
                    permissions += Manifest.permission.ACCESS_FINE_LOCATION
                }
            }

            return permissions.toTypedArray()
        }

    /**
     * Returns all required permissions for BLE operations on this device.
     */
    fun getRequiredPermissions(): Array<String> = blePermissions

    /**
     * Checks if all required BLE permissions are currently granted.
     */
    fun hasBlePermissions(context: Context): Boolean {
        return blePermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}
