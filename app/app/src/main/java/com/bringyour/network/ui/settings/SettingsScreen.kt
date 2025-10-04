package com.bringyour.network.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Outbound
import androidx.compose.material.icons.automirrored.outlined.Outbound
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.bringyour.network.ui.Route
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.settings.updateReferralNetworkBottomSheet.UpdateReferralNetworkBottomSheet
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.viewmodels.OverlayViewModel
import com.bringyour.network.ui.shared.viewmodels.Plan
import com.bringyour.network.ui.shared.viewmodels.PlanViewModel
import com.bringyour.network.ui.shared.viewmodels.SubscriptionBalanceViewModel
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.wallet.WalletViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.successPayload
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.launch
import java.util.Date
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    planViewModel: PlanViewModel,
    settingsViewModel: SettingsViewModel,
    overlayViewModel: OverlayViewModel,
    subscriptionBalanceViewModel: SubscriptionBalanceViewModel,
    activityResultSender: ActivityResultSender?,
    walletViewModel: WalletViewModel,
    bonusReferralCode: String,
) {

    val notificationsAllowed = settingsViewModel.permissionGranted.collectAsState().value
    val currentPlan = subscriptionBalanceViewModel.currentPlan.collectAsState().value
    val showDeleteAccountDialog = settingsViewModel.showDeleteAccountDialog.collectAsState().value
    val referralNetwork = settingsViewModel.referralNetwork.collectAsState().value
    val isPresentingAuthCodeDialog = settingsViewModel.isPresentingAuthCodeDialog.collectAsState().value
    val authCode = settingsViewModel.authCode.collectAsState().value

    val scope = rememberCoroutineScope()

    val updateReferralNetworkSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isPresentingUpdateReferralNetworkSheet by remember { mutableStateOf(false) }
    val setIsPresentingUpdateReferralNetworkSheet: (Boolean) -> Unit = { isPresenting ->
        isPresentingUpdateReferralNetworkSheet = isPresenting
    }

    val solanaUri = Uri.parse("https://ur.io")
    val iconUri = Uri.parse("favicon.ico")
    val identityName = "URnetwork"
    val snackbarHostState = remember { SnackbarHostState() }

    val clipboardManager = LocalClipboardManager.current

    val expandUpdateNetworkReferralSheet: () -> Unit = {
        scope.launch {
            updateReferralNetworkSheetState.expand()
            setIsPresentingUpdateReferralNetworkSheet(true)
        }
    }

    val signAndVerifySeekerHolder: () -> Unit = {
        scope.launch {

            // `connect` dispatches an association intent to MWA-compatible wallet apps.
            activityResultSender?.let { activityResultSender ->

                // Instantiate the MWA client object
                val walletAdapter = MobileWalletAdapter(
                    connectionIdentity = ConnectionIdentity(
                        identityUri = solanaUri,
                        iconUri = iconUri,
                        identityName = identityName,
                    ),
                )
                walletAdapter.blockchain = Solana.Mainnet

                val timestamp = Date().time.toString()
                val message = "Verify Seeker Token Holder - $timestamp"
                val result = walletAdapter.transact(activityResultSender) { authResult ->
                    signMessagesDetached(arrayOf(message.toByteArray()), arrayOf((authResult.accounts.first().publicKey)))
                }

                when (result) {
                    is TransactionResult.Success -> {
                        val signedMessageBytes = result.successPayload?.messages?.first()?.signatures?.first()
                        val signatureBase64 = Base64.encodeToString(signedMessageBytes, Base64.NO_WRAP)
                        // val message = result.successPayload?.messages?.first()?.message?.decodeToString()
                        val pk = SolanaPublicKey(result.authResult.accounts.first().publicKey)

                        walletViewModel.verifySeekerHolder(
                            pk,
                            message,
                            signatureBase64
                        ) { errMsg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = errMsg,
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Indefinite
                                )
                            }
                        }


                    }
                    is TransactionResult.NoWalletFound -> {
                        println("No MWA compatible wallet app found on device.")
                    }
                    is TransactionResult.Failure -> {
                        println("Error during transaction signing: ${result.e}")
                    }
                }
            }

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
        provideControlMode = settingsViewModel.provideControlMode,
        setProvideControlMode = settingsViewModel.setProvideControlMode,
        urIdUrl = settingsViewModel.urIdUrl,
        showDeleteAccountDialog = showDeleteAccountDialog,
        setShowDeleteAccountDialog = settingsViewModel.setShowDeleteAccountDialog,
        deleteAccount = settingsViewModel.deleteAccount,
        isDeletingAccount = settingsViewModel.isDeletingAccount.collectAsState().value,
        routeLocal = settingsViewModel.routeLocal.collectAsState().value,
        toggleRouteLocal = settingsViewModel.toggleRouteLocal,
        allowForeground = settingsViewModel.allowForeground,
        toggleAllowForeground = settingsViewModel.toggleAllowForeground,
        snackbarHostState = snackbarHostState,
        signAndVerifySeekerHolder = signAndVerifySeekerHolder,
        isSeekerHolder = walletViewModel.isSeekerHolder.collectAsState().value,
        bonusReferralCode = bonusReferralCode,
        referralNetworkName = referralNetwork?.name,
        expandUpdateNetworkReferralSheet = expandUpdateNetworkReferralSheet,
        version = settingsViewModel.version,
        allowProvideCell = settingsViewModel.allowProvideOnCell.collectAsState().value,
        toggleProvideCell = settingsViewModel.toggleAllowProvideOnCell,
        authCodeCreate = settingsViewModel.authCodeCreate,
        authCode = authCode,
        isCreatingAuthCode = settingsViewModel.isCreatingAuthCode.collectAsState().value,
        setDisplayAuthCodeDialog = settingsViewModel.setIsPresentingAuthCodeDialog,
        provideIndicatorColor = settingsViewModel.provideIndicatorColor,
        stripePortalUrl = settingsViewModel.stripePortalUrl.collectAsState().value
    )

    if (isPresentingAuthCodeDialog) {
        AuthCodeCreateDialog(
            authCode = authCode,
            onDismissRequest = {
                settingsViewModel.setIsPresentingAuthCodeDialog(false)
            },
            copyAuthCode = {
                clipboardManager.setText(AnnotatedString(authCode ?: ""))
                settingsViewModel.setIsPresentingAuthCodeDialog(false)
            }
        )
    }

    if (isPresentingUpdateReferralNetworkSheet) {
        UpdateReferralNetworkBottomSheet(
            sheetState = updateReferralNetworkSheetState,
            setIsPresenting = setIsPresentingUpdateReferralNetworkSheet,
            onSuccess = {
                setIsPresentingUpdateReferralNetworkSheet(false)
                settingsViewModel.fetchReferralNetwork()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Referral network updated",
                        withDismissAction = true,
                         duration = SnackbarDuration.Short
                    )
                }
            },
            onError = { errMsg ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = errMsg,
                        withDismissAction = true,
                        duration = SnackbarDuration.Short
                    )
                }
            },
            referralNetworkName = referralNetwork?.name
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
    provideControlMode: ProvideControlMode,
    setProvideControlMode: (ProvideControlMode) -> Unit,
    urIdUrl: (String) -> String?,
    setShowDeleteAccountDialog: (Boolean) -> Unit = {},
    showDeleteAccountDialog: Boolean,
    deleteAccount: (onSuccess: () -> Unit, onFailure: (Exception?) -> Unit) -> Unit,
    isDeletingAccount: Boolean,
    routeLocal: Boolean,
    toggleRouteLocal: () -> Unit,
    allowForeground: Boolean,
    toggleAllowForeground: () -> Unit,
    snackbarHostState: SnackbarHostState,
    signAndVerifySeekerHolder: () -> Unit,
    isSeekerHolder: Boolean,
    bonusReferralCode: String,
    referralNetworkName: String?,
    expandUpdateNetworkReferralSheet: () -> Unit,
    version: String,
    allowProvideCell: Boolean,
    toggleProvideCell: () -> Unit,
    authCodeCreate: () -> Unit,
    authCode: String?,
    isCreatingAuthCode: Boolean,
    setDisplayAuthCodeDialog: (Boolean) -> Unit,
    provideIndicatorColor: Color,
    stripePortalUrl: String?
) {

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val application = context.applicationContext as? MainApplication

    // todo - load this maybe as an config var?
    val discordInviteLink = "https://discord.com/invite/RUNZXMwPRK"

    val depinHubStr = "DePIN Hub"
    val depinHubLink = "https://depinhub.io/projects/urnetwork"
    val seekerLink = "https://ur.io/seeker"

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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

            /**
             * URid
             */
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


            /**
             * Referral code
             */
            URTextInputLabel(
                text = stringResource(id = R.string.referral_code)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0x1AFFFFFF),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        clipboardManager.setText(AnnotatedString(bonusReferralCode))
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,

                ) {
                Text(
                    bonusReferralCode,
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

            /**
             * Update referral network
             */
            URTextInputLabel(stringResource(id = R.string.referral_network))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    referralNetworkName ?: stringResource(id = R.string.none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                TextButton(onClick = {
                    expandUpdateNetworkReferralSheet()
                }) {
                    Text(
                        stringResource(id = R.string.update),
                        color = BlueMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            /**
             * Auth code
             */
            URTextInputLabel(stringResource(id = R.string.authentication))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.auth_code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                TextButton(onClick = {
                    authCodeCreate()
                    setDisplayAuthCodeDialog(true)
                }) {
                    Text(
                        stringResource(id = R.string.create),
                        color = BlueMedium
                    )
                }
            }
            Text(
                stringResource(id = R.string.auth_code_expires),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(32.dp))

            /**
             * General
             */
            URTextInputLabel(stringResource(id = R.string.general))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.show_icon_when_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = allowForeground,
                    toggle = {
                        toggleAllowForeground()
                        application?.updateVpnService()
                    },
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = stringResource(id = R.string.connections))

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color = provideIndicatorColor, shape = CircleShape)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        stringResource(id = R.string.provide_mode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

                ProvideControlMode.entries.forEach { mode ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (mode == provideControlMode),
                            onClick = { setProvideControlMode(mode) }
                        )
                        Text(
                            stringResource(id = ProvideControlMode.toStringResourceId(mode)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Allow providing on cell networks
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.allow_providing_cell),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = allowProvideCell,
                    toggle = {
                        toggleProvideCell()
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Allow local traffic
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.route_local),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                URSwitch(
                    checked = routeLocal,
                    toggle = {
                        toggleRouteLocal()
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Route.BlockedRegions)
                    }
                    .padding(vertical = 6.dp)
                ,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.blocked_locations),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Keyboard Arrow Right",
                    tint = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(32.dp))

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

            Spacer(modifier = Modifier.height(18.dp))

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
                        val intent = Intent(Intent.ACTION_VIEW, discordInviteLink.toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Outbound,
                        contentDescription = "Right Arrow",
                        tint = TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.depin_hub),
                        contentDescription = "Image description",
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    Text(
                        text = buildAnnotatedString {
                            val fullText = stringResource(R.string.verified_project_on,
                                depinHubStr)
                            val startIndex = fullText.indexOf(depinHubStr)

                            append(fullText.substring(0, startIndex))
                            withStyle(style = SpanStyle(color = BlueMedium)) {
                                append(depinHubStr)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                }

                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, depinHubLink.toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Outbound,
                        contentDescription = "Right Arrow",
                        tint = TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))


            /**
             * Plan
             */
            URTextInputLabel(text = stringResource(id = R.string.plan))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                                            navController.navigate(Route.Upgrade)
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
                            navController.navigate(Route.Upgrade)
                        }
                    )
                }

                if (currentPlan == Plan.Supporter) {

//                    TextButton(
//                        onClick = {
//                            val intent = Intent(Intent.ACTION_VIEW, stripePortalUrl.toUri())
//                            context.startActivity(intent)
//                        }
//                    ) {
//                        Text(
//                            stringResource(id = R.string.manage_subscription),
//                            color = BlueMedium
//                        )
//                    }
                    ManageSubscriptionButton(stripePortalUrl)

                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            /**
            * Version
             */
            URTextInputLabel(stringResource(id = R.string.version_info))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    version.ifEmpty { "0.0.0" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            /**
             * Seeker wallet holder
             */
            URTextInputLabel(stringResource(id = R.string.earning_multipliers))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.claim_multiplier),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                if (isSeekerHolder) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Multiplier claimed",
                        tint = Green
                    )
                } else {

                    TextButton(onClick = {
                        signAndVerifySeekerHolder()
                    }) {
                        Text(
                            stringResource(id = R.string.claim),
                            color = BlueMedium
                        )
                    }
                }

            }

            Text(
                stringResource(id = R.string.connect_seeker_wallet),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.learn_more_about_multiplier),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, seekerLink.toUri())
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Outbound,
                        contentDescription = "Right Arrow",
                        tint = TextMuted,
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
            provideControlMode = ProvideControlMode.AUTO,
            setProvideControlMode = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false,
            routeLocal = false,
            toggleRouteLocal = {},
            allowForeground = false,
            toggleAllowForeground = {},
            snackbarHostState = remember { SnackbarHostState() },
            signAndVerifySeekerHolder = {},
            isSeekerHolder = false,
            bonusReferralCode = "ABC123",
            referralNetworkName = "parent_network",
            expandUpdateNetworkReferralSheet = {},
            version = "1.2.3",
            allowProvideCell = true,
            toggleProvideCell = {},
            authCodeCreate = {},
            authCode = null,
            isCreatingAuthCode = false,
            setDisplayAuthCodeDialog = {},
            provideIndicatorColor = Green,
            stripePortalUrl = null
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
            provideControlMode = ProvideControlMode.AUTO,
            setProvideControlMode = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false,
            routeLocal = false,
            toggleRouteLocal = {},
            allowForeground = false,
            toggleAllowForeground = {},
            snackbarHostState = remember { SnackbarHostState() },
            signAndVerifySeekerHolder = {},
            isSeekerHolder = false,
            bonusReferralCode = "ABC123",
            referralNetworkName = null,
            expandUpdateNetworkReferralSheet = {},
            version = "1.2.3",
            allowProvideCell = true,
            toggleProvideCell = {},
            authCodeCreate = {},
            authCode = null,
            isCreatingAuthCode = false,
            setDisplayAuthCodeDialog = {},
            provideIndicatorColor = Green,
            stripePortalUrl = null
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
            provideControlMode = ProvideControlMode.AUTO,
            setProvideControlMode = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false,
            routeLocal = false,
            toggleRouteLocal = {},
            allowForeground = false,
            toggleAllowForeground = {},
            snackbarHostState = remember { SnackbarHostState() },
            signAndVerifySeekerHolder = {},
            isSeekerHolder = true,
            bonusReferralCode = "ABC123",
            referralNetworkName = "parent_network",
            expandUpdateNetworkReferralSheet = {},
            version = "1.2.3",
            allowProvideCell = true,
            toggleProvideCell = {},
            authCodeCreate = {},
            authCode = null,
            isCreatingAuthCode = false,
            setDisplayAuthCodeDialog = {},
            provideIndicatorColor = Green,
            stripePortalUrl = null
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
            provideControlMode = ProvideControlMode.AUTO,
            setProvideControlMode = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            showDeleteAccountDialog = false,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false,
            routeLocal = false,
            toggleRouteLocal = {},
            allowForeground = false,
            toggleAllowForeground = {},
            snackbarHostState = remember { SnackbarHostState() },
            signAndVerifySeekerHolder = {},
            isSeekerHolder = false,
            bonusReferralCode = "ABC123",
            referralNetworkName = "parent_network",
            expandUpdateNetworkReferralSheet = {},
            version = "1.2.3",
            allowProvideCell = true,
            toggleProvideCell = {},
            authCodeCreate = {},
            authCode = null,
            isCreatingAuthCode = false,
            setDisplayAuthCodeDialog = {},
            provideIndicatorColor = Green,
            stripePortalUrl = null
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
            provideControlMode = ProvideControlMode.AUTO,
            setProvideControlMode = {},
            urIdUrl = { clientId -> "https://ur.io/c?$clientId" },
            showDeleteAccountDialog = true,
            setShowDeleteAccountDialog = {},
            deleteAccount = { onSuccess, onFailure -> },
            isDeletingAccount = false,
            routeLocal = false,
            toggleRouteLocal = {},
            allowForeground = false,
            toggleAllowForeground = {},
            snackbarHostState = remember { SnackbarHostState() },
            signAndVerifySeekerHolder = {},
            isSeekerHolder = false,
            bonusReferralCode = "ABC123",
            referralNetworkName = null,
            expandUpdateNetworkReferralSheet = {},
            version = "1.2.3",
            allowProvideCell = true,
            toggleProvideCell = {},
            authCodeCreate = {},
            authCode = null,
            isCreatingAuthCode = false,
            setDisplayAuthCodeDialog = {},
            provideIndicatorColor = Green,
            stripePortalUrl = null
        )
    }
}
