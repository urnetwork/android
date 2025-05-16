package com.bringyour.network.ui.login

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.AuthVerifyArgs
import com.bringyour.sdk.AuthVerifySendArgs
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.SnackBarType
import com.bringyour.network.ui.components.URCodeInput
import com.bringyour.network.ui.components.URSnackBar
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.isTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
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
    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(true) }
    val isEmail = Patterns.EMAIL_ADDRESS.matcher(userAuth).matches()

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

                    isContentVisible = false

                    delay(500)

                    welcomeOverlayVisible = true

                    delay(2250)

                    loginActivity?.authClientAndFinish({ error ->
                        verifyInProgress = false

                        verifyError = error
                    })
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


    AnimatedVisibility(
        visible = isContentVisible,
        enter = EnterTransition.None,
        exit = fadeOut()
    ) {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Black
                    ),
                    actions = {},
                )
            }
        ) { innerPadding ->

            // mobile + tablet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(top = 16.dp, start = 16.dp, bottom = 124.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(512.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Column(
                        modifier = Modifier.imePadding()
                    ) {
                        Text(
                            stringResource(id =
                                if (isEmail) R.string.login_verify_header
                                else R.string.login_verify_check_phone
                            ),
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            stringResource(id = R.string.login_verify_details),
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

                        ResendCode(
                            resendCode = {
                                resendCode()
                            },
                            resendBtnEnabled = resendBtnEnabled
                        )
                    }
                }
            }
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
                Text(stringResource(id = R.string.something_went_wrong))
                Text(stringResource(id = R.string.please_wait))
            }
        }
    }

    if (welcomeOverlayVisible) {
        WelcomeAnimatedOverlayLogin()
    }
}

@Composable
private fun ResendCode(
    resendCode: () -> Unit,
    resendBtnEnabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.dont_see_it),
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(4.dp))

        Text(
            stringResource(id = R.string.resend_verify_code),
            style = TextStyle(
                color = if (resendBtnEnabled) Color.White else TextMuted,
                fontSize = 16.sp
            ),
            modifier = Modifier.clickable {
                if (resendBtnEnabled) {
                    resendCode()
                }
            }
        )
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