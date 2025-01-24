package com.bringyour.network.ui.wallet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable()
fun AddWallet(
    openSolanaConnectModal: () -> Unit,
    openExternalConnectModal: () -> Unit,
    connectSagaWallet: () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(false) }

    val isSaga = Build.MODEL.equals("SAGA", ignoreCase = true)

    Box() {
        IconButton(
            onClick = {
                showOverlay = true
            },
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

            if (showOverlay) {
                AddWalletPopup(
                    onDismiss = {
                        showOverlay = false
                    },
                    openSolanaConnectModal = openSolanaConnectModal,
                    openExternalWalletModal = openExternalConnectModal,
                    connectSagaWallet = connectSagaWallet,
                    isSaga = isSaga
                )
            }
        }
    }
}

@Composable
fun AddWalletPopup(
    onDismiss: () -> Unit,
    openExternalWalletModal: () -> Unit,
    openSolanaConnectModal: () -> Unit,
    connectSagaWallet: () -> Unit,
    isSaga: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Popup(
            onDismissRequest = onDismiss,
            alignment = Alignment.TopEnd,
            offset = IntOffset(x = 16, y = 96),
            properties = PopupProperties(
                focusable = true,
            ),
        ) {

            Column(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        MainTintedBackgroundBase,
                    )
                    .width(224.dp)
            ) {
                if (isSaga) {

                    PopupMenuItem(
                        painter = painterResource(id = R.drawable.solana_logo),
                        text = stringResource(id = R.string.connect_saga_wallet),
                        onClick = {
                            connectSagaWallet()
                            onDismiss()
                        }
                    )

                } else {

                    PopupMenuItem(
                        painter = painterResource(id = R.drawable.solana_logo),
                        text = "Connect Solana wallet",
                        onClick = {
                            openSolanaConnectModal()
                            onDismiss()
                        }
                    )

                }

                HorizontalDivider()

                PopupMenuItem(
                    painter = painterResource(id = R.drawable.plus_icon),
                    text = stringResource(id = R.string.connect_external_wallet),
                    onClick = {
                        openExternalWalletModal()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun PopupMenuItem(
    painter: Painter,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(text)
    }
}

@Preview
@Composable
private fun AddWalletPopupPreview() {
    URNetworkTheme {
        Scaffold { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AddWalletPopup(
                    onDismiss = {},
                    openExternalWalletModal = {},
                    connectSagaWallet = {},
                    isSaga = true,
                    openSolanaConnectModal = {}
                )
            }
        }
    }
}