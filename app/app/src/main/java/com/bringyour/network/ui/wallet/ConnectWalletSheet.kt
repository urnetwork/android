package com.bringyour.network.ui.wallet

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.components.buttonTextStyle
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.TopBarTitleTextStyle
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

private enum class SheetContent {
    WALLET_LIST,
    EXTERNAL_WALLET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWalletSheet(
    setIsPresentedConnectWalletSheet: (Boolean) -> Unit,
    connectWalletSheetState: SheetState
) {
    var currentContent by remember { mutableStateOf(SheetContent.WALLET_LIST) }

    var isForwardNavigation by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = {
            setIsPresentedConnectWalletSheet(false)
        },
        sheetState = connectWalletSheetState
    ) {


        AnimatedContent(
            targetState = currentContent,
            transitionSpec = {
                if (isForwardNavigation) {
                    // Forward navigation (wallet list to external)
                    slideInHorizontally { height -> height } togetherWith
                        slideOutHorizontally { height -> -height }
                } else {
                    // Back navigation (external to wallet list)
                    slideInHorizontally { height -> -height } togetherWith
                        slideOutHorizontally { height -> height }
                }
            }
        ) { content ->
            when (content) {
                SheetContent.WALLET_LIST -> WalletProvidersList(
                    onExternalClick = {
                        isForwardNavigation = true
                        currentContent = SheetContent.EXTERNAL_WALLET
                    }
                )
                SheetContent.EXTERNAL_WALLET -> LinkExternalWalletView(
                    back = {
                        isForwardNavigation = false
                        currentContent = SheetContent.WALLET_LIST
                    }
                )
            }
        }

    }

}

@Composable
private fun WalletProvidersList(
    onExternalClick: () -> Unit
) {

    Surface (
        color = MainTintedBackgroundBase
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.Center
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                WalletProviderIconButton(
                    walletName = "Phantom",
                    iconPainter = painterResource(R.drawable.phantom_purple_logo),
                    onClick = {
                        // TODO: connect Phantom
                    }
                )

                WalletProviderIconButton(
                    walletName = "Solflare",
                    iconPainter = painterResource(R.drawable.solflare_logo),
                    onClick = {
                        // TODO: connect Solflare
                    }
                )

                WalletProviderIconButton(
                    walletName = "External",
                    iconPainter = painterResource(R.drawable.nav_list_item_wallet),
                    onClick = onExternalClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkExternalWalletView(
    back: () -> Unit
) {

    var text by remember { mutableStateOf(TextFieldValue("")) }

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
                    navigationIcon = {
                        IconButton(onClick = { back() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back"
                            )
                        }
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
                    value = text,
                    onValueChange = { text = it },
                    label = "USDC wallet address",
                    placeholder = "Enter a Solana or Matic USDC wallet address",
                    supportingText = "USDC addresses on Solana and Polygon are currently supported"
                )

                URButton(
                    onClick = {}
                ) { buttonTextStyle ->
                    Text("Link wallet", style = buttonTextStyle)
                }

            }

        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ConnectWalletSheetPreview() {

    val bottomSheetState = rememberStandardBottomSheetState(
        SheetValue.Expanded
    )

    URNetworkTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                ConnectWalletSheet(
                    setIsPresentedConnectWalletSheet = {},
                    connectWalletSheetState = bottomSheetState
                )
            }
        }
    }
}

