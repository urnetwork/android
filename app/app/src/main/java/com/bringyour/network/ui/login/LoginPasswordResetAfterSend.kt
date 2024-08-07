package com.bringyour.network.ui.login

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.client.AuthPasswordResetArgs
import com.bringyour.client.BringYourApi
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Composable
fun LoginPasswordResetAfterSend(
    userAuth: String,
    byApi: BringYourApi?,
) {
    var markAsSent by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf(false) }
    var passwordResetError by remember { mutableStateOf<String?>(null) }
    val isBtnEnabled by remember {
        derivedStateOf {
            !inProgress && !markAsSent
        }
    }

    val sendResetLink = {
        val args = AuthPasswordResetArgs()
        args.userAuth = userAuth.trim()

        inProgress = true

        byApi?.authPasswordReset(args) { _, err ->
            runBlocking(Dispatchers.Main.immediate) {
                inProgress = false

                if (err != null) {
                    passwordResetError = err.message
                } else {
                    passwordResetError = null

                    markAsSent = true
                }
            }
        }
    }

    LaunchedEffect(markAsSent) {
        if (markAsSent) {
            delay(5000L)
            markAsSent = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Reset link sent", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(64.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                "Reset link sent to $userAuth",
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "You may need to check your spam folder or unblock no-reply@bringyour.com",
            style = TextStyle(
                fontSize = 12.sp,
                color = TextMuted
            )
        )
        Spacer(modifier = Modifier.height(32.dp))

        URButton(
            onClick = {
                sendResetLink()
            },
            enabled = isBtnEnabled
        ) { buttonTextStyle ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (markAsSent) "Sent!" else "Resend Link",
                    style = buttonTextStyle
                )
            }
        }
    }
}

@Preview
@Composable
fun LoginPasswordResetAfterSendPreview() {
    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginPasswordResetAfterSend(
                    userAuth = "hello@bringyour.com",
                    byApi = null
                )
            }
        }
    }
}