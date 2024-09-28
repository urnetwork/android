package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.TextMuted

@Composable
fun SetupWallet(
    initCircleWallet: () -> Unit,
    circleWalletInProgress: Boolean,
    isSolanaSaga: Boolean,
    getSolanaAddress: ((String?) -> Unit) -> Unit,
    openModal: () -> Unit,
    connectSaga: (String?) -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        Column {
            Text(
                stringResource(id = R.string.share_with_others),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(id = R.string.start_earning),
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }


        Column {

            if (isSolanaSaga) {

                URButton(
                    onClick = {
                        getSolanaAddress { address ->
                            connectSaga(address)
                        }
                    },
                    // style = ButtonStyle.OUTLINE
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Connect Saga wallet", style = buttonTextStyle)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        openModal()
                    },
                    style = ButtonStyle.OUTLINE
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Connect external wallet", style = buttonTextStyle)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

            } else {
                // non Saga wallet

                URButton(
                    onClick = {
                        initCircleWallet()
                    },
                    enabled = !circleWalletInProgress
                ) { buttonTextStyle ->
                    Text("Set up Circle Wallet", style = buttonTextStyle)
                }

                Spacer(modifier = Modifier.height(16.dp))

                URButton(
                    onClick = {
                        openModal()
                    },
                    style = ButtonStyle.OUTLINE
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Connect external wallet", style = buttonTextStyle)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

            }
        }
    }
}