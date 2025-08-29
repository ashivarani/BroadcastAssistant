package com.android.broadcastassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.android.broadcastassistant.ui.navigation.AppNavHost
import com.android.broadcastassistant.ui.theme.BroadcastAssistantTheme
import com.android.broadcastassistant.util.PermissionHelper
import com.android.broadcastassistant.viewmodel.AuracastViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AuracastViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Check again after user interaction
            val granted = PermissionHelper.hasBlePermissions(this)
            viewModel.updatePermissionsGranted(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Bluetooth permissions if not granted
        if (!PermissionHelper.hasBlePermissions(this)) {
            requestPermissionsLauncher.launch(PermissionHelper.getRequiredPermissions())
        } else {
            viewModel.updatePermissionsGranted(true)
        }

        setContent {
            BroadcastAssistantTheme {
                AppNavHost(viewModel = viewModel)
            }
        }
    }
}
