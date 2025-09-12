package com.android.broadcastassistant.data

/**
 * Represents the result of a BIS (Broadcast Isochronous Stream) selection operation.
 * selecting BIS channels for a device succeeded or failed.
 */
sealed class BisSelectionResult {

    /**
     * Indicates a successful BIS selection.
     *
     * @property device The target [AuracastDevice] whose BIS selection succeeded.
     * @property selectedIndexes The list of BIS indexes that were successfully selected.
     */
    data class Success(
        val device: AuracastDevice,
        val selectedIndexes: List<Int>
    ) : BisSelectionResult()

    /**
     * Indicates a failure in BIS selection.
     *
     * @property device The target [AuracastDevice] for which the selection failed.
     * @property reason Explanation for the failure (e.g., missing sourceId, invalid BIS indexes).
     */
    data class Failure(
        val device: AuracastDevice,
        val reason: String
    ) : BisSelectionResult()
}
