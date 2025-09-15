package com.android.broadcastassistant.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.android.broadcastassistant.ui.screen.auracast.AuracastScreen
import com.android.broadcastassistant.ui.screen.bisselection.BisChannelScreen
import com.android.broadcastassistant.data.AuracastDevice
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * Main navigation host for the Auracast app.
 *
 * Responsibilities:
 * - Display list of Auracast devices (broadcasters and receivers) on [AuracastScreen].
 * - Navigate to BIS selection screen ([BisChannelScreen]) for selected broadcasters.
 * - Observe state from [AuracastViewModel] for devices, scanning status, permissions, and status messages.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    // Collect state from ViewModel
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState(initial = false)
    val permissionsGranted by viewModel.permissionsGranted.collectAsState(initial = false)
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    // Split devices into broadcasters (have broadcastId) and receivers (no broadcastId)
    val broadcasters = devices.filter { it.broadcastId != null }
    val receivers = devices.filter { it.broadcastId == null }

    NavHost(navController = navController, startDestination = "auracast") {

        // Main Auracast device list screen
        composable("auracast") {
            AuracastScreen(
                broadcasters = broadcasters,
                receivers = receivers,
                isScanning = isScanning,
                permissionsGranted = permissionsGranted,
                statusMessage = statusMessage,
                onToggleScan = { viewModel.toggleScan() },
                onDeviceClick = { device ->
                    // Navigate to BIS selection screen only for broadcasters
                    if (device.broadcastId != null) {
                        navController.navigate("bis/${device.address}")
                    } else {
                        // Optional: handle receiver click (e.g., show info toast or log)
                        logi("Clicked receiver device: ${device.address}")
                    }
                }
            )
        }

        // BIS Channel Screen for a selected broadcaster device
        composable(
            "bis/{deviceAddress}",
            arguments = listOf(navArgument("deviceAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("deviceAddress")

            // Find device from current ViewModel state
            val device: AuracastDevice? = devices.find { it.address == address }

            device?.let {
                BisChannelScreen(
                    device = it,
                    onBack = { navController.popBackStack() } // Navigate back to AuracastScreen
                )
            } ?: run {
                loge("AppNavHost: Device not found for address=$address")
            }
        }
    }
}
