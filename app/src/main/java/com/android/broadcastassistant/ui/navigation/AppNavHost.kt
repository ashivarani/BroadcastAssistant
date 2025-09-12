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
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * The main navigation host for the Auracast app.
 *
 * Responsibilities:
 * - Displays the Auracast device list ([AuracastScreen]) as the start destination.
 * - Navigates to BIS selection screen ([BisChannelScreen]) for a selected device.
 * - Observes state from [AuracastViewModel] including devices, scanning status, permissions, and status messages.
 *
 * @param viewModel The [AuracastViewModel] providing state for devices and scanning.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState(initial = false)
    val permissionsGranted by viewModel.permissionsGranted.collectAsState(initial = false)
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    NavHost(navController = navController, startDestination = "auracast") {

        // Main Auracast device list
        composable("auracast") {
            AuracastScreen(
                devices = devices,
                isScanning = isScanning,
                permissionsGranted = permissionsGranted,
                statusMessage = statusMessage,
                onToggleScan = { viewModel.toggleScan() },
                onDeviceClick = { device ->
                    navController.navigate("bis/${device.address}")
                }
            )
        }

        // BIS Channel Screen for a selected device
        composable(
            "bis/{deviceAddress}",
            arguments = listOf(navArgument("deviceAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("deviceAddress")
            val device: AuracastDevice? = devices.find { it.address == address }

            device?.let {
                BisChannelScreen(
                    device = it,
                    onBack = { navController.popBackStack() }
                )
            } ?: run {
                loge("AppNavHost: Device not found for address=$address")
            }
        }
    }
}
