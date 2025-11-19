package com.bringyour.network.ui.components

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bringyour.sdk.Sdk
import java.io.File

@Composable
fun ExportLogButton(logDir: String) {
    val logFile = remember {
        File(logDir).listFiles()
            ?.filter { it.name.contains(".log.INFO") }
            ?.maxByOrNull { it.lastModified() }
    }

    if (logFile != null) {
        ExportLogFileLauncher(
            logFilePath = logFile.path
        )
    } else {
        Text("No log file found")
    }
}

@Composable
private fun ExportLogFileLauncher(logFilePath: String) {
    val context = LocalContext.current

    val suggestedFileName = "urnetwork-log-${System.currentTimeMillis()}.txt"


    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {

            Sdk.flushGlog()

            val logFile = File(logFilePath)
            val logFileContent = logFile.readBytes()
            context.contentResolver.openOutputStream(it)?.use { output ->
                output.write(logFileContent)
            }
        }
    }

    TextButton(onClick = { saveLauncher.launch(suggestedFileName) }) {
        Icon(
            imageVector = Icons.Default.Save,
            contentDescription = "Save logs"
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Save logs")
    }
}