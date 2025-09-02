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
import com.android.broadcastassistant.ui.AuracastScreen
import com.android.broadcastassistant.ui.screens.LanguageSelectionScreen
import com.android.broadcastassistant.viewmodel.AuracastViewModel

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun AppNavHost(viewModel: AuracastViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auracast") {

        // Auracast main screen
        composable("auracast") {
            AuracastScreen(
                viewModel = viewModel
            )
        }

        // Language / BIS Selection
        composable(
            "language_selection/{address}",
            arguments = listOf(navArgument("address") { type = NavType.StringType })
        ) { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address") ?: return@composable
            val device = viewModel.devices.collectAsState().value.find { it.address == address }
            if (device != null) {
                LanguageSelectionScreen(
                    device = device,
                    onBisSelected = { bisIndex ->
                        viewModel.selectBisChannel(device, bisIndex)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
