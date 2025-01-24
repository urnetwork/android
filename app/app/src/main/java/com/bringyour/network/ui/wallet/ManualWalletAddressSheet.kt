package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TopBarTitleTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualWalletAddressSheet(
    setIsPresentedConnectWalletSheet: (Boolean) -> Unit,
    connectWalletSheetState: SheetState,
    externalWalletAddress: TextFieldValue,
    setExternalWalletAddress: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    walletValidationState: WalletValidationState,
    isProcessingWallet: Boolean
) {

    ModalBottomSheet(
        onDismissRequest = {
            setIsPresentedConnectWalletSheet(false)
        },
        sheetState = connectWalletSheetState
    ) {


        Box(
            modifier = Modifier
                .height(248.dp)
                .fillMaxWidth()
        ) {

            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                "Link external wallet",
                                style = TopBarTitleTextStyle
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MainTintedBackgroundBase
                        ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        actions = {},
                    )
                },
            ) { innerPadding ->

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(264.dp)
                        .background(MainTintedBackgroundBase)
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {

                    URTextInput(
                        value = externalWalletAddress,
                        onValueChange = setExternalWalletAddress,
                        label = "USDC wallet address",
                        placeholder = "Enter a Solana or Matic USDC wallet address",
                        supportingText = "USDC addresses on Solana and Polygon are currently supported",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        onDone = {
                            if ((walletValidationState.solana || walletValidationState.polygon) && !isProcessingWallet)  {
                                onSubmit()
                            }
                        }
                    )

                    URButton(
                        onClick = onSubmit,
                        enabled = (walletValidationState.solana || walletValidationState.polygon) && !isProcessingWallet,
                    ) { buttonTextStyle ->
                        Text("Link wallet", style = buttonTextStyle)
                    }

                }
            }
        }
    }
}
