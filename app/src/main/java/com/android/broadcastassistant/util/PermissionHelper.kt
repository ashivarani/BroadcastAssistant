package com.android.broadcastassistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper object for managing runtime BLE (Bluetooth Low Energy) permissions.
 *
 * Responsibilities:
 * - Determine required BLE permissions dynamically based on Android version.
 * - Check if permissions are granted.
 */
object PermissionHelper {

    /**
     * Dynamically returns the list of required BLE permissions based on Android version.
     *
     * - Android 13+ (TIRAMISU): BLUETOOTH_SCAN, BLUETOOTH_CONNECT, NEARBY_WIFI_DEVICES
     * - Android 12+ (S): BLUETOOTH_SCAN, BLUETOOTH_CONNECT
     * - Below Android 12: ACCESS_FINE_LOCATION (required for BLE scanning)
     */
    private val blePermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>()
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        permissions += Manifest.permission.BLUETOOTH_SCAN
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        permissions += Manifest.permission.BLUETOOTH_SCAN
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                    }
                    else -> {
                        // Older Android versions require location for BLE scanning
                        permissions += Manifest.permission.ACCESS_FINE_LOCATION
                    }
                }
            } catch (_: Exception) {
                // Ignore exceptions; return empty or partial list
            }
            return permissions.toTypedArray()
        }

    /**
     * Public function to get the required BLE permissions for the current Android version.
     *
     * @return Array of permission strings
     */
    fun getRequiredPermissions(): Array<String> = blePermissions

    /**
     * Checks if all required BLE permissions are granted in the given [context].
     *
     * @param context Android context
     * @return `true` if all required permissions are granted, `false` otherwise
     */
    fun hasBlePermissions(context: Context): Boolean {
        return try {
            blePermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Exception) {
            false
        }
    }
}
