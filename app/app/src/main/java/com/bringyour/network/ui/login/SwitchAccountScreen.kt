package com.bringyour.network.ui.login

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun SwitchAccountScreen(
    currentNetworkName: String,
    targetJwt: String?,
    setSwitchAccount: (Boolean) -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication ?: return
    val loginActivity = context as? LoginActivity ?: return

    val onAccept: () -> Unit = {
        application.logout()

        if (!targetJwt.isNullOrEmpty()) {
            // switch to different account

            application.login(targetJwt)
            loginActivity.authClientAndFinish(
                {err ->
                    setSwitchAccount(false)
                },
            )

        } else {
            Log.i("SwitchAccountScreen", "No target jwt found")
        }
    }

    val onDecline: () -> Unit = {
        loginActivity.navigateToMain()
    }

    Scaffold() { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(id = R.string.switch_account),
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                stringResource(id = R.string.switch_account_details, currentNetworkName),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            URButton(
                onClick = onAccept
            ) { textStyle ->
                Text(
                    stringResource(id = R.string.switch_account),
                    style = textStyle
                )
            }

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            URButton(
                onClick = onDecline,
                style = ButtonStyle.OUTLINE
            ) { textStyle ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(id = R.string.cancel),
                        style = textStyle
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun SwitchAccountScreenPreview() {
    URNetworkTheme {
        SwitchAccountScreen(
            currentNetworkName = "URnetwork123",
            targetJwt = "abc",
            setSwitchAccount = {}
        )
    }
}
