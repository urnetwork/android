package com.bringyour.network.ui.account

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.navigation.NavController
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.SwipeToRevealRow
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.relativeTime
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.launch

/**
 * Account -> Sessions (server/session/REVOKE-UI-FINAL.md): every sign-in of
 * the account, current first, each with its device, place, last use, sign-in
 * date, method and short id. Swipe a row left to reveal Sign out (assistive
 * tech gets "Sign out {device}" as the row's action), long-press a row to copy
 * its full session id, and pull to refresh. Every sign out asks first.
 *
 * Visible while started: LifecycleStartEffect drives the controller's
 * SetVisible, so it polls only while the screen shows.
 */
@Composable
fun SessionsScreen(
    navController: NavController,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(true)
        onStopOrDispose {
            viewModel.setVisible(false)
        }
    }

    SessionsContent(
        ui = viewModel.ui,
        confirmation = viewModel.confirmation,
        text = rememberSessionsText(),
        onBack = { navController.popBackStack() },
        onRefresh = viewModel::refresh,
        onSignOut = viewModel::requestSignOut,
        onSignOutOthers = viewModel::requestSignOutOthers,
        onDismissConfirmation = viewModel::dismissConfirmation,
        onConfirm = viewModel::confirm,
    )
}

/** The platform text of the screen; recreated when the configuration (the locale) changes. */
@Composable
private fun rememberSessionsText(): SessionsText {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { ContextSessionsText(context) }
}

private class ContextSessionsText(private val context: Context) : SessionsText {
    override fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    // past 7 days the same abbreviated date as the sign-in date
    override fun lastUsed(timeMillis: Long, nowMillis: Long): String =
        relativeTime(timeMillis, nowMillis, DateUtils.FORMAT_ABBREV_MONTH)

    override fun signedIn(timeMillis: Long): String = DateUtils.formatDateTime(
        context,
        timeMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
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
private fun SessionsContent(
    ui: SessionsUi,
    confirmation: SessionsConfirmation?,
    text: SessionsText,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: (String) -> Unit,
    onSignOutOthers: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.sessions_title),
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
                if (ui.refreshFailed) {
                    item(key = "refresh-failed") {
                        SessionsNote(
                            stringResource(id = R.string.sessions_refresh_failed),
                            color = Red
                        )
                    }
                }

                when (ui.body) {
                    SessionsBody.Progress -> item(key = "progress") {
                        val loadingLabel = stringResource(id = R.string.loading)
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .semantics { contentDescription = loadingLabel },
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = TextMuted,
                                trackColor = TextFaint,
                            )
                        }
                    }

                    SessionsBody.LoadFailed -> item(key = "load-failed") {
                        SessionsMessage(stringResource(id = R.string.sessions_load_failed)) {
                            TextButton(onClick = onRefresh) {
                                Text(stringResource(id = R.string.try_again))
                            }
                        }
                    }

                    SessionsBody.Unsupported -> item(key = "unsupported") {
                        SessionsMessage(stringResource(id = R.string.sessions_unsupported))
                    }

                    SessionsBody.Empty -> item(key = "empty") {
                        SessionsMessage(stringResource(id = R.string.sessions_empty))
                    }

                    SessionsBody.Rows -> items(ui.rows, key = { it.key }) { row ->
                        Column(modifier = Modifier.animateItem()) {
                            SessionRow(
                                row = row,
                                nowMillis = ui.nowMillis,
                                text = text,
                                onSignOut = { onSignOut(row.sessionId) },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                ui.signOutOthers?.let { others ->
                    item(key = "sign-out-others") {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            URButton(
                                onClick = onSignOutOthers,
                                style = ButtonStyle.WARNING,
                                isProcessing = others.signingOut,
                            ) { buttonTextStyle ->
                                Text(
                                    stringResource(
                                        id = if (others.signingOut) {
                                            R.string.sessions_signing_out
                                        } else {
                                            R.string.sessions_sign_out_all_others
                                        }
                                    ),
                                    style = buttonTextStyle
                                )
                            }
                            if (others.failed) {
                                Spacer(modifier = Modifier.height(8.dp))
                                URInlineErrorText(message = stringResource(id = R.string.try_again))
                            }
                        }
                    }
                }

                if (ui.body == SessionsBody.Rows) {
                    item(key = "last-used-help") {
                        SessionsNote(stringResource(id = R.string.sessions_last_used_help))
                    }
                }
                if (ui.legacyNote) {
                    item(key = "legacy-note") {
                        SessionsNote(stringResource(id = R.string.sessions_legacy_note))
                    }
                }
            }
        }
    }

    when (confirmation) {
        is SessionsConfirmation.SignOut -> SessionsConfirmDialog(
            title = stringResource(id = R.string.sessions_confirm_title),
            body = sessionConfirmBody(confirmation.row, text),
            onDismiss = onDismissConfirmation,
            onConfirm = onConfirm,
        )

        SessionsConfirmation.SignOutOthers -> SessionsConfirmDialog(
            title = stringResource(id = R.string.sessions_confirm_others_title),
            body = stringResource(id = R.string.sessions_confirm_others_body),
            onDismiss = onDismissConfirmation,
            onConfirm = onConfirm,
        )

        null -> {}
    }
}

