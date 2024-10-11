package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import circle.programmablewallet.sdk.api.ApiError
import circle.programmablewallet.sdk.api.Callback
import circle.programmablewallet.sdk.api.ExecuteWarning
import circle.programmablewallet.sdk.result.ExecuteResult
import com.bringyour.client.AccountWallet
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.components.URDialog
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.bringyour.client.AccountPayment
import com.bringyour.client.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.theme.BlueLight

@Composable
fun WalletsScreen(
    navController: NavHostController,
    walletViewModel: WalletViewModel,
) {

    LaunchedEffect(Unit) {
        walletViewModel.fetchTransferStats()
    }

    WalletsScreen(
        navController,
        getSolanaAddress = walletViewModel.getSagaWalletAddress,
        addExternalWalletModalVisible = walletViewModel.addExternalWalletModalVisible,
        openModal = walletViewModel.openExternalWalletModal,
        closeModal = walletViewModel.closeExternalWalletModal,
        createCircleWallet = walletViewModel.createCircleWallet,
        circleWalletInProgress = walletViewModel.circleWalletInProgress,
        wallets = walletViewModel.wallets,
        externalWalletAddress = walletViewModel.externalWalletAddress,
        setExternalWalletAddress = walletViewModel.setExternaWalletAddress,
        walletValidationState = walletViewModel.externalWalletAddressIsValid,
        createExternalWallet = walletViewModel.createExternalWallet,
        isProcessingExternalWallet = walletViewModel.isProcessingExternalWallet,
        payoutWalletId = walletViewModel.payoutWalletId,
        isInitializingFirstWallet = walletViewModel.initializingFirstWallet,
        setCircleWalletInProgress = walletViewModel.setCircleWalletInProgress,
        setInitializingFirstWallet = walletViewModel.setInitializingFirstWallet,
        payouts = walletViewModel.payouts,
        isRemovingWallet = walletViewModel.isRemovingWallet,
        pollWallets = walletViewModel.pollWallets,
        initializingWallets = walletViewModel.initializingWallets,
        unpaidMegaByteCount = walletViewModel.unpaidMegaByteCount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    navController: NavHostController,
    getSolanaAddress: () -> Unit,
    addExternalWalletModalVisible: Boolean,
    openModal: () -> Unit,
    closeModal: () -> Unit,
    createCircleWallet: (OnWalletExecute) -> Unit,
    externalWalletAddress: TextFieldValue,
    setExternalWalletAddress: (TextFieldValue) -> Unit,
    circleWalletInProgress: Boolean,
    walletValidationState: WalletValidationState,
    createExternalWallet: () -> Unit,
    isProcessingExternalWallet: Boolean,
    payoutWalletId: Id?,
    isInitializingFirstWallet: Boolean,
    wallets: List<AccountWallet>,
    setCircleWalletInProgress: (Boolean) -> Unit,
    setInitializingFirstWallet: (Boolean) -> Unit,
    payouts: List<AccountPayment>,
    isRemovingWallet: Boolean,
    pollWallets: () -> Unit?,
    initializingWallets: Boolean,
    unpaidMegaByteCount: String
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val app = context.applicationContext as? MainApplication

    val initCircleWallet = {

        val onWalletExecute: OnWalletExecute = { walletSdk, userToken, encryptionKey, challengeId ->
            walletSdk.execute(
                activity,
                userToken,
                encryptionKey,
                arrayOf(challengeId),
                object : Callback<ExecuteResult> {
                    override fun onWarning(
                        warning: ExecuteWarning,
                        result: ExecuteResult?
                    ): Boolean {
                        complete()
                        // FIXME toast
                        return false
                    }

                    override fun onError(error: Throwable): Boolean {
                        setCircleWalletInProgress(false)
                        setInitializingFirstWallet(false)

                        if (error is ApiError) {
                            when (error.code) {
                                ApiError.ErrorCode.userCanceled -> return false // App won't handle next step, SDK will finish the Activity.
                                ApiError.ErrorCode.incorrectUserPin, ApiError.ErrorCode.userPinLocked,
                                ApiError.ErrorCode.incorrectSecurityAnswers, ApiError.ErrorCode.securityAnswersLocked,
                                ApiError.ErrorCode.insecurePinCode, ApiError.ErrorCode.pinCodeNotMatched -> {
                                }

                                ApiError.ErrorCode.networkError -> {
                                    // FIXME toast
                                }

                                else -> {
                                    // FIXME toast
                                }
                            }
                            // App will handle next step, SDK will keep the Activity.
                            return true
                        }

                        // App won't handle next step, SDK will finish the Activity.
                        return false
                    }

                    override fun onResult(result: ExecuteResult) {
                        complete()
                    }

                    fun complete() {
                        if (app?.hasBiometric == false) {
                            // update {}
                        } else {
                            // enable biometrics
                            walletSdk.setBiometricsPin(
                                activity,
                                userToken,
                                encryptionKey,
                                object : Callback<ExecuteResult> {
                                    override fun onError(error: Throwable): Boolean {

                                        error.printStackTrace()
                                        if (error is ApiError) {
                                            return when (error.code) {
                                                ApiError.ErrorCode.incorrectUserPin, ApiError.ErrorCode.userPinLocked, ApiError.ErrorCode.securityAnswersLocked, ApiError.ErrorCode.incorrectSecurityAnswers, ApiError.ErrorCode.pinCodeNotMatched, ApiError.ErrorCode.insecurePinCode -> true // App will handle next step, SDK will keep the Activity.
                                                else -> false
                                            }
                                        }
                                        return false // App won't handle next step, SDK will finish the Activity.
                                    }

                                    override fun onResult(result: ExecuteResult) {
                                        //success
                                        // update {}
                                    }

                                    override fun onWarning(
                                        warning: ExecuteWarning,
                                        result: ExecuteResult?
                                    ): Boolean {
                                        return false // App won't handle next step, SDK will finish the Activity.
                                    }
                                }
                            )
                        }

                        pollWallets()
                    }
                }
            )
        }

        createCircleWallet(onWalletExecute)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.payout_wallets),
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
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("URwallet", style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .background(
                            color = MainTintedBackgroundBase,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(
                            start = 16.dp,
                            top = 16.dp,
                            bottom = 10.dp, // hacky due to line-height issue
                            end = 16.dp
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(id = R.string.unpaid_mb),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                unpaidMegaByteCount,
                                style = HeadingLargeCondensed
                            )

                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    Text(
                        stringResource(id = R.string.payouts_amount_threshold),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

            }

            if (isInitializingFirstWallet) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

            } else {

                if (initializingWallets) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                } else {
                    if (wallets.isEmpty()) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            SetupWallet(
                                initCircleWallet = initCircleWallet,
                                circleWalletInProgress = circleWalletInProgress,
                                getSolanaAddress = getSolanaAddress,
                                openModal = openModal,
                            )
                        }
                    } else {

                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                Text(
                                    stringResource(id = R.string.wallets),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.width(2.dp))

                                InfoIconWithOverlay() {
                                    Column() {
                                        Text(
                                            stringResource(id = R.string.chains_supported),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BlueLight
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { openModal() },
                                modifier = Modifier
                                    .background(
                                        color = MainTintedBackgroundBase,
                                        shape = CircleShape
                                    )
                                    .width(26.dp)
                                    .height(26.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.plus_icon),
                                    contentDescription = stringResource(id = R.string.add_wallet),
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isRemovingWallet) {
                            Row(
                                modifier = Modifier
                                    .height(124.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(24.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(124.dp)
                            ) {

                                item {
                                    Spacer(modifier = Modifier.width(16.dp))
                                }

                                items(wallets) { wallet ->

                                    WalletCard(
                                        isCircleWallet = !wallet.circleWalletId.isNullOrEmpty(),
                                        blockchain = Blockchain.fromString(wallet.blockchain),
                                        isPayoutWallet = wallet.walletId.equals(payoutWalletId),
                                        walletAddress = wallet.walletAddress,
                                        walletId = wallet.walletId,
                                        navController = navController
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                }


                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            WalletsPayoutsList(
                                payouts,
                            )
                        }

                    }
                }
            }
        }

        URDialog(
            visible = addExternalWalletModalVisible,
            onDismiss = { closeModal() }
        ) {
            Column() {
                Text(
                    stringResource(id = R.string.connect_external_wallet),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(id = R.string.connect_external_wallet_supported_chains),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                URTextInput(
                    value = externalWalletAddress,
                    onValueChange = { newValue ->
                        setExternalWalletAddress(newValue)
                                    },
                    label = stringResource(id = R.string.wallet_address_label),
                    placeholder = stringResource(id = R.string.wallet_address_placeholder),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(32.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = AnnotatedString(
                            stringResource(id = R.string.cancel),
                            spanStyle = SpanStyle(
                                color = BlueMedium,
                                fontSize = 14.sp
                            )
                        ),
                        modifier = Modifier.clickable {
                            closeModal()
                            setExternalWalletAddress(TextFieldValue(""))
                        },
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    Text(
                        text = AnnotatedString(
                            stringResource(id = R.string.connect),
                            spanStyle = if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet)
                                SpanStyle(
                                    color = BlueMedium,
                                    fontSize = 14.sp
                                ) else
                                SpanStyle(
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                        ),
                        modifier = Modifier.clickable {
                            if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingExternalWallet) {
                                createExternalWallet()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun WalletScreenPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            getSolanaAddress = {},
            addExternalWalletModalVisible = false,
            openModal = {},
            closeModal = {},
            createCircleWallet = {},
            circleWalletInProgress = false,
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            createExternalWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setCircleWalletInProgress = {},
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            pollWallets = {},
            initializingWallets = false,
            unpaidMegaByteCount = "124.64"
        )
    }
}

@Preview
@Composable
private fun WalletScreenSagaPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            getSolanaAddress = {},
            addExternalWalletModalVisible = false,
            openModal = {},
            closeModal = {},
            createCircleWallet = {},
            circleWalletInProgress = false,
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            createExternalWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setCircleWalletInProgress = {},
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            pollWallets = {},
            initializingWallets = false,
            unpaidMegaByteCount = "124.64"
        )
    }
}

@Preview
@Composable
private fun WalletScreenExternalWalletModalPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            getSolanaAddress = {},
            addExternalWalletModalVisible = true,
            openModal = {},
            closeModal = {},
            createCircleWallet = {},
            circleWalletInProgress = false,
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            createExternalWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = false,
            setCircleWalletInProgress = {},
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            pollWallets = {},
            initializingWallets = false,
            unpaidMegaByteCount = "124.64"
        )
    }
}

@Preview
@Composable
private fun WalletScreenInitializingWalletPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            getSolanaAddress = {},
            addExternalWalletModalVisible = false,
            openModal = {},
            closeModal = {},
            createCircleWallet = {},
            circleWalletInProgress = false,
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            createExternalWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = true,
            setCircleWalletInProgress = {},
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = false,
            pollWallets = {},
            initializingWallets = false,
            unpaidMegaByteCount = "124.64"
        )
    }
}

@Preview
@Composable
private fun WalletScreenRemovingWalletPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            navController,
            getSolanaAddress = {},
            addExternalWalletModalVisible = false,
            openModal = {},
            closeModal = {},
            createCircleWallet = {},
            circleWalletInProgress = false,
            wallets = listOf(),
            externalWalletAddress = TextFieldValue(""),
            setExternalWalletAddress = {},
            walletValidationState = WalletValidationState(),
            createExternalWallet = {},
            isProcessingExternalWallet = false,
            payoutWalletId = null,
            isInitializingFirstWallet = true,
            setCircleWalletInProgress = {},
            setInitializingFirstWallet = {},
            payouts = listOf(),
            isRemovingWallet = true,
            pollWallets = {},
            initializingWallets = false,
            unpaidMegaByteCount = "124.64"
        )
    }
}

