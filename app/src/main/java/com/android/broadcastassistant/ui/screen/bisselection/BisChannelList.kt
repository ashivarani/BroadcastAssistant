package com.android.broadcastassistant.ui.screen.bisselection

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.broadcastassistant.data.AuracastDevice

/**
 * Displays a vertical list of BIS channels for a given Auracast device.
 *
 * Each BIS channel is displayed as a [BisChannelCard], highlighting
 * selected channels and supporting multi-selection. Handles click
 * events to update the selected BIS indices.
 *
 * @param device The Auracast device containing BIS channels and selected indexes
 * @param switchingActive Whether a BIS switch animation/highlight is active (disables clicks)
 * @param onBisSelected Callback invoked when the BIS selection changes, returns updated list of selected indexes
 * @param modifier Optional [Modifier] for the column layout
 */
@Composable
fun BisChannelList(
    device: AuracastDevice,
    switchingActive: Boolean,
    onBisSelected: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Iterate through all BIS channels of the device
        device.bisChannels.forEach { bis ->
            // Check if this BIS is currently selected
            val isSelected = bis.index in device.selectedBisIndexes

            // Render a card for each BIS channel
            BisChannelCard(
                bis = bis,
                isSelected = isSelected,
                switchingActive = switchingActive,
                onClick = {
                    // Toggle selection for this BIS index
                    val updatedSelection = if (isSelected) {
                        device.selectedBisIndexes - bis.index
                    } else {
                        device.selectedBisIndexes + bis.index
                    }
                    // Emit a new immutable list to trigger recomposition
                    onBisSelected(updatedSelection.toList())
                }
            )
        }
    }
}
