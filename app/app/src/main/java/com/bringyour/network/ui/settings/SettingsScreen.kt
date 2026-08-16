package com.bringyour.network.ui.settings

import com.bringyour.network.BuildConfig
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Outbound
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.network.ui.components.URTextInputLabel
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.TextDanger
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.CopyReferralCode
import com.bringyour.network.ui.components.ProvideCellPicker
import com.bringyour.network.ui.components.ProvideControlModePicker
import com.bringyour.network.ui.login.SeedphraseDisplayScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    accountViewModel: AccountViewModel,
    planViewModel: PlanViewModel,
    settingsViewModel: SettingsViewModel,
    overlayViewModel: OverlayViewModel,
    activityResultSender: ActivityResultSender?,
    walletViewModel: WalletViewModel,
    bonusReferralCode: String,
    isPro: Boolean,
    totalReferrals: Long = 0L
) {

    val notificationsAllowed = settingsViewModel.permissionGranted.collectAsState().value
    val showDeleteAccountDialog = settingsViewModel.showDeleteAccountDialog.collectAsState().value
    val referralNetwork = settingsViewModel.referralNetwork.collectAsState().value
    val isPresentingAuthCodeDialog = settingsViewModel.isPresentingAuthCodeDialog.collectAsState().value
    val authCode = settingsViewModel.authCode.collectAsState().value

    val networkUser = accountViewModel.networkUser.collectAsState().value
    val authMethods = remember(networkUser) { networkUser?.let { parseAuthMethods(it) } ?: emptyList() }
    val isAddingAuth = settingsViewModel.isAddingAuth.collectAsState().value
    val isRemovingAuth = settingsViewModel.isRemovingAuth.collectAsState().value

    var presentAddAuthSheet by remember { mutableStateOf(false) }
    var pendingRemoveMethod by remember { mutableStateOf<String?>(null) }
    var removeAuthError by remember { mutableStateOf<String?>(null) }

    val generatedSeedphrase = settingsViewModel.generatedSeedphrase.collectAsState().value
    val isGeneratingSeedphrase = settingsViewModel.isGeneratingSeedphrase.collectAsState().value
    val isRegeneratingSeedphrase = settingsViewModel.isRegeneratingSeedphrase.collectAsState().value

    var pendingSeedphraseAction by remember { mutableStateOf<SeedphraseAction?>(null) }
    var seedphraseActionError by remember { mutableStateOf<String?>(null) }

    // Close the confirm dialog only once the request actually succeeds (mirrors
    // the remove-sign-in-method dialog, which closes on its onSuccess callback,
    // not eagerly on tap) -- otherwise an async error has nowhere to render,
    // since seedphraseActionError lives inside this dialog's own content.
    LaunchedEffect(generatedSeedphrase) {
        if (generatedSeedphrase != null) {
            pendingSeedphraseAction = null
        }
    }

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

    var isPresentingRenameDevice by remember { mutableStateOf(false) }
    var editingDeviceName by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(Unit) {
        settingsViewModel.fetchDeviceInfo()
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
        currentPlan = if (isPro) Plan.Supporter else Plan.Basic,
        notificationsAllowed = notificationsAllowed,
        requestAllowNotifications = settingsViewModel.triggerPermissionRequest,
        notificationsPermanentlyDenied = settingsViewModel.notificationsPermanentlyDenied,
        allowProductUpdates = settingsViewModel.allowProductUpdates,
        toggleAllowProductUpdates = settingsViewModel.toggleAllowProductUpdates,
        provideControlMode = settingsViewModel.provideControlMode,
        setProvideControlMode = settingsViewModel.setProvideControlMode,
        deviceName = settingsViewModel.deviceName,
        deviceSpec = settingsViewModel.deviceSpec,
        onEditDeviceName = {
            editingDeviceName = TextFieldValue(settingsViewModel.deviceName)
            isPresentingRenameDevice = true
        },
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
        provideIndicatorRingColor = settingsViewModel.provideIndicatorRingColor,
        stripePortalUrl = settingsViewModel.stripePortalUrl.collectAsState().value,
        totalReferrals = totalReferrals,
        authMethods = authMethods,
        onRemoveAuthMethod = { method -> pendingRemoveMethod = method },
        onAddAuthMethodClick = { presentAddAuthSheet = true },
        hasSeedphrase = authMethods.contains("seedphrase"),
        isGeneratingSeedphrase = isGeneratingSeedphrase,
        isRegeneratingSeedphrase = isRegeneratingSeedphrase,
        onSeedphraseActionClick = { action -> pendingSeedphraseAction = action },
    )

    if (isPresentingRenameDevice) {
        URDialog(
            visible = true,
            onDismiss = { isPresentingRenameDevice = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(id = R.string.device_name_label),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                URTextInput(
                    value = editingDeviceName,
                    onValueChange = { editingDeviceName = it },
                    label = null,
                    placeholder = stringResource(id = R.string.device_name_label),
                )
                Spacer(modifier = Modifier.height(16.dp))
                URButton(
                    onClick = {
                        settingsViewModel.updateDeviceName(editingDeviceName.text) { success ->
                            isPresentingRenameDevice = false
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = if (success) "Device name updated"
                                        else "There was an error updating the device name.",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    enabled = !settingsViewModel.isUpdatingDeviceName
                ) { buttonTextStyle ->
                    Text(stringResource(id = R.string.save), style = buttonTextStyle)
                }
            }
        }
    }

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

    AddAuthMethodSheet(
        visible = presentAddAuthSheet,
        onDismiss = { presentAddAuthSheet = false },
        showGoogleOption = BuildConfig.BRINGYOUR_BUNDLE_SSO_GOOGLE,
        activityResultSender = activityResultSender,
        isAddingAuth = isAddingAuth,
        addAuth = settingsViewModel.addAuth,
        onAdded = {
            presentAddAuthSheet = false
            accountViewModel.refreshNetworkUser()
        }
    )

    URDialog(
        visible = pendingRemoveMethod != null,
        onDismiss = {
            pendingRemoveMethod = null
            removeAuthError = null
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Remove ${pendingRemoveMethod?.let { methodDisplayName(it) } ?: ""}?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You won't be able to sign in with this method anymore.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (removeAuthError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                URInlineErrorText(removeAuthError)
            }
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = {
                    val method = pendingRemoveMethod ?: return@URButton
                    removeAuthError = null
                    settingsViewModel.removeAuth(
                        method,
                        {
                            pendingRemoveMethod = null
                            accountViewModel.refreshNetworkUser()
                        },
                        { msg -> removeAuthError = msg }
                    )
                },
                enabled = !isRemovingAuth,
                isProcessing = isRemovingAuth
            ) { buttonTextStyle ->
                Text("Remove", style = buttonTextStyle)
            }
        }
    }

    URDialog(
        visible = pendingSeedphraseAction != null,
        onDismiss = {
            pendingSeedphraseAction = null
            seedphraseActionError = null
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val isRegenerate = pendingSeedphraseAction == SeedphraseAction.REGENERATE
            Text(
                if (isRegenerate) "Regenerate your seedphrase?" else "Generate a seedphrase?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isRegenerate) {
                    "Your current seedphrase will stop working. You'll be shown a new one to save."
                } else {
                    "A seedphrase lets you recover your account if you lose access. You'll be shown it once."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            if (seedphraseActionError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                URInlineErrorText(seedphraseActionError)
            }
            Spacer(modifier = Modifier.height(16.dp))
            URButton(
                onClick = {
                    seedphraseActionError = null
                    val onError: (String) -> Unit = { msg -> seedphraseActionError = msg }
                    if (isRegenerate) {
                        settingsViewModel.regenerateSeedphrase(onError)
                    } else {
                        settingsViewModel.generateSeedphrase(onError)
                    }
                },
                enabled = !isGeneratingSeedphrase && !isRegeneratingSeedphrase,
                isProcessing = isGeneratingSeedphrase || isRegeneratingSeedphrase
            ) { buttonTextStyle ->
                Text(if (isRegenerate) "Regenerate" else "Generate", style = buttonTextStyle)
            }
        }
    }

    if (generatedSeedphrase != null) {
        SeedphraseDisplayScreen(
            seedphrase = generatedSeedphrase,
            onConfirmed = {
                settingsViewModel.dismissSeedphraseDisplay()
                accountViewModel.refreshNetworkUser()
            },
            onBack = {
                settingsViewModel.dismissSeedphraseDisplay()
                accountViewModel.refreshNetworkUser()
            }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
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
    deviceName: String = "",
    deviceSpec: String = "",
    onEditDeviceName: () -> Unit = {},
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
    provideIndicatorRingColor: Color? = null,
    stripePortalUrl: String?,
    totalReferrals: Long = 0L,
    authMethods: List<String>,
    onRemoveAuthMethod: (String) -> Unit,
    onAddAuthMethodClick: () -> Unit,
    hasSeedphrase: Boolean,
    isGeneratingSeedphrase: Boolean,
    isRegeneratingSeedphrase: Boolean,
    onSeedphraseActionClick: (SeedphraseAction) -> Unit,
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

                /**
                 * referral royalty: networks with at least one referral get the
                 * crowned frog mascot (same as the ur.io site)
                 */
                if (0 < totalReferrals) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.referral_frog),
                            contentDescription = stringResource(id = R.string.referral_royalty),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            stringResource(id = R.string.referral_royalty),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(64.dp))

            /**
             * Client ID
             */
            URTextInputLabel(
                text = "Client ID"
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


            /**
             * Referral code
             */
            URTextInputLabel(
                text = stringResource(id = R.string.referral_code)
            )

            CopyReferralCode(
                bonusReferralCode = bonusReferralCode
            )

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
            URTextInputLabel(stringResource(id = R.string.account))
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

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Balance codes link
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Route.BalanceCodes)
                    }
                    .padding(vertical = 6.dp)
                ,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.balance_codes_link),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Keyboard Arrow Right",
                    tint = TextMuted
                )
            }


            Spacer(modifier = Modifier.height(32.dp))

            /**
             * Seedphrase
             */
            URTextInputLabel(text = "Seedphrase")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (hasSeedphrase) "Regenerate Seedphrase" else "Generate Seedphrase",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if ((hasSeedphrase && isRegeneratingSeedphrase) || (!hasSeedphrase && isGeneratingSeedphrase)) {
                        CircularProgressIndicator(modifier = Modifier.width(16.dp))
                    } else {
                        TextButton(onClick = {
                            onSeedphraseActionClick(
                                if (hasSeedphrase) SeedphraseAction.REGENERATE else SeedphraseAction.GENERATE
                            )
                        }) {
                            Text(
                                if (hasSeedphrase) "Regenerate" else "Generate",
                                color = BlueMedium
                            )
                        }
                    }
                }
            }
            Text(
                "A seedphrase lets you recover your account if you lose access.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(32.dp))

            /**
             * Sign-In Methods
             */
            URTextInputLabel(text = "Sign-In Methods")

            authMethods.forEach { method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        methodDisplayName(method),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    // removing the last method would permanently lock the
                    // account after logout -- keep at least one way to sign in
                    if (authMethods.size > 1) {
                        TextButton(onClick = { onRemoveAuthMethod(method) }) {
                            Text(
                                "Remove",
                                color = TextDanger
                            )
                        }
                    }
                }
            }

            TextButton(onClick = onAddAuthMethodClick) {
                Text(
                    "Add sign-in method",
                    color = BlueMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            /**
             * General
             */
            URTextInputLabel(stringResource(id = R.string.general))

            /**
             * Show icon when connected
             */
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

            if (supportsBatteryOptimizationExemption()) {
                /**
                 * Battery optimization
                 */

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id = R.string.ignore_battery_optimizations),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    BatteryOptimizationToggle()

                }

                Row {
                    Text(
                        stringResource(id = R.string.disable_ignore_battery_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            /**
             * Device name and spec
             */
            URTextInputLabel(text = stringResource(id = R.string.device))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditDeviceName() }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.device_name_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        deviceName.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(id = R.string.edit_device_name),
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.device_spec_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(16.dp))
                // the spec string can be very long; keep it to one ellipsized line
                Text(
                    deviceSpec.ifEmpty { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            URTextInputLabel(text = stringResource(id = R.string.connections))

            ProvideControlModePicker(
                provideControlMode,
                setProvideControlMode,
                provideIndicatorColor,
                provideIndicatorRingColor
            )

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Allow providing on cell networks
             */
            ProvideCellPicker(
                allowProvideCell = allowProvideCell,
                toggleProvideCell = toggleProvideCell
            )

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Kill switch
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(id = R.string.kill_switch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    InfoIconWithOverlay(
                        contentDescription = stringResource(
                            id = R.string.show_kill_switch_exception
                        )
                    ) {
                        Column {
                            Text(
                                stringResource(id = R.string.kill_switch_exception),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(id = R.string.kill_switch_exception_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = BlueLight
                            )
                        }
                    }
                }

                URSwitch(
                    checked = !routeLocal,
                    toggle = {
                        toggleRouteLocal()
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            /**
             * Device location sync (the mock location provider setup guide)
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Route.MockLocationGuide)
                    }
                    .padding(vertical = 6.dp)
                ,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.mock_location_settings_row),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Keyboard Arrow Right",
                    tint = TextMuted
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
                        if (currentPlan == Plan.Basic) "Basic" else "Pro",
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

                    ManageSubscriptionButton(stripePortalUrl)

                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            /**
            * Version
             */
            URTextInputLabel(stringResource(id = R.string.developer))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate(Route.Developer)
                    }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(id = R.string.dev_open_tools),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(id = R.string.dev_open_tools),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            /**
             * Protocol
             */
            val uriHandler = LocalUriHandler.current
            Text(
                stringResource(id = R.string.uses_ur_protocol),
                style = MaterialTheme.typography.bodyMedium,
                color = BlueMedium,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://ur.xyz")
                }
            )

            // The Seeker/Saga data multiplier is promoted only on the Solana Mobile flavor.
            if (BuildConfig.BRINGYOUR_BUNDLE_STORE == "solana_dapp") {

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
            } // end Solana-only seeker multiplier block

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
            stripePortalUrl = null,
            authMethods = listOf("email"),
            onRemoveAuthMethod = {},
            onAddAuthMethodClick = {},
            hasSeedphrase = false,
            isGeneratingSeedphrase = false,
            isRegeneratingSeedphrase = false,
            onSeedphraseActionClick = {},
        )
    }
}

@Composable
fun BatteryOptimizationToggle() {
    val context = LocalContext.current
    var isIgnored by remember { mutableStateOf(false) }

    // Check status when the screen resumes
    // we can't get "allow" or "deny" response when we fire the activity
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    URSwitch(
        checked = isIgnored,
        toggle = {
            if (!isIgnored) {
                requestBatteryOptimizationExemption(context)
            }
        },
        enabled = !isIgnored
    )
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
            stripePortalUrl = null,
            authMethods = listOf("email"),
            onRemoveAuthMethod = {},
            onAddAuthMethodClick = {},
            hasSeedphrase = false,
            isGeneratingSeedphrase = false,
            isRegeneratingSeedphrase = false,
            onSeedphraseActionClick = {},
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
            stripePortalUrl = null,
            authMethods = listOf("email"),
            onRemoveAuthMethod = {},
            onAddAuthMethodClick = {},
            hasSeedphrase = false,
            isGeneratingSeedphrase = false,
            isRegeneratingSeedphrase = false,
            onSeedphraseActionClick = {},
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
            stripePortalUrl = null,
            authMethods = listOf("email"),
            onRemoveAuthMethod = {},
            onAddAuthMethodClick = {},
            hasSeedphrase = false,
            isGeneratingSeedphrase = false,
            isRegeneratingSeedphrase = false,
            onSeedphraseActionClick = {},
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
            stripePortalUrl = null,
            authMethods = listOf("email"),
            onRemoveAuthMethod = {},
            onAddAuthMethodClick = {},
            hasSeedphrase = false,
            isGeneratingSeedphrase = false,
            isRegeneratingSeedphrase = false,
            onSeedphraseActionClick = {},
        )
    }
}

private enum class SeedphraseAction { GENERATE, REGENERATE }
