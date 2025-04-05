package com.bringyour.network.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.account.AccountViewModel
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.components.URLinkText
import com.bringyour.network.ui.components.URSwitch
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.R
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.UpgradePlanBottomSheet
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.BlueMedium
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    planViewModel: PlanViewModel,
    settingsViewModel: SettingsViewModel,
    overlayViewModel: OverlayViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel
) {

    val notificationsAllowed = settingsViewModel.permissionGranted.collectAsState().value
    val currentPlan = subscriptionBalanceViewModel.currentPlan.collectAsState().value
    val showDeleteAccountDialog = settingsViewModel.showDeleteAccountDialog.collectAsState().value

    val scope = rememberCoroutineScope()

    val upgradePlanSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPresentingUpgradePlanSheet by remember { mutableStateOf(false) }
    val setIsPresentingUpgradePlanSheet: (Boolean) -> Unit = { isPresenting ->
        isPresentingUpgradePlanSheet = isPresenting
    }

    val expandUpgradePlanSheet: () -> Unit = {

        scope.launch {
            upgradePlanSheetState.expand()
            setIsPresentingUpgradePlanSheet(true)
        }

    }

    SettingsScreen(
        navController,
        clientId = accountViewModel.clientId,
        currentPlan = currentPlan,
        notificationsAllowed = notificationsAllowed,
        requestAllowNotifications = settingsViewModel.triggerPermissionRequest,
        notificationsPermanentlyDenied = settingsViewModel.notificationsPermanentlyDenied,
        allowProductUpdates = settingsViewModel.allowProductUpdates,
        toggleAllowProductUpdates = settingsViewModel.toggleAllowProductUpdates,
        provideWhileDisconnected = settingsViewModel.provideWhileDisconnected,
        toggleProvideWhileDisconnected = settingsViewModel.toggleProvideWhileDisconnected,
        urIdUrl = settingsViewModel.urIdUrl,
        expandUpgradePlanSheet = expandUpgradePlanSheet,
        showDeleteAccountDialog = showDeleteAccountDialog,
        setShowDeleteAccountDialog = settingsViewModel.setShowDeleteAccountDialog,
        deleteAccount = settingsViewModel.deleteAccount,
        isDeletingAccount = settingsViewModel.isDeletingAccount.collectAsState().value
    )

    if (isPresentingUpgradePlanSheet) {
        UpgradePlanBottomSheet(
            sheetState = upgradePlanSheetState,
            scope = scope,
            planViewModel = planViewModel,
            overlayViewModel = overlayViewModel,
            setIsPresentingUpgradePlanSheet = setIsPresentingUpgradePlanSheet
        )
    }


}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    clientId: String,
    currentPlan: Plan,
    notificationsAllowed: Boolean,
    notificationsPermanentlyDenied: Boolean,
    requestAllowNotifications: () -> Unit,
    allowProductUpdates: Boolean,
    toggleAllowProductUpdates: () -> Unit,
    provideWhileDisconnected: Boolean,
    toggleProvideWhileDisconnected: () -> Unit,
    urIdUrl: (String) -> String?,
    expandUpgradePlanSheet: () -> Unit,
    setShowDeleteAccountDialog: (Boolean) -> Unit = {},
    showDeleteAccountDialog: Boolean,
    deleteAccount: (onSuccess: () -> Unit, onFailure: (Exception?) -> Unit) -> Unit,
    isDeletingAccount: Boolean
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val application = context.applicationContext as? MainApplication

    // todo - load this maybe as an config var?
    val discordInviteLink = "https://discord.com/invite/RUNZXMwPRK"


    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.settings),
                        style = TopBarTitleTextStyle
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Black
                ),
                actions = {},
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(id = R.string.settings), style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.height(64.dp))

            URTextInputLabel(text = stringResource(id = R.string.plan))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        if (currentPlan == Plan.Basic) "Basic" else "Supporter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )


                    if (currentPlan == Plan.Basic) {
                        Spacer(modifier = Modifier.width(2.dp))

                        InfoIconWithOverlay() {
                            Column() {

                                Text(
                                    stringResource(id = R.string.unlock_supporter_tooltip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BlueLight
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandUpgradePlanSheet()
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(id = R.string.become_supporter),
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            lineHeight = 20.sp,
                                            fontWeight = FontWeight(700),
                                            color = BlueLight,
                                        )
                                    )
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Right Arrow",
                                        tint = BlueLight,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                }

                if (currentPlan == Plan.Basic) {
                    Text(
                        stringResource(id = R.string.change),
                        style = TextStyle(
                            color = BlueMedium
                        ),
                        modifier = Modifier.clickable {
                            expandUpgradePlanSheet()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(
                text = "URid"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        clipboardManager.setText(AnnotatedString(clientId))
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

            ) {
                Text(
                    clientId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = "Copy",
                    tint = TextMuted,
                    modifier = Modifier.width(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(
                text = "URL"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {

                        clipboardManager.setText(
                            AnnotatedString(
                                urIdUrl(clientId) ?: clientId
                            )
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

                ) {
                Text(
                    "https://ur.io/c?$clientId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Icon(
                    painter = painterResource(id = R.drawable.content_copy),
                    contentDescription = "Copy",
                    tint = TextMuted,
                    modifier = Modifier.width(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = stringResource(id = R.string.connections))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.provide_disconnected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = provideWhileDisconnected,
                    toggle = {
                        toggleProvideWhileDisconnected()
                    },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // allow notifications
            URTextInputLabel(text = stringResource(id = R.string.notifications_label))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.receive_notifications),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = notificationsAllowed,
                    enabled = !notificationsAllowed && !notificationsPermanentlyDenied,
                    toggle = {
                        requestAllowNotifications()
                    },
                )
            }

            Text(
                if (notificationsPermanentlyDenied || notificationsAllowed) stringResource(id = R.string.update_notification_settings) else "",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            URTextInputLabel(text = stringResource(id = R.string.stay_in_touch))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.send_product_updates),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = allowProductUpdates,
                    toggle = {
                        toggleAllowProductUpdates()
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.join_community_discord),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    URLinkText(
                        text = "Discord",
                        url = discordInviteLink,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(discordInviteLink))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Right Arrow",
                        tint = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row{
                URButton(
                    onClick = {
                        setShowDeleteAccountDialog(true)
                    },
                    style = ButtonStyle.WARNING
                ) { buttonTextStyle ->
                    Text(
                        stringResource(id = R.string.delete_account),
                        style = buttonTextStyle
                    )
                }
            }

        }

        if (showDeleteAccountDialog) {
            BasicAlertDialog(
                onDismissRequest = {
                    setShowDeleteAccountDialog(false)
                },
            ) {

                Surface(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight(),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {

                    Column(modifier = Modifier.padding(16.dp)) {


                        Row {
                            Text(
                                stringResource(id = R.string.delete_account),
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            Text(
                                stringResource(id = R.string.sorry_to_see_you_go),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row {
                            Text(
                                stringResource(id = R.string.are_you_sure_delete_account),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row {
                            URButton(
                                onClick = {

                                    deleteAccount(
                                        {
                                            setShowDeleteAccountDialog(false)

                                            application?.logout()

                                            val intent = Intent(context, LoginActivity::class.java)
                                            context.startActivity(intent)

                                            (context as? Activity)?.finish()

                                        },
                                        { exception ->
                                            Log.i(TAG, "Error deleting account: ${exception?.message}")
                                            setShowDeleteAccountDialog(false)
                                            // todo: snackbar show error
                                        }
                                    )
                                },
                                style = ButtonStyle.WARNING,
                                enabled = !isDeletingAccount,
                                isProcessing = isDeletingAccount
                            ) { buttonTextStyle ->
                                Text(
                                    stringResource(id = R.string.delete_account),
                                    style = buttonTextStyle
                                )
                            }
                        }

                    }
                }

            }
        }

    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    val navController = rememberNavController()

    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Basic,
            notificationsAllowed = true,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            toggleAllowProductUpdates = {},
            provideWhileDisconnected = true,
            toggleProvideWhileDisconnected = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            expandUpgradePlanSheet = {},
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false
        )
    }
}

@Preview
@Composable
private fun SettingsScreenSupporterPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = true,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            toggleAllowProductUpdates = {},
            provideWhileDisconnected = false,
            toggleProvideWhileDisconnected = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            expandUpgradePlanSheet = {},
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false
        )
    }
}

@Preview
@Composable
private fun SettingsScreenNotificationsDisabledPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = false,
            notificationsPermanentlyDenied = true,
            requestAllowNotifications = {},
            allowProductUpdates = true,
            toggleAllowProductUpdates = {},
            provideWhileDisconnected = true,
            toggleProvideWhileDisconnected = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            expandUpgradePlanSheet = {},
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false
        )
    }
}

@Preview
@Composable
private fun SettingsScreenNotificationsAllowedPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = false,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = false,
            toggleAllowProductUpdates = {},
            provideWhileDisconnected = true,
            toggleProvideWhileDisconnected = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            expandUpgradePlanSheet = {},
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false
        )
    }
}

@Preview
@Composable
private fun SettingsScreenDeleteAccountDialogPreview() {
    val navController = rememberNavController()
    URNetworkTheme {
        SettingsScreen(
            navController,
            clientId = "0000abc0-1111-0000-a123-000000abc000",
            currentPlan = Plan.Supporter,
            notificationsAllowed = false,
            notificationsPermanentlyDenied = false,
            requestAllowNotifications = {},
            allowProductUpdates = false,
            toggleAllowProductUpdates = {},
            provideWhileDisconnected = true,
            toggleProvideWhileDisconnected = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            expandUpgradePlanSheet = {},
            showDeleteAccountDialog = true,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false
        )
    }
}