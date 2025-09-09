package com.android.broadcastassistant.data

import androidx.compose.runtime.Immutable

/**
 * Represents a log entry for a command executed via BASS or BIS selection.
 *
 * @property message The log message describing the command or action.
 * @property timestamp Epoch milliseconds when the log was created. Defaults to current time.
 */
@Immutable
data class CommandLog(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
