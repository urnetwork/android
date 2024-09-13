package com.bringyour.network.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.client.AuthVerifyArgs
import com.bringyour.client.AuthVerifySendArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URCodeInput
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Composable
fun LoginVerify(
    userAuth: String,
    navController: NavController
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    val codeLength = 8
    var code by remember { mutableStateOf(List(codeLength) { "" }) }
    var resendInProgress by remember { mutableStateOf(false) }
    var markResendAsSent by remember { mutableStateOf(false) }
    var resendError by remember { mutableStateOf<String?>(null) }
    val resendBtnEnabled by remember {
        derivedStateOf {
            resendError == null &&
                    !resendInProgress &&
                    !markResendAsSent
        }
    }
    var verifyInProgress by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }

    val resendCode = {

        resendInProgress = true

        val args = AuthVerifySendArgs()
        args.userAuth = userAuth

        application?.api?.authVerifySend(args) { _, err ->
            runBlocking(Dispatchers.Main.immediate) {

                resendInProgress = false
                markResendAsSent = true

                if (err != null) {
                    resendError = context.getString(R.string.verify_send_error)
                }
            }
        }
    }

    val verify = {

        verifyInProgress = true

        val args = AuthVerifyArgs()
        args.userAuth = userAuth
        args.verifyCode = code.joinToString("")

        application?.api?.authVerify(args) { result, err ->
            runBlocking(Dispatchers.Main.immediate) {
                verifyInProgress = false

                if (err != null) {
                    verifyError = err.message
                } else if (result.error != null) {
                    verifyError = result.error.message
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    verifyError = null

                    application.login(result.network.byJwt)

                    verifyInProgress = true

                    loginActivity?.authClientAndFinish { error ->
                        verifyInProgress = false

                        verifyError = error
                    }
                } else {
                    verifyError = context.getString(R.string.verify_error)
                }

                if (verifyError != null) {
                    code = List(codeLength) { "" }
                }
            }
        }
    }

    LaunchedEffect(code) {

        val codeStr = code.joinToString("")

        if (codeStr.length == 8 && !verifyInProgress) {
            verify()
        }

    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("You've got mail", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Tell us who you really are. Enter the code we",
                color = TextMuted
            )
            Text(
                "sent you to verify your identity.",
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(40.dp))

            URCodeInput(
                value = code,
                onValueChange = { newCode ->
                    code = newCode
                },
                codeLength = codeLength,
                enabled = !verifyInProgress
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't see it?",
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(4.dp))
                ClickableText(
                    text = AnnotatedString("Resend code"),
                    onClick = {
                        if (resendBtnEnabled) {
                            resendCode()
                        }
                    },
                    style = TextStyle(
                        color = if (resendBtnEnabled) Color.White else TextMuted,
                        fontSize = 16.sp
                    ),

                    )
            }
        }

        URSnackBar(
            type = if (markResendAsSent) SnackBarType.SUCCESS else SnackBarType.ERROR,
            isVisible = verifyError != null,
            onDismiss = {
                if (verifyError != null) {
                    verifyError = null
                }
                if (resendError != null) {
                    resendError = null
                }
                if (markResendAsSent) {
                    markResendAsSent = false
                }
            }
        ) {
            if (markResendAsSent) {
                Column() {
                    Text("Verification email sent to $userAuth")
                }
            } else {
                Column() {
                    Text("Something went wrong.")
                    Text("Please wait a few minutes and try again.")
                }
            }
        }
    }
}

@Preview
@Composable
fun LoginVerifyPreview() {

    val navController = rememberNavController()

    URNetworkTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LoginVerify(
                    userAuth = "hello@ur.io",
                    navController
                )
            }
        }
    }
}