/**
 * One session (§3): the country circle with the device logo, then the device
 * and version, the place and last use, and the sign-in date, method and id.
 * Assistive tech reads the row as one node, with full dates and times, and
 * offers "Sign out {device}" and the copy of the session id as its actions.
 */
@Composable
private fun SessionRow(
    row: SessionRowUi,
    nowMillis: Long,
    text: SessionsText,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val rowText = sessionRowText(row, nowMillis, text)
    val copyLabel = stringResource(id = R.string.sessions_copy_id)
    val thisSessionLabel = stringResource(id = R.string.sessions_this_session)
    val signingOutLabel = stringResource(id = R.string.sessions_signing_out)
    val actionFailedLabel = stringResource(id = R.string.sessions_action_failed)
    val spokenRow = listOfNotNull(
        rowText.device,
        thisSessionLabel.takeIf { row.current },
        rowText.spokenUse,
        rowText.spokenSignIn,
        signingOutLabel.takeIf { row.signingOut },
        actionFailedLabel.takeIf { row.actionFailed },
    ).joinToString(", ")
    // the full id, though the row shows only its first characters
    val copySessionId = {
        scope.launch {
            clipboard.setClipEntry(ClipData.newPlainText(row.sessionId, row.sessionId).toClipEntry())
        }
        // from android 13 the system confirms a copy itself
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }
    val circleColor = remember(row.countryCode) {
        Color(sessionCountryArgb(row.countryCode) { Sdk.getColorHex(it) })
    }

    SwipeToRevealRow(
        onAction = onSignOut,
        actionLabel = stringResource(id = R.string.sign_out),
        actionIcon = Icons.AutoMirrored.Filled.Logout,
        accessibilityLabel = rowText.signOutAction,
        enabled = !row.signingOut,
        mergeDescendants = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = spokenRow
                    onLongClick(label = copyLabel) {
                        copySessionId()
                        true
                    }
                }
                .pointerInput(row.sessionId) {
                    detectTapGestures(onLongPress = { copySessionId() })
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            CircleImage(
                size = 40.dp,
                backgroundColor = circleColor,
            ) {
                Icon(
                    painterResource(id = row.device.iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                SessionDeviceLine(rowText.device, current = row.current)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    rowText.use,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    rowText.signIn,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                if (row.signingOut) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = TextMuted,
                            trackColor = TextFaint,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            signingOutLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                if (row.actionFailed) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        actionFailedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Red
                    )
                }
            }
        }
    }
}

/** The device and version, and the This session tag on the current session's row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionDeviceLine(device: String, current: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            device,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
        if (current) {
            Text(
                stringResource(id = R.string.sessions_this_session),
                style = TextStyle(fontSize = 11.sp),
                color = BlueMedium,
                modifier = Modifier
                    .border(1.dp, BlueMedium, RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

/** A state of the list in place of the rows: empty, unsupported, or failed to load. */
@Composable
private fun SessionsMessage(
    message: String,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )
        action()
    }
}

@Composable
private fun SessionsNote(
    note: String,
    color: Color = TextMuted,
) {
    Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * The confirmation before a sign out (§4). Cancel is the default: it comes
 * first, takes the focus, and so do back and a tap outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsConfirmDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    title,
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
                            stringResource(id = R.string.sign_out),
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
