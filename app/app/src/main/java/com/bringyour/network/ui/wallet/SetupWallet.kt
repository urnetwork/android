package com.bringyour.network.ui.wallet

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.BlueMedium
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
                "You share with others, we share with you. Earn a share of revenue when you provide data to others in the network.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "To start earning, connect your cryptocurrency wallet to URnetwork or set one up with Circle.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }


        Column {

            if (isSolanaSaga) {

                URButton(
                    onClick = {
                        getSolanaAddress { address ->
                            Log.i("WalletScreen", "Connected Solana address is $address")
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