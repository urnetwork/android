package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.navigation.NavController
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
import androidx.compose.ui.res.painterResource
import com.bringyour.client.Id
import com.bringyour.network.R
import com.bringyour.network.ui.components.InfoIconWithOverlay
import com.bringyour.network.ui.theme.BlueLight

@Composable
fun WalletsScreen(
    accountNavController: NavController,
    walletNavController: NavController,
    sagaViewModel: SagaViewModel,
    walletViewModel: WalletViewModel,
) {

    WalletsScreen(
        accountNavController,
        walletNavController,
        isSolanaSaga = sagaViewModel.isSolanaSaga,
        getSolanaAddress = sagaViewModel.getSagaWalletAddress,
        nextPayoutDate = walletViewModel.nextPayoutDateStr,
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
        setExternalWalletAddressIsValid = walletViewModel.setExternalWalletAddressIsValid,
        isInitializingFirstWallet = walletViewModel.isInitializingFirstWallet
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsScreen(
    accountNavController: NavController,
    walletNavController: NavController,
    isSolanaSaga: Boolean,
    getSolanaAddress: ((String?) -> Unit) -> Unit,
    nextPayoutDate: String,
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
    setExternalWalletAddressIsValid: (chain: String, isValid: Boolean) -> Unit,
    isInitializingFirstWallet: Boolean,
    wallets: List<AccountWallet>
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val app = context.applicationContext as? MainApplication

    // todo - populate this with real data
    val estimatedPayoutAmount = "0.25"

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

                        Log.i("WalletScreen", "circle wallet on result")
                        Log.i("WalletScreen", result.toString())

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
                    Text("Payout Wallets", style = TopBarTitleTextStyle)
                },
                navigationIcon = {
                    IconButton(onClick = { accountNavController.popBackStack() }) {
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
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            Column {
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
                            "Estimated on $nextPayoutDate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                estimatedPayoutAmount,
                                style = HeadingLargeCondensed
                            )

                            Spacer(modifier = Modifier.width(2.dp))

                            // this is really hacky, but setting line-height isn't being acknowledged
                            Box(
                                modifier = Modifier.offset(y = -(11).dp)
                            ) {
                                Text(
                                    "USDC",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
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

                if (wallets.isEmpty()) {
                    SetupWallet(
                        initCircleWallet = initCircleWallet,
                        circleWalletInProgress = circleWalletInProgress,
                        isSolanaSaga = isSolanaSaga,
                        getSolanaAddress = getSolanaAddress,
                        openModal = openModal,
                        connectSaga = { address ->

                            Log.i("WalletsScreen", "Solana address is: $address")
                            if (!address.isNullOrEmpty()) {
                                setExternalWalletAddress(TextFieldValue(address))
                                // since this is taken directly from the saga,
                                // we can mark this as true without calling our API to validate
                                setExternalWalletAddressIsValid("SOL", true)
                                createExternalWallet()
                            }
                        }
                    )
                } else {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            Text(
                                "Wallets",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.width(2.dp))

                            InfoIconWithOverlay() {
                                Column() {
                                    Text(
                                        "Solana and Polygon wallets are currently supported",
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
                                contentDescription = "Add wallet",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp)
                    ) {


                        items(wallets) { wallet ->

                            WalletCard(
                                isCircleWallet = !wallet.circleWalletId.isNullOrEmpty(),
                                blockchain = Blockchain.fromString(wallet.blockchain),
                                isPayoutWallet = wallet.walletId.equals(payoutWalletId),
                                walletAddress = wallet.walletAddress,
                                walletId = wallet.walletId,
                                navController = walletNavController
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
                    "Connect External Wallet",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "USDC addresses on Solana and Polygon are currently supported.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                URTextInput(
                    value = externalWalletAddress,
                    onValueChange = { newValue ->
                        setExternalWalletAddress(newValue)
                                    },
                    label = "Wallet Address",
                    placeholder = "Copy and paste here",
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(32.dp))


                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClickableText(
                        text = AnnotatedString(
                            "Cancel",
                            spanStyle = SpanStyle(
                                color = BlueMedium,
                                fontSize = 14.sp
                            )
                        ),
                        onClick = {
                            closeModal()
                            setExternalWalletAddress(TextFieldValue(""))
                                  },

                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    ClickableText(
                        text = AnnotatedString(
                            "Connect",
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
                        onClick = {
                            // todo - validate and add external wallet
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

    val parentNavController = rememberNavController()
    val walletNavController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            parentNavController,
            walletNavController,
            isSolanaSaga = false,
            getSolanaAddress = {},
            nextPayoutDate = "Jan 1",
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
            setExternalWalletAddressIsValid = { _, _ -> },
            isInitializingFirstWallet = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenSagaPreview() {

    val parentNavController = rememberNavController()
    val walletNavController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            parentNavController,
            walletNavController,
            isSolanaSaga = true,
            getSolanaAddress = {},
            nextPayoutDate = "Jan 1",
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
            setExternalWalletAddressIsValid = { _, _ -> },
            isInitializingFirstWallet = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenExternalWalletModalPreview() {

    val parentNavController = rememberNavController()
    val walletNavController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            parentNavController,
            walletNavController,
            isSolanaSaga = true,
            getSolanaAddress = {},
            nextPayoutDate = "Jan 1",
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
            setExternalWalletAddressIsValid = { _, _ -> },
            isInitializingFirstWallet = false
        )
    }
}

@Preview
@Composable
private fun WalletScreenInitializingWalletPreview() {

    val parentNavController = rememberNavController()
    val walletNavController = rememberNavController()

    URNetworkTheme {
        WalletsScreen(
            parentNavController,
            walletNavController,
            isSolanaSaga = false,
            getSolanaAddress = {},
            nextPayoutDate = "Jan 1",
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
            setExternalWalletAddressIsValid = { _, _ -> },
            isInitializingFirstWallet = true
        )
    }
}

