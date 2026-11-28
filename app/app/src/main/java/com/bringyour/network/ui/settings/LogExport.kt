package com.bringyour.network.ui.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_EXPORT_TAG = "LogExport"

private const val LOG_EXPORT_PREFIX = "urnetwork-logs-"

/** The folder exports are written to, under cacheDir/share (res/xml/file_paths.xml). */
fun logExportDir(cacheDir: File): File = File(File(cacheDir, "share"), "logs")

/**
 * An export's file name, stamped in UTC like the developer bundle's
 * (diagnosticBundleFileName), so names sort in time across devices and zones.
 */
fun logExportFileName(millis: Long): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(millis))
    return "$LOG_EXPORT_PREFIX$stamp.zip"
}

/**
 * Empties [dir] of earlier exports and whatever an export cut short left, and
 * makes sure it exists. Nothing outside [dir] is touched: cacheDir/share holds
 * other features' payloads.
 */
fun prepareLogExportDir(dir: File) {
    dir.listFiles()?.forEach { file -> file.deleteRecursively() }
    dir.mkdirs()
}

/** Why an export has nothing to share. */
enum class LogExportNotice(val messageId: Int) {
    NoLogs(R.string.no_log_files_found),
    Failed(R.string.something_went_wrong),
}

/** What one export left: a zip to share, or why there is none. */
private data class LogExportOutcome(val file: File?, val notice: LogExportNotice?)

/**
 * Settings > Export Logs: the logs "send feedback with logs" uploads, as a zip
 * handed to the share sheet (https://github.com/urnetwork/android/issues/471).
 *
 * The zip is the upload's own (Sdk.exportUploadLogs): the same files, the
 * newest glog files that fit the upload's cap with the exit diagnostics and
 * crash files first, written by the sdk's upload writer after the same glog
 * flush. Android runs the tunnel in the app's process, so these are the app's
 * and the tunnel's logs together. Like the upload, it holds the logs as they
 * were written. The developer bundle (DeveloperViewModel) is the export with a
 * choice of files, a manifest and redaction.
 *
 * The zip is a handoff to the share sheet, never storage: each export empties
 * its folder first (logExportDir), which also collects a zip an export cut
 * short left behind.
 *
 * The export runs off the main thread, and the zip and the notice come back as
 * state for the screen ([LogExportEffects]): a completion lambda from the
 * screen would capture its Activity, which an export outliving a rotation
 * would then start the share sheet from.
 */
@HiltViewModel
class LogExportViewModel @Inject constructor() : ViewModel() {

    /** True while an export runs. A tap meanwhile is dropped: two exports would write one folder. */
    var exporting by mutableStateOf(false)
        private set

    /** A zip waiting for the share sheet, cleared by [consumePendingShare]. */
    var pendingShare by mutableStateOf<File?>(null)
        private set

    /** Why the last export has nothing to share, cleared by [consumePendingNotice]. */
    var pendingNotice by mutableStateOf<LogExportNotice?>(null)
        private set

    fun export(cacheDir: File, nowMillis: Long) {
        if (exporting) {
            return
        }
        exporting = true
        viewModelScope.launch {
            try {
                val outcome = withContext(Dispatchers.IO) {
                    try {
                        val dir = logExportDir(cacheDir)
                        prepareLogExportDir(dir)
                        val dest = File(dir, logExportFileName(nowMillis))
                        val result = Sdk.exportUploadLogs(dest.absolutePath)
                        // FileCount is a Go int, which gobind binds as a java long
                        if (0L < result.fileCount) {
                            LogExportOutcome(dest, null)
                        } else {
                            LogExportOutcome(null, LogExportNotice.NoLogs)
                        }
                    } catch (e: Exception) {
                        Log.i(LOG_EXPORT_TAG, "export logs failed: ${e.message}")
                        LogExportOutcome(null, LogExportNotice.Failed)
                    }
                }
                pendingShare = outcome.file
                pendingNotice = outcome.notice
            } finally {
                exporting = false
            }
        }
    }

    fun consumePendingShare() {
        pendingShare = null
    }

    fun consumePendingNotice() {
        pendingNotice = null
    }
}

/**
 * Hands a finished export to the share sheet, and shows why an export has
 * nothing to share, with the current context.
 */
@Composable
fun LogExportEffects(
    logExportViewModel: LogExportViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    // the snackbar outlives the notice it shows: clearing the notice must not
    // cancel it
    val scope = rememberCoroutineScope()

    val pendingShare = logExportViewModel.pendingShare
    LaunchedEffect(pendingShare) {
        pendingShare?.let { file ->
            shareLogExport(context, file)
            logExportViewModel.consumePendingShare()
        }
    }

    val pendingNotice = logExportViewModel.pendingNotice
    LaunchedEffect(pendingNotice) {
        pendingNotice?.let { notice ->
            val message = context.getString(notice.messageId)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
            logExportViewModel.consumePendingNotice()
        }
    }
}

/** The share sheet for one export, as the developer bundle is shared (DeveloperScreen). */
private fun shareLogExport(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.export_logs)),
    )
}
