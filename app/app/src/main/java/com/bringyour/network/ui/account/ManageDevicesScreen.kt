package com.bringyour.network.ui.account

import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.FoldSafeBasicAlertDialog
import com.bringyour.network.ui.components.FoldSafeDialog
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.tabletReadableColumn
import com.bringyour.network.ui.connect.clientLimitMessageId
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.utils.relativeTime
import kotlinx.coroutines.delay

// how often "Last seen" moves on while the list shows
private const val MANAGE_DEVICES_CLOCK_MILLIS = 30_000L

/**
 * Settings -> Manage devices, and the client limit notice's Manage devices:
 * the network's devices and hosted proxies, this device first, each other one
 * removable after a confirmation. A removal frees its slot of the plan's
 * devices connected at once, and the sdk then ends this device's client limit
 * hold so it connects (ManageDevicesStore).
 */
@Composable
fun ManageDevicesScreen(navController: NavController) {
    val application = LocalContext.current.applicationContext as? MainApplication
    val device = application?.device
    // one controller per device; a replaced device closes the former one's
    val store = remember(device) { device?.let { ManageDevicesStore.forDevice(it) } }
    DisposableEffect(store) {
        store?.start()
        onDispose {
            store?.close()
        }
    }

    ManageDevicesScaffold(
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        },
    ) { modifier ->
        if (store == null) {
            // no device: only a new sign-in helps
            ManageDevicesMessage(
                stringResource(id = R.string.manage_devices_sign_in_required),
                modifier = modifier,
            )
        } else {
            ManageDevicesList(store = store, message = null, modifier = modifier)
        }
    }
}

/**
 * The device list for a sign-in the client limit refused, as a full-screen
 * dialog over the sign-in screens: every request carries the sign-in's network
 * token, and a removal runs the sign-in again ([retry]). [message] says why
 * the list opened, and why the last retry failed.
 */
@Composable
fun SignInManageDevicesDialog(
    limit: SignInClientLimit,
    message: String,
    retry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val store = remember(limit) {
        ManageDevicesStore.forSignIn(limit.api, limit.networkJwt, retry)
    }
    DisposableEffect(store) {
        store.start()
        onDispose {
            store.close()
        }
    }

    FoldSafeDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        fullScreen = true,
    ) {
        ManageDevicesScaffold(
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(id = R.string.close)
                    )
                }
            },
        ) { modifier ->
            ManageDevicesList(store = store, message = message, modifier = modifier)
        }
    }
}

/**
 * A sign-in the network's client limit refused (MainApplication
 * signInClientLimit), over the sign-in screens: first what to do with Manage
 * devices, then the device list, where a removal runs the sign-in again and a
 * success completes the login as the sign-in screens do.
 */
@Composable
fun SignInClientLimitOffer() {
    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication ?: return
    // the activity, captured outside the dialogs, whose context is not it
    val loginActivity = context as? LoginActivity
    val limit by application.signInClientLimit.collectAsState()
    val retryMessage by application.signInClientLimitRetryMessage.collectAsState()
    val current = limit ?: return
    var listOpen by remember(current.sequence) { mutableStateOf(false) }
    val message = retryMessage ?: stringResource(id = clientLimitMessageId(current.offersUpgrade))

    if (!listOpen) {
        SignInClientLimitDialog(
            message = message,
            onManageDevices = { listOpen = true },
            onDismiss = application::dismissSignInClientLimit,
        )
    } else {
        SignInManageDevicesDialog(
            limit = current,
            message = message,
            retry = {
                application.retrySignInClientLimit(current) { request ->
                    loginActivity?.finishAuthenticatedLoginNow(request)
                }
            },
            onDismiss = application::dismissSignInClientLimit,
        )
    }
}

/**
 * What a sign-in the client limit refused offers: what to do, and Manage
 * devices. No Get Pro: the purchase needs a signed-in device, and the server
 * signs the apps in and holds a free plan at connect, where the connect
 * notice offers it.
 */
@Composable
fun SignInClientLimitDialog(
    message: String,
    onManageDevices: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    FoldSafeBasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(id = R.string.client_limit_reached),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
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
                        onClick = onManageDevices,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("acceptance.client_limit_sign_in_manage_devices")
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.manage_devices),
                            style = buttonTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    cancelFocus.requestFocus()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageDevicesScaffold(
    navigationIcon: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.manage_devices),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = navigationIcon,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
            )
        },
        containerColor = Black
    ) { innerPadding ->
        content(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        )
    }
}

