package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circle.programmablewallet.sdk.api.ApiError
import circle.programmablewallet.sdk.api.Callback
import circle.programmablewallet.sdk.api.ExecuteWarning
import circle.programmablewallet.sdk.result.ExecuteResult
import circle.programmablewallet.sdk.result.ExecuteResultStatus
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.overlays.OverlayMode
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.ui.theme.ppNeueMontreal
import com.bringyour.network.utils.isTablet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleTransferSheet(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    transferAmountTextFieldValue: TextFieldValue,
    setTransferAmountFieldValue: (TextFieldValue, Double) -> Unit,
    walletBalance: Double,
    setCircleWalletBalance: (Double) -> Unit,
    sendToAddress: TextFieldValue,
    setSendToAddress: (TextFieldValue) -> Unit,
    isSendToAddressValidating: Boolean,
    isSendToAddressValid: Boolean,
    setTransferError: (String?) -> Unit,
    setTransferInProgress: (Boolean) -> Unit,
    transfer: (OnWalletExecute) -> Unit,
    transferInProgress: Boolean,
    transferAmountValid: Boolean,
) {

    CircleTransferSheetContent(
        scaffoldState = scaffoldState,
        scope = scope,
        transferAmountTextFieldValue = transferAmountTextFieldValue,
        setTransferAmountFieldValue = setTransferAmountFieldValue,
        walletBalance = walletBalance,
        setCircleWalletBalance = setCircleWalletBalance,
        sendToAddress = sendToAddress,
        setSendToAddress = setSendToAddress,
        isSendToAddressValid = isSendToAddressValid,
        isSendToAddressValidating = isSendToAddressValidating,
        setTransferError = setTransferError,
        setTransferInProgress = setTransferInProgress,
        transfer = transfer,
        transferInProgress = transferInProgress,
        transferAmountValid = transferAmountValid
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleTransferSheetContent(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    transferAmountTextFieldValue: TextFieldValue,
    setTransferAmountFieldValue: (TextFieldValue, Double) -> Unit,
    walletBalance: Double,
    setCircleWalletBalance: (Double) -> Unit,
    sendToAddress: TextFieldValue,
    setSendToAddress: (TextFieldValue) -> Unit,
    isSendToAddressValidating: Boolean,
    isSendToAddressValid: Boolean,
    setTransferError: (String?) -> Unit,
    setTransferInProgress: (Boolean) -> Unit,
    transfer: (OnWalletExecute) -> Unit,
    transferInProgress: Boolean,
    transferAmountValid: Boolean,
) {

    val context = LocalContext.current
    val activity = context as? MainActivity
    val application = context.applicationContext as? MainApplication
    val overlayVc = application?.overlayVc
    
    val amountInputTextStyle = TextStyle(
        fontSize = 24.sp,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontFamily = ppNeueMontreal
    )

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            keyboardController?.hide()
        }
    }

    val transferUsdc = {

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
                        when (result?.status) {
                            ExecuteResultStatus.COMPLETE,
                            ExecuteResultStatus.IN_PROGRESS,
                            ExecuteResultStatus.PENDING -> complete(true)

                            else -> complete(false)
                        }
                        // FIXME toast
                        return false
                    }

                    override fun onError(error: Throwable): Boolean {
                        setTransferError(error.message)
                        setTransferInProgress(false)

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

                        // success
                        complete(true)
                    }

                    fun complete(success: Boolean) {

                        if (success) {
                            scope.launch {
                                overlayVc?.openOverlay(OverlayMode.TransferSubmitted.toString())
                                scaffoldState.bottomSheetState.hide()
                            }

                            setCircleWalletBalance(walletBalance - transferAmountTextFieldValue.text.toDouble())

                            setTransferInProgress(false)

                            setTransferAmountFieldValue(TextFieldValue(""), walletBalance)
                        }
                    }
                }
            )
        }

        transfer(onWalletExecute)

    }

    BottomSheetContentContainer {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(id = R.string.circle_transfer_topbar_title),
                            style = TopBarTitleTextStyle)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    scaffoldState.bottomSheetState.hide()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    },
                )
            }
        ) { innerPadding ->
            val colModifier = Modifier

            if (!isTablet()) {
                colModifier.fillMaxSize()
            }

            Column(
                modifier = colModifier
                    .background(color = Black)
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                Column {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        stringResource(id = R.string.circle_transfer_header),
                        style = MaterialTheme.typography.headlineLarge
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Send",
                            style = MaterialTheme.typography.bodyLarge,
                        )

                        Text(
                            "Max",
                            modifier = Modifier.clickable {
                                setTransferAmountFieldValue(TextFieldValue(walletBalance.toString()), walletBalance)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = BlueMedium
                        )

                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {

                        Row {
                            Box() {
                                BasicTextField(
                                    value = transferAmountTextFieldValue,
                                    onValueChange = {
                                        setTransferAmountFieldValue(it, walletBalance)
                                    },
                                    modifier = Modifier
                                        .width(IntrinsicSize.Min),
                                    textStyle = amountInputTextStyle,
                                    keyboardOptions = KeyboardOptions.Default.copy(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                        }
                                    )
                                )

                                if (transferAmountTextFieldValue.text.isEmpty()) {
                                    Text(
                                        "0",
                                        style = amountInputTextStyle
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                "USDC",
                                style = amountInputTextStyle,
                                color = TextMuted
                            )
                        }

                        Text(
                            String.format("%.2f", walletBalance),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(id = R.string.circle_transfer_fee_coverage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    // todo - this doesn't exactly match the Figma design
                    URTextInput(
                        value = sendToAddress,
                        onValueChange = {
                            setSendToAddress(it)
                        },
                        label = "To",
                        placeholder = "Wallet address",
                        isValidating = isSendToAddressValidating,
                        isValid = isSendToAddressValid || sendToAddress.text.isEmpty(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardController = keyboardController
                    )
                }

                // action buttons
                Column {
                    URButton(
                        onClick = {
                            transferUsdc()
                        },
                        enabled = isSendToAddressValid && transferAmountValid,
                        isProcessing = transferInProgress
                    ) { buttonTextStyle ->
                        Text(
                            if (transferAmountValid) stringResource(id = R.string.transfer)
                                else stringResource(id = R.string.insufficient_funds),
                            style = buttonTextStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    URButton(
                        onClick = {
                            scope.launch {
                                scaffoldState.bottomSheetState.hide()
                            }
                        },
                        style = ButtonStyle.OUTLINE,
                        enabled = !transferInProgress
                    ) { buttonTextStyle ->

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                stringResource(id = R.string.cancel),
                                style = buttonTextStyle
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

private val mockSetTransferAmountFieldValue: (TextFieldValue, Double) -> Unit = { _, _ -> }

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun CircleTransferSheetInvalidAddressPreview() {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )

    val scope = rememberCoroutineScope()

    URNetworkTheme {
        CircleTransferSheetContent(
            scaffoldState = scaffoldState,
            scope = scope,
            transferAmountTextFieldValue = TextFieldValue("1.00"),
            setTransferAmountFieldValue = mockSetTransferAmountFieldValue,
            walletBalance = 3.12,
            setCircleWalletBalance = {},
            sendToAddress = TextFieldValue("abcd"),
            setSendToAddress = {},
            isSendToAddressValidating = false,
            isSendToAddressValid = false,
            setTransferError = {},
            setTransferInProgress = {},
            transfer = {},
            transferInProgress = false,
            transferAmountValid = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun CircleTransferSheetSendAmountPreview() {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )

    val scope = rememberCoroutineScope()

    URNetworkTheme {
        CircleTransferSheetContent(
            scaffoldState = scaffoldState,
            scope = scope,
            transferAmountTextFieldValue = TextFieldValue("3.12"),
            setTransferAmountFieldValue = mockSetTransferAmountFieldValue,
            walletBalance = 3.12,
            setCircleWalletBalance = {},
            sendToAddress = TextFieldValue("0x35eeb71e1d098d53f0b0bb6cfeae0e3b0c4028b9"),
            setSendToAddress = {},
            isSendToAddressValidating = true,
            isSendToAddressValid = true,
            setTransferError = {},
            setTransferInProgress = {},
            transfer = {},
            transferInProgress = false,
            transferAmountValid = true
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun CircleTransferSheetSendAmountInvalidPreview() {
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )

    val scope = rememberCoroutineScope()

    URNetworkTheme {
        CircleTransferSheetContent(
            scaffoldState = scaffoldState,
            scope = scope,
            transferAmountTextFieldValue = TextFieldValue("5"),
            setTransferAmountFieldValue = mockSetTransferAmountFieldValue,
            walletBalance = 3.12,
            setCircleWalletBalance = {},
            sendToAddress = TextFieldValue("0x35eeb71e1d098d53f0b0bb6cfeae0e3b0c4028b9"),
            setSendToAddress = {},
            isSendToAddressValidating = true,
            isSendToAddressValid = true,
            setTransferError = {},
            setTransferInProgress = {},
            transfer = {},
            transferInProgress = false,
            transferAmountValid = false
        )
    }
}