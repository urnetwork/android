package com.bringyour.network.ui.wallet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueDark
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable()
fun AddWallet(
    circleWalletExists: Boolean,
    initCircleWallet: () -> Unit,
    openExternalWalletModal: () -> Unit,
    connectSagaWallet: () -> Unit,
) {
    var showOverlay by remember { mutableStateOf(false) }

    val isSaga = Build.MODEL.equals("SAGA", ignoreCase = true)

    Box() {
        IconButton(
            onClick = {
                if (!isSaga && circleWalletExists) {
                    // just directly open the modal to add external wallet
                    openExternalWalletModal()
                } else {
                    // prompt popup to allow choice of what type of wallet to add
                    showOverlay = true
                }
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
                    circleWalletExists = circleWalletExists,
                    initCircleWallet = initCircleWallet,
                    openExternalWalletModal = openExternalWalletModal,
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
    initCircleWallet: () -> Unit,
    circleWalletExists: Boolean,
    openExternalWalletModal: () -> Unit,
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
                focusable = true
            )
        ) {

            Column(
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .background(
                        BlueDark,
                    )
                    .width(224.dp)
            ) {
                if (isSaga) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                connectSagaWallet()
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text(stringResource(id = R.string.connect_saga_wallet))
                    }

                    HorizontalDivider()
                }

                if (!circleWalletExists) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                initCircleWallet()
                                onDismiss()
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Text(stringResource(id = R.string.setup_circle_wallet))
                    }
                    HorizontalDivider()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openExternalWalletModal()
                            onDismiss()
                        }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Text(stringResource(id = R.string.connect_external_wallet))
                }

            }
        }
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
                    circleWalletExists = false,
                    initCircleWallet = {},
                    openExternalWalletModal = {},
                    connectSagaWallet = {},
                    isSaga = true
                )
            }
        }
    }
}