package com.bringyour.network.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The end of one process's log (Sdk.logTails), copied off the gomobile object. */
data class LogTailUi(
    // the process that wrote the log
    val source: String,
    // the log file's name
    val name: String,
    // the last lines, oldest first
    val lines: List<String>,
    // true when the log has lines before these
    val truncated: Boolean,
)

/**
 * Settings > View Logs: the last lines of the app's logs, read-only
 * (https://github.com/urnetwork/android/issues/471). The sdk reads them
 * (Sdk.logTails): the last 500 lines of the newest INFO log, the file glog
 * writes every line to, after a flush so the newest lines are in. They are
 * read once, when the screen opens, off the main thread, and copied into
 * kotlin values so the list does not cross the gomobile bridge per row.
 */
@HiltViewModel
class LogViewerViewModel @Inject constructor() : ViewModel() {

    // null while loading
    private val _logTails = MutableStateFlow<List<LogTailUi>?>(null)
    val logTails: StateFlow<List<LogTailUi>?> = _logTails

    init {
        viewModelScope.launch {
            _logTails.value = withContext(Dispatchers.IO) {
                readLogTails()
            }
        }
    }

    private fun readLogTails(): List<LogTailUi> {
        val list = Sdk.logTails() ?: return listOf()
        val logTails = ArrayList<LogTailUi>()
        for (i in 0 until list.len()) {
            val logTail = list.get(i) ?: continue
            val lines = logTail.lines
            logTails.add(
                LogTailUi(
                    source = logTail.source.orEmpty(),
                    name = logTail.name.orEmpty(),
                    lines = if (lines == null) listOf() else (0 until lines.len()).map { lines.get(it).orEmpty() },
                    truncated = logTail.truncated,
                )
            )
        }
        return logTails
    }
}

@Composable
fun LogViewerScreen(
    navController: NavController,
    viewModel: LogViewerViewModel = hiltViewModel(),
) {
    val logTails by viewModel.logTails.collectAsState()
    LogViewerScreen(
        logTails = logTails,
        onBack = { navController.popBackStack() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerScreen(
    logTails: List<LogTailUi>?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.view_logs),
                        style = TopBarTitleTextStyle,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(id = R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black,
    ) { innerPadding ->
        if (logTails == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TextMuted)
            }
        } else if (logTails.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(id = R.string.no_log_files_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }
        } else {
            LogTailLines(
                logTails = logTails,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private val logLineStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    lineHeight = 15.sp,
)

/**
 * Each log under its process and file name, oldest line first, opened at the
 * newest line, the last. A tail that leaves out the log's earlier lines starts
 * with an ellipsis.
 */
@Composable
private fun LogTailLines(
    logTails: List<LogTailUi>,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // a header per log, an ellipsis per truncated log, and the lines
    val itemCount = remember(logTails) {
        var count = 0
        for (logTail in logTails) {
            count += 1 + (if (logTail.truncated) 1 else 0) + logTail.lines.size
        }
        count
    }
    LaunchedEffect(logTails) {
        if (0 < itemCount) {
            listState.scrollToItem(itemCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .tabletReadableColumn()
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        logTails.forEachIndexed { tailIndex, logTail ->
            item(key = "header:$tailIndex") {
                Text(
                    "${logTail.source}/${logTail.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(top = if (tailIndex == 0) 0.dp else 16.dp, bottom = 4.dp),
                )
            }
            if (logTail.truncated) {
                item(key = "truncated:$tailIndex") {
                    Text("…", style = logLineStyle, color = TextMuted)
                }
            }
            itemsIndexed(
                logTail.lines,
                key = { lineIndex, _ -> "line:$tailIndex:$lineIndex" }
            ) { _, line ->
                Text(line, style = logLineStyle, color = Color.White)
            }
        }
    }
}
