package com.android.broadcastassistant.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.android.broadcastassistant.ui.screen.*
import com.android.broadcastassistant.util.*
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * App navigation host for the Auracast Assistant application.
 *
 * Manages navigation between the main Auracast device list screen and
 * the language/BIS selection screen. Integrates with the [AuracastViewModel]
 * to provide device state, scan state, and handle BIS selection.
 *
 * @param viewModel The AuracastViewModel providing state and actions for the screens
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    // Collect state from the ViewModel
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    val isScanning by viewModel.isScanning.collectAsState(initial = false)
    val permissionsGranted by viewModel.permissionsGranted.collectAsState(initial = false)
    val statusMessage by viewModel.statusMessage.collectAsState(initial = "")

    NavHost(navController = navController, startDestination = "auracast") {

        // Auracast screen displaying device list
        composable("auracast") {
            AuracastScreen(
                devices = devices,
                isScanning = isScanning,
                permissionsGranted = permissionsGranted,
                statusMessage = statusMessage,
                onToggleScan = {
                    logd("AppNavHost: Scan toggle pressed")
                    viewModel.toggleScan()
                },
                onDeviceClick = { device ->
                    logd("AppNavHost: Navigating to language selection for ${device.address}")
                    navController.navigate("language_selection/${device.address}")
                }
            )
        }

        // Bis Channel Screen for a selected device
        composable(
            "language_selection/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address")

            // Safe lookup for the device by address
            val device = devices.find { it.address == address }
            if (device != null) {
                BisChannelScreen(
                    device = device,
                    onBisSelected = { bisIndex ->
                        try {
                            logd("AppNavHost: BIS selected for ${device.address} → index=$bisIndex")
                            viewModel.selectBisChannel(device, bisIndex)
                        } catch (e: Exception) {
                            loge("AppNavHost: Error selecting BIS for ${device.address}", e)
                        }
                    },
                    onBack = {
                        logd("AppNavHost: Back pressed from language selection")
                        navController.popBackStack()
                    }
                )
            } else {
                loge("AppNavHost: Device not found for address=$address")
            }
        }
    }
}