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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
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
    setTransferAmountFieldValue: (TextFieldValue) -> Unit,
    walletBalance: Float,
    sendToAddress: TextFieldValue,
    setSendToAddress: (TextFieldValue) -> Unit,
) {

    CircleTransferSheetContent(
        scaffoldState = scaffoldState,
        scope = scope,
        transferAmountTextFieldValue = transferAmountTextFieldValue,
        setTransferAmountFieldValue = setTransferAmountFieldValue,
        walletBalance = walletBalance,
        sendToAddress = sendToAddress,
        setSendToAddress = setSendToAddress
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleTransferSheetContent(
    scaffoldState: BottomSheetScaffoldState,
    scope: CoroutineScope,
    transferAmountTextFieldValue: TextFieldValue,
    setTransferAmountFieldValue: (TextFieldValue) -> Unit,
    walletBalance: Float,
    sendToAddress: TextFieldValue,
    setSendToAddress: (TextFieldValue) -> Unit,
) {
    
    val inputTextStyle = TextStyle(
        fontSize = 24.sp,
        // color = if (enabled) Color.White else TextMuted,
        color = Color.White,
        textAlign = TextAlign.Center,
        fontFamily = ppNeueMontreal
    )

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
                                setTransferAmountFieldValue(TextFieldValue(walletBalance.toString()))
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
                                        setTransferAmountFieldValue(it)
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(IntrinsicSize.Min),
                                    textStyle = inputTextStyle,
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                                )

                                if (transferAmountTextFieldValue.text.isEmpty()) {
                                    Text(
                                        "0",
                                        style = inputTextStyle
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                "USDC",
                                style = inputTextStyle,
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
                        maxLines = 2
                    )
                }

                // action buttons
                Column {
                    URButton(
                        onClick = {

                        }
                    ) { buttonTextStyle ->
                        Text(
                            stringResource(id = R.string.transfer),
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
                        style = ButtonStyle.OUTLINE
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

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun CircleTransferSheetPreview() {
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
            transferAmountTextFieldValue = TextFieldValue(""),
            setTransferAmountFieldValue = {},
            walletBalance = 3.1295674f,
            sendToAddress = TextFieldValue(""),
            setSendToAddress = {}
        )
    }
}