package com.bringyour.network.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.bringyour.network.R
import java.io.File

@Composable
fun ShareLogFileButton(logDir: String) {
    val logFile = remember {
        File(logDir).listFiles()
            ?.filter { it.name.contains(".log.INFO") }
            ?.maxByOrNull { it.lastModified() }
    }

    if (logFile != null) {
        ShareLogFileButton(
            logFile = logFile
        )
    } else {
        Text(stringResource(id = R.string.no_log_files_found))
    }
}

@Composable
private fun ShareLogFileButton(logFile: File) {
    val context = LocalContext.current

    TextButton(onClick = {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Log File"))
    }) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = stringResource(id = R.string.share_logs)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            stringResource(id = R.string.share_logs),
        )
    }
}