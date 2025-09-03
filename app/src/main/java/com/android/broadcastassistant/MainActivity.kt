package com.android.broadcastassistant

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import com.android.broadcastassistant.ui.navigation.AppNavHost
import com.android.broadcastassistant.ui.theme.BroadcastAssistantTheme
import com.android.broadcastassistant.util.PermissionHelper
import com.android.broadcastassistant.util.logd
import com.android.broadcastassistant.util.logi
import com.android.broadcastassistant.util.logw
import com.android.broadcastassistant.util.loge
import com.android.broadcastassistant.viewmodel.AuracastViewModel

/**
 * MainActivity for the Auracast Broadcast Assistant app.
 *
 * Responsibilities:
 * - Request necessary BLE permissions based on Android version.
 * - Initialize and provide [AuracastViewModel] to Composable screens.
 * - Launch the main navigation host [AppNavHost].
 *
 * Permissions:
 * - Dynamically requests BLE-related permissions (scan/connect, location, nearby Wi-Fi devices).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {

    /** ViewModel managing Auracast scan, devices, and BIS selection */
    private val viewModel: AuracastViewModel by viewModels()

    /**
     * Activity Result launcher for requesting multiple permissions.
     * After user interaction, checks if BLE permissions are granted and updates the ViewModel.
     */
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            try {
                // Check BLE permissions after user action
                val granted = PermissionHelper.hasBlePermissions(this)
                logd("Permission result received → granted=$granted")
                viewModel.updatePermissionsGranted(granted)
            } catch (e: Exception) {
                loge("Failed to handle permission result", e)
            }
        }

    /**
     * Called when the activity is starting.
     * Sets up the Compose UI, initializes ViewModel, and requests permissions if needed.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down,
     * this contains the data it most recently supplied.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logi("MainActivity: onCreate called")

        try {
            // Check if BLE permissions are already granted
            if (!PermissionHelper.hasBlePermissions(this)) {
                logw("MainActivity: BLE permissions not granted → requesting from user")
                // Launch permission request
                requestPermissionsLauncher.launch(PermissionHelper.getRequiredPermissions())
            } else {
                logd("MainActivity: BLE permissions already granted")
                viewModel.updatePermissionsGranted(true)
            }

            // Set Compose content
            setContent {
                BroadcastAssistantTheme {
                    // Launch navigation host with ViewModel
                    AppNavHost(viewModel = viewModel)
                }
            }
            logi("MainActivity: Compose UI set with AppNavHost")
        } catch (e: Exception) {
            loge("MainActivity: Failed during onCreate setup", e)
        }
    }
}
