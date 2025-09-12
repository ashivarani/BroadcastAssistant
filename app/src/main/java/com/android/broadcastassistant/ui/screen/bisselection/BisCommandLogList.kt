package com.android.broadcastassistant.ui.screen.bisselection

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.broadcastassistant.data.CommandLog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Displays a vertical list of command logs for BIS operations.
 * Each log entry shows a timestamp and message, formatted in a monospace font.
 *
 * @param commandLogs List of [CommandLog] to display. Empty list renders nothing.
 */
@SuppressLint("SimpleDateFormat")
@Composable
fun BisCommandLogList(commandLogs: List<CommandLog>) {
    if (commandLogs.isEmpty()) return // Early return if no logs

    Column {
        // Header
        Text(
            "Command Logs",
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Iterate over each command log and display with timestamp
        commandLogs.forEach { log ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black) // Log background
                    .padding(vertical = 2.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = "[${SimpleDateFormat("HH:mm:ss").format(Date(log.timestamp))}] ${log.message}",
                    fontSize = 12.sp,
                    color = Color.Green, // Log text color
                    fontFamily = FontFamily.Monospace // Monospace for clarity
                )
            }
        }
    }
}
