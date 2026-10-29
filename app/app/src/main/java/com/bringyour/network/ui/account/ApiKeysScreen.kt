package com.bringyour.network.ui.account

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.PersistableBundle
import android.text.format.DateUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.FoldSafeBasicAlertDialog
import com.bringyour.network.ui.components.RowRemoveControl
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.apiKeyRemoveControls
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.Yellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Account -> API keys: the network's keys, newest first, each with its name,
 * creation date and id, under a short explanation that links to the api
 * reference (ur.io/docs/api). Create API key asks for a name, then shows the
 * new key once, with Copy key. A row's Delete (its button, a swipe left, or a
 * right click) asks first, naming the key. Pull to refresh.
 *
 * Visible while started: LifecycleStartEffect drives the model's visibility,
 * and each appearance loads the list again.
 *
 * The new key shows only in its dialog (ApiKeysCreatedDialog), which nothing
 * saves: the key is not in the saved state, preferences, files, logs or
 * analytics, and the dialog's Done drops it.
 */
@Composable
fun ApiKeysScreen(
    navController: NavController,
    viewModel: ApiKeysViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(true)
        onStopOrDispose {
            viewModel.setVisible(false)
        }
    }

    ApiKeysContent(
        ui = viewModel.ui,
        dialog = viewModel.dialog,
        text = rememberApiKeysText(),
        onBack = { navController.popBackStack() },
        onRefresh = viewModel::refresh,
        onCreate = viewModel::requestCreate,
        onCreateNamed = viewModel::create,
        onDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDialog = viewModel::dismissDialog,
    )
}

/** The platform text of the screen; recreated when the configuration (the locale) changes. */
@Composable
private fun rememberApiKeysText(): ApiKeysText {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { ContextApiKeysText(context) }
}

private class ContextApiKeysText(private val context: Context) : ApiKeysText {
    override fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    // the abbreviated date with its year ("Oct 29, 2026")
    override fun created(timeMillis: Long): String = DateUtils.formatDateTime(
        context,
        timeMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
    )

