package com.android.broadcastassistant.ui.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.broadcastassistant.ui.screen.AuracastScreen
import com.android.broadcastassistant.ui.screen.LanguageSelectionScreen
import com.android.broadcastassistant.viewmodel.AuracastViewModel
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw

/**
 * Navigation host for the Broadcast Assistant app.
 *
 * ## Responsibilities
 * - Defines navigation graph using Jetpack Compose Navigation.
 * - Hosts the main **AuracastScreen** (device list, scanning, etc).
 * - Hosts the **LanguageSelectionScreen** for BIS (language/stream) selection.
 *
 * ## Usage
 * Place this composable at the root of your app (e.g., in `MainActivity.setContent`).
 *
 * Example:
 * ```
 * setContent {
 *     AppNavHost()
 * }
 * ```
 *
 * @param viewModel Shared [AuracastViewModel] injected into screens.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auracast") {

        /**
         * Route: "auracast"
         * - Displays the main Auracast screen with device list and scanning controls.
         */
        composable("auracast") {
            logd("AppNavHost: Navigating to AuracastScreen")
            AuracastScreen(viewModel = viewModel)
            logi("AppNavHost: AuracastScreen rendered")
        }

        /**
         * Route: "language_selection/{address}"
         * - Displays the BIS (language/stream) selection screen for a specific device.
         */
        composable(
            "language_selection/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address")
            if (address == null) {
                logw("AppNavHost: Missing address argument in navigation")
                return@composable
            }

            logd("AppNavHost: Navigating to LanguageSelectionScreen for $address")

            // Collect latest device list from ViewModel state
            val device = viewModel.devices.collectAsState().value.find { it.address == address }
            if (device != null) {
                LanguageSelectionScreen(
                    device = device,
                    onBisSelected = { bisIndex ->
                        try {
                            logd("AppNavHost: onBisSelected → BIS=$bisIndex for ${device.address}")
                            viewModel.selectBisChannel(device, bisIndex)
                            logi("AppNavHost: BIS selection processed for ${device.address}")
                        } catch (e: Exception) {
                            loge("AppNavHost: Failed to handle BIS selection for ${device.address}", e)
                        }
                    },
                    onBack = {
                        try {
                            logd("AppNavHost: onBack → Returning to previous screen")
                            navController.popBackStack()
                            logi("AppNavHost: Navigation back successful")
                        } catch (e: Exception) {
                            loge("AppNavHost: Failed to navigate back", e)
                        }
                    }
                )
                logi("AppNavHost: LanguageSelectionScreen rendered for $address")
            } else {
                logw("AppNavHost: Device with address=$address not found in ViewModel state")
            }
        }
    }
}
