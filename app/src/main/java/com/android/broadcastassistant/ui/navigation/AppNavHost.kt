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
 * - Display broadcasters and receivers on [AuracastScreen].
 * - Navigate to BIS selection screen ([BisChannelScreen]) for broadcasters.
 * - Fetch selected devices from merged device list.
 *
 * @param viewModel [AuracastViewModel] providing device states.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    // Collect device flows
    val broadcasters by viewModel.broadcasters.collectAsState(initial = emptyList())
    val receivers by viewModel.receivers.collectAsState(initial = emptyList())

    // Merge devices for BIS lookup
    val allDevices = broadcasters + receivers

    NavHost(navController = navController, startDestination = "auracast") {

        // Main device list screen
        composable("auracast") {
            AuracastScreen(
                viewModel = viewModel,
                onDeviceClick = { device ->
                    // Navigate only if device is a broadcaster
                    if (device.broadcastId != null) {
                        logi("Navigating to BIS screen for device ${device.address}")
                        navController.navigate("bis/${device.address}")
                    } else {
                        logi("Receiver clicked (no navigation): ${device.address}")
                    }
                }
            )
        }

        // BIS selection screen
        composable(
            "bis/{deviceAddress}",
            arguments = listOf(navArgument("deviceAddress") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("deviceAddress")
            val device: AuracastDevice? = allDevices.find { it.address == address }

            if (device != null) {
                logi("Displaying BIS screen for ${device.name} (${device.address})")
                BisChannelScreen(
                    device = device,
                    onBack = { navController.popBackStack() }
                )
            } else {
                loge("BIS screen: Device not found for address=$address")
            }
        }
    }
}