@Composable
private fun ManageDevicesList(
    store: ManageDevicesStore,
    message: String?,
    modifier: Modifier,
) {
    val ui = store.ui
    // "Last seen" moves on while the list shows
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MANAGE_DEVICES_CLOCK_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = modifier.tabletReadableColumn(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "help") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    stringResource(id = R.string.manage_devices_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
        }

        if (store.connecting) {
            item(key = "connecting") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("acceptance.manage_devices_connecting"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = TextMuted,
                        trackColor = TextFaint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = R.string.manage_devices_connecting),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when {
            // only a new sign-in, through the app's own flow, helps
            ui.signInRequired -> item(key = "sign-in-required") {
                ManageDevicesMessage(stringResource(id = R.string.manage_devices_sign_in_required))
            }

            !ui.loaded && ui.loadFailed && !ui.loading -> item(key = "load-failed") {
                ManageDevicesMessage(stringResource(id = R.string.manage_devices_load_failed)) {
                    TextButton(onClick = store::refresh) {
                        Text(stringResource(id = R.string.try_again))
                    }
                }
            }

            !ui.loaded -> item(key = "progress") {
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

            else -> {
                if (ui.loadFailed && !ui.loading) {
                    item(key = "refresh-failed") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(id = R.string.manage_devices_load_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = Red,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = store::refresh) {
                                Text(stringResource(id = R.string.try_again))
                            }
                        }
                    }
                }
                if (ui.removeFailed) {
                    item(key = "remove-failed") {
                        Text(
                            stringResource(id = R.string.manage_devices_remove_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = Red,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(ui.devices, key = { it.clientId }) { device ->
                    Column {
                        ManagedDeviceRow(
                            device = device,
                            nowMillis = nowMillis,
                            removing = ui.removingClientId,
                            onRemove = { store.requestRemove(device) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    store.confirmation?.let { device ->
        ManageDevicesConfirmDialog(
            body = stringResource(id = R.string.sessions_confirm_body_no_place, managedDeviceTitle(device)),
            onDismiss = store::cancelRemove,
            onConfirm = { store.confirmRemove(device) },
        )
    }
}

/** The device's name, else what it is. */
@Composable
private fun managedDeviceTitle(device: ManagedDeviceUi): String = when {
    device.name.isNotEmpty() -> device.name
    device.proxy -> stringResource(id = R.string.manage_devices_proxy)
    else -> stringResource(id = R.string.sessions_device_unknown)
}

/** "This device · 18.5 Pixel 9 · Connected", or "Last seen 5m ago". */
@Composable
private fun managedDeviceDetail(device: ManagedDeviceUi, nowMillis: Long): String {
    val parts = mutableListOf<String>()
    if (device.thisDevice) {
        parts.add(stringResource(id = R.string.manage_devices_this_device))
    }
    if (device.proxy && device.name.isNotEmpty()) {
        parts.add(stringResource(id = R.string.manage_devices_proxy))
    }
    if (device.spec.isNotEmpty()) {
        parts.add(device.spec)
    }
    if (device.connected) {
        parts.add(stringResource(id = R.string.connected))
    } else if (0 < device.lastSeenMillis) {
        // past 7 days the same abbreviated date as the sessions list
        parts.add(
            stringResource(
                id = R.string.manage_devices_last_seen,
                relativeTime(device.lastSeenMillis, nowMillis, DateUtils.FORMAT_ABBREV_MONTH),
            )
        )
    }
    return parts.joinToString(" · ")
}

@Composable
private fun ManagedDeviceRow(
    device: ManagedDeviceUi,
    nowMillis: Long,
    removing: String?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                managedDeviceTitle(device),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = managedDeviceDetail(device, nowMillis)
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!device.thisDevice) {
            Spacer(modifier = Modifier.width(8.dp))
            if (removing == device.clientId) {
                CircularProgressIndicator(
                    color = TextMuted,
                    trackColor = TextFaint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                TextButton(
                    onClick = onRemove,
                    enabled = removing == null,
                    modifier = Modifier.testTag("acceptance.manage_devices_remove")
                ) {
                    Text(
                        stringResource(id = R.string.remove),
                        color = if (removing == null) Red else TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageDevicesMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
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

/**
 * The confirmation before a removal. Cancel is the default: it comes first,
 * takes the focus, and so do back and a tap outside.
 */
@Composable
private fun ManageDevicesConfirmDialog(
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    FoldSafeBasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(id = R.string.manage_devices_remove_title),
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
                            stringResource(id = R.string.remove),
                            style = buttonTextStyle
                        )
                    }
                }
                LaunchedEffect(Unit) {
                    cancelFocus.requestFocus()
                }
            }
        }
    }
}