    override fun dateTime(timeMillis: Long): String = DateUtils.formatDateTime(
        context,
        timeMillis,
        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeysContent(
    ui: ApiKeysUi,
    dialog: ApiKeysDialog?,
    text: ApiKeysText,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onCreateNamed: (String) -> Unit,
    onDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDialog: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.api_keys_title),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = onRefresh,
            state = rememberPullToRefreshState(),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .tabletReadableColumn()
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "intro") {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Text(
                            stringResource(id = R.string.api_keys_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        // under the explanation, the api reference in the
                        // browser, as a full-size target
                        TextButton(
                            onClick = { uriHandler.openUri(API_KEYS_DOCS_URL) },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                        ) {
                            Text(
                                stringResource(id = R.string.api_keys_docs_link),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Pink
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ArrowOutward,
                                contentDescription = null,
                                tint = Pink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                item(key = "create") {
                    URButton(
                        onClick = onCreate,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.api_keys_create),
                            style = buttonTextStyle
                        )
                    }
                }

                // the rows stay; Retry, beside the pull, loads them again
                if (ui.refreshFailed) {
                    item(key = "refresh-failed") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(id = R.string.api_keys_load_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = Red,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRefresh) {
                                Text(stringResource(id = R.string.retry))
                            }
                        }
                    }
                }

                when (ui.body) {
                    ApiKeysBody.Progress -> item(key = "progress") {
                        val loadingLabel = stringResource(id = R.string.loading)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp)
                                .semantics { contentDescription = loadingLabel },
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = TextMuted,
                                trackColor = TextFaint,
                            )
                        }
                    }

                    ApiKeysBody.LoadFailed -> item(key = "load-failed") {
                        ApiKeysBodyMessage(ui.body) {
                            TextButton(onClick = onRefresh) {
                                Text(stringResource(id = R.string.retry))
                            }
                        }
                    }

                    ApiKeysBody.Empty -> item(key = "empty") {
                        ApiKeysBodyMessage(ui.body)
                    }

                    ApiKeysBody.Rows -> items(ui.rows, key = { it.key }) { row ->
                        Column(modifier = Modifier.animateItem()) {
                            ApiKeyRow(
                                row = row,
                                text = text,
                                onDelete = { onDelete(row.apiKeyId) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        is ApiKeysDialog.Create -> ApiKeysCreateDialog(
            form = dialog,
            onCreate = onCreateNamed,
            onDismiss = onDismissDialog,
        )

        is ApiKeysDialog.Created -> ApiKeysCreatedDialog(
            created = dialog,
            onDone = onDismissDialog,
        )

        is ApiKeysDialog.ConfirmDelete -> ApiKeysConfirmDeleteDialog(
            body = apiKeyDeleteConfirmBody(dialog.row, text),
            onDismiss = onDismissDialog,
            onConfirm = onConfirmDelete,
        )

        null -> {}
    }
}

/**
 * One key: its name (or Unnamed key), creation date and full id, and Delete.
 * Deleting has no other path, so the row shows a Delete button and the swipe
 * left is a shortcut to the same confirmation (RowRemoveControls). Assistive
 * tech reads the key as one node, with the full date and time, and then the
 * button.
 */
@Composable
private fun ApiKeyRow(
    row: ApiKeyRowUi,
    text: ApiKeysText,
    onDelete: () -> Unit,
) {
    val rowText = apiKeyRowText(row, text)
    val deleteLabel = stringResource(id = R.string.api_keys_delete)
    val deleteFailedLabel = stringResource(id = R.string.api_keys_delete_failed)
    val spokenRow = listOfNotNull(
        rowText.name,
        rowText.spokenCreated,
        rowText.id,
        deleteFailedLabel.takeIf { row.deleteFailed },
    ).joinToString(", ")

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleImage(
                size = 40.dp,
                backgroundColor = MainTintedBackgroundBase,
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { contentDescription = spokenRow }
            ) {
                // a long name wraps; nothing is cut
                Text(
                    rowText.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
                rowText.created?.let { created ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        created,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    rowText.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                if (row.deleteFailed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        deleteFailedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Red
                    )
                }
            }

            if (RowRemoveControl.Button in apiKeyRemoveControls) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (row.deleting) {
                        // the delete runs: progress, and no control
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = deleteLabel,
                                tint = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (RowRemoveControl.Swipe in apiKeyRemoveControls) {
        SwipeToRevealRow(
            onAction = onDelete,
            actionLabel = deleteLabel,
            accessibilityLabel = deleteLabel,
            // a running delete disables the control
            enabled = !row.deleting,
            content = content,
        )
    } else {
        content()
    }
}

/** The body's message (ApiKeysBody.messageRes) in place of the rows. */
@Composable
private fun ApiKeysBodyMessage(
    body: ApiKeysBody,
    action: @Composable () -> Unit = {},
) {
    body.messageRes?.let { messageRes ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(id = messageRes),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
            action()
        }
    }
}

/**
 * The name form. Create sends the trimmed name, and stays off until there is
 * one; the app always names a key. While the create runs nothing dismisses
 * the form, since its answer holds the new key; a failed create says so above
 * the buttons, and Create tries again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeysCreateDialog(
    form: ApiKeysDialog.Create,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // the name is no secret, so it may outlive a rotation in the saved state
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val nameFocus = remember { FocusRequester() }
    val canCreate = !form.creating && apiKeyCreateName(name.text) != null
    val submit = {
        if (canCreate) {
            onCreate(name.text)
        }
    }

    FoldSafeBasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !form.creating,
            dismissOnClickOutside = !form.creating,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                Text(
                    stringResource(id = R.string.api_keys_create),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                URTextInput(
                    value = name,
                    onValueChange = { value ->
                        // one line, within the server's limit
                        val input = apiKeyNameInput(value.text)
                        name = if (input == value.text) value else value.copy(text = input)
                    },
                    label = stringResource(id = R.string.api_keys_name_label),
                    placeholder = stringResource(id = R.string.api_keys_name_placeholder),
                    // as typed: a key's name is often a host or a tool's name
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    onDone = submit,
                    enabled = !form.creating,
                    modifier = Modifier.focusRequester(nameFocus),
                )

                if (form.failed) {
                    URInlineErrorText(message = stringResource(id = R.string.api_keys_create_failed))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    URButton(
                        onClick = onDismiss,
                        style = ButtonStyle.OUTLINE,
                        enabled = !form.creating,
                        modifier = Modifier.weight(1f)
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.cancel),
                            style = buttonTextStyle
                        )
                    }

                    URButton(
                        onClick = submit,
                        enabled = canCreate,
                        isProcessing = form.creating,
                        modifier = Modifier.weight(1f)
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.create),
                            style = buttonTextStyle
                        )
                    }
                }

                // in the dialog's own composition, once the field is attached
                LaunchedEffect(Unit) {
                    nameFocus.requestFocus()
                }
            }
        }
    }
}

// how long Copy key reads Copied after a copy
private const val API_KEY_COPIED_SHOWN_MILLIS = 2_000L

/**
 * The key as clipboard content marked sensitive: android 13 and later show no
 * preview of it, and keyboards that offer the clipboard leave it out. The
 * extra is a plain string, which keyboards on earlier versions read too.
 */
@SuppressLint("InlinedApi")
private fun sensitiveClip(label: String, apiKey: String): ClipData =
    ClipData.newPlainText(label, apiKey).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }

/**
 * The new key, shown once: the key, the warning that it will not be shown
 * again and gives full access to the account, Copy key and Done.
 *
 * The dialog is a secure window (SecureFlagPolicy.SecureOn): screenshots and
 * screen recordings leave it out, and so does the recents thumbnail, which
 * the system saves to disk. The key is not selectable, so its one copy is the
 * sensitive one. Only Done closes the dialog, which drops the key; back and a
 * tap outside do nothing, as the key is never shown again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeysCreatedDialog(
    created: ApiKeysDialog.Created,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(id = R.string.api_keys_title)
    // Copy key reads Copied for a moment after each copy
    var copyCount by remember { mutableIntStateOf(0) }
    var copiedShown by remember { mutableStateOf(false) }
    LaunchedEffect(copyCount) {
        if (0 < copyCount) {
            copiedShown = true
            delay(API_KEY_COPIED_SHOWN_MILLIS)
            copiedShown = false
        }
    }

    FoldSafeBasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            securePolicy = SecureFlagPolicy.SecureOn,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                Text(
                    stringResource(id = R.string.api_keys_created_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    created.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    created.apiKey,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = TextMuted.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    stringResource(id = R.string.api_keys_created_warning),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Yellow
                )

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        val clip = sensitiveClip(clipLabel, created.apiKey)
                        scope.launch {
                            clipboard.setClipEntry(clip.toClipEntry())
                        }
                        copyCount += 1
                    },
                    // the one primary button is Done
                    style = ButtonStyle.OUTLINE,
                    modifier = Modifier.fillMaxWidth()
                ) { buttonTextStyle ->
                    if (copiedShown) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.content_copy),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = if (copiedShown) R.string.api_keys_copied else R.string.api_keys_copy),
                        style = buttonTextStyle,
                        // a screen reader hears Copied, as the system confirms
                        // a copy only from android 13
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                URButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.done),
                        style = buttonTextStyle
                    )
                }
            }
        }
    }
}

/**
 * The confirmation before a delete, which names the key. Cancel is the
 * default: it comes first, takes the focus, and so do back and a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiKeysConfirmDeleteDialog(
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }

    FoldSafeBasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    stringResource(id = R.string.api_keys_delete_confirm_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    URButton(
                        onClick = onDismiss,
                        style = ButtonStyle.OUTLINE,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cancelFocus)
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.cancel),
                            style = buttonTextStyle
                        )
                    }

                    URButton(
                        onClick = onConfirm,
                        style = ButtonStyle.WARNING,
                        modifier = Modifier.weight(1f)
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.api_keys_delete),
                            style = buttonTextStyle
                        )
                    }
                }

                // in the dialog's own composition, once Cancel is attached
                LaunchedEffect(Unit) {
                    cancelFocus.requestFocus()
                }
            }
        }
    }
}
