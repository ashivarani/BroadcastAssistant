package com.android.broadcastassistant.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper object for managing runtime permissions required for BLE operations.
 *
 * Responsibilities:
 * - Dynamically determine required permissions based on Android version.
 * - Provide the list of required permissions for runtime requests.
 * - Check whether all required BLE-related permissions have been granted.
 * - Logs key actions for debugging and error tracking.
 */
object PermissionHelper {

    /**
     * Dynamically builds the list of required BLE-related permissions for the current device.
     *
     * @return Array of permission strings required for BLE operations.
     */
    private val blePermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>() // Temporary list to collect permissions
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                        // Android 13+ requires scan, connect, and nearby Wi-Fi permissions
                        permissions += Manifest.permission.BLUETOOTH_SCAN
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
                        logv("BLE permissions for Android 13+ selected:", permissions)
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        // Android 12+ requires scan and connect permissions
                        permissions += Manifest.permission.BLUETOOTH_SCAN
                        permissions += Manifest.permission.BLUETOOTH_CONNECT
                        logd("BLE permissions for Android 12+ selected:", permissions)
                    }
                    else -> {
                        // Pre-Android 12 requires location permission
                        permissions += Manifest.permission.ACCESS_FINE_LOCATION
                        logd("BLE permissions for legacy Android selected:", permissions)
                    }
                }
            } catch (e: Exception) {
                loge("Failed to build BLE permissions", e)
            }
            return permissions.toTypedArray() // Convert mutable list to array
        }

    /**
     * Returns all required permissions for BLE operations on this device.
     *
     * @return Array of BLE-related permission strings.
     */
    fun getRequiredPermissions(): Array<String> {
        return try {
            val perms = blePermissions
            logd("getRequiredPermissions executed successfully:", perms.joinToString())
            perms
        } catch (e: Exception) {
            loge("Failed to retrieve required BLE permissions", e)
            emptyArray() // Return empty array if error occurs
        }
    }

    /**
     * Checks if all required BLE permissions are currently granted.
     *
     * @param context Context to check permissions against (Application or Activity context)
     * @return true if all required BLE permissions are granted, false otherwise
     */
    fun hasBlePermissions(context: Context): Boolean {
        return try {
            // Check each permission individually
            val allGranted = blePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                logd("All required BLE permissions are granted")
            } else {
                // Collect the permissions that are missing
                val missing = blePermissions.filter {
                    ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                }
                logw("Missing BLE permissions:", missing)
            }

            allGranted // Return result
        } catch (e: Exception) {
            loge("Error while checking BLE permissions", e)
            false
        }
    }
}
