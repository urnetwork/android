package com.bringyour.network.ui.login

import com.bringyour.network.ui.components.tabletForm
import android.util.Patterns
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.bringyour.sdk.AuthVerifyArgs
import com.bringyour.sdk.AuthVerifySendArgs
import com.bringyour.sdk.AuthVerifySendError
import com.bringyour.network.LoginActivity
import com.bringyour.network.LoginClientCompletion
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.URCodeInput
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.overlays.WelcomeAnimatedOverlayLogin
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginVerify(
    userAuth: String,
    navController: NavController,
    // set when the login or sign-up that opened this screen did not send a code
    sendError: VerifySendError? = null,
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication
    val loginActivity = context as? LoginActivity
    val codeLength = 6
    var code by remember { mutableStateOf(List(codeLength) { "" }) }
    var resendInProgress by remember { mutableStateOf(false) }
    var markResendAsSent by remember { mutableStateOf(false) }
    // the outcome of the last code send: the one that opened this screen, then each resend
    var sendNotice by remember { mutableStateOf(VerifySendNotice.from(false, sendError)) }
    var codeSent by remember { mutableStateOf(sendNotice == VerifySendNotice.Sent) }
    // after a rate limit, Resend waits until the server will send a new code
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var resendCooldown by remember { mutableStateOf(ResendCooldown.after(sendError, nowMillis)) }
    var verifyInProgress by remember { mutableStateOf(false) }
    val resendBtnEnabled by remember {
        derivedStateOf {
            !resendInProgress &&
                    !verifyInProgress &&
                    !markResendAsSent &&
                    resendCooldown?.canResend(nowMillis) != false
        }
    }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var welcomeOverlayVisible by remember { mutableStateOf(false) }
    var isContentVisible by remember { mutableStateOf(true) }
    val isEmail = Patterns.EMAIL_ADDRESS.matcher(userAuth).matches()
    val titleSize: TextUnit = dimensionResource(id = R.dimen.login_title_size).value.sp
    val sendNoticeText = when (val notice = sendNotice.at(resendCooldown, nowMillis)) {
        VerifySendNotice.Sent -> null
        VerifySendNotice.SendFailed -> stringResource(id = R.string.error_sending_verification_code)
        is VerifySendNotice.RateLimited -> pluralStringResource(
            id = R.plurals.verify_code_rate_limited,
            count = notice.minutes,
            notice.minutes,
        )
        is VerifySendNotice.ServerMessage -> notice.message
    }
    val verifyErrMsg = stringResource(id = R.string.verify_error)

    val scope = rememberCoroutineScope()

    val resendCode: () -> Unit = resendCode@{
        if (!resendBtnEnabled) {
            return@resendCode
        }

        sendNotice = VerifySendNotice.Sent
        resendInProgress = true

        val args = AuthVerifySendArgs()
        args.userAuth = userAuth
        args.useNumeric = true
        // a rate limit or failed send comes back in `result.error`, with the retry time
        args.resultErrors = true

        application?.api?.authVerifySend(args) { result, err ->
            scope.launch {

                resendInProgress = false

                val error = result?.error?.toVerifySendError()
                sendNotice = VerifySendNotice.from(err != null || result == null, error)
                nowMillis = System.currentTimeMillis()
                resendCooldown = ResendCooldown.after(error, nowMillis)
                if (sendNotice == VerifySendNotice.Sent) {
                    codeSent = true
                    markResendAsSent = true
                    Toast.makeText(
                        context,
                        context.getString(R.string.verification_code_sent_2),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } ?: run {
            resendInProgress = false
            sendNotice = VerifySendNotice.SendFailed
        }
    }

    val verify: () -> Unit = verify@{
        if (verifyInProgress || resendInProgress) {
            return@verify
        }

        verifyError = null
        verifyInProgress = true

        val args = AuthVerifyArgs()
        args.userAuth = userAuth
        args.verifyCode = code.joinToString("")

        application?.api?.authVerify(args) { result, err ->
            application.dispatchLoginResult {
                if (err != null) {
                    scope.launch {
                        verifyInProgress = false
                        verifyError = err.message
                        code = List(codeLength) { "" }
                    }
                } else if (result == null) {
                    scope.launch {
                        verifyInProgress = false
                        verifyError = verifyErrMsg
                        code = List(codeLength) { "" }
                    }
                } else if (result.error != null) {
                    scope.launch {
                        verifyInProgress = false
                        verifyError = result.error.message
                        code = List(codeLength) { "" }
                    }
                } else if (result.network != null && result.network.byJwt.isNotEmpty()) {
                    // a verified sign-up is a new network: it gets the onboarding flow
                    application.authenticateNetworkSession(
                        result.network.byJwt,
                        newNetwork = true,
                    ) { completion ->
                        if (completion is LoginClientCompletion.Ready) {
                            loginActivity?.finishAuthenticatedLoginAfterWelcome()
                        }
                        scope.launch {
                            val error = (completion as? LoginClientCompletion.Failed)?.message
                            if (completion is LoginClientCompletion.Ready) {
                                verifyError = null
                                isContentVisible = false
                                delay(500)
                                welcomeOverlayVisible = true
                                return@launch
                            }
                            verifyInProgress = false
                            verifyError = error ?: verifyErrMsg
                            val visibility = loginRetryVisibility()
                            isContentVisible = visibility.contentVisible
                            welcomeOverlayVisible = visibility.welcomeOverlayVisible
                            code = List(codeLength) { "" }
                        }
                    }
                } else {
                    scope.launch {
                        verifyInProgress = false
                        verifyError = verifyErrMsg
                        code = List(codeLength) { "" }
                    }
                }
            }
        } ?: run {
            verifyInProgress = false
            verifyError = verifyErrMsg
        }
    }

    LaunchedEffect(markResendAsSent) {
        if (markResendAsSent) {
            delay(30000L)
            markResendAsSent = false
        }
    }

    // tick the rate-limit countdown until Resend is available again
    LaunchedEffect(resendCooldown) {
        while (resendCooldown?.canResend(nowMillis) == false) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    LaunchedEffect(code) {

        val codeStr = code.joinToString("")

        if (codeStr.length == codeLength && !verifyInProgress && !resendInProgress) {
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
                        .tabletForm()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Column(
                        modifier = Modifier.imePadding()
                    ) {
                        // both lines say a code was sent
                        if (codeSent) {
                            Text(
                                stringResource(id =
                                    if (isEmail) R.string.login_verify_header
                                    else R.string.login_verify_check_phone
                                ),
                                style = MaterialTheme.typography.headlineLarge,
                                fontSize = titleSize
                            )

                            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.login_margin_lg)))

                            Text(
                                stringResource(id = R.string.login_verify_details),
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        URCodeInput(
                            value = code,
                            onValueChange = { newCode ->
                                code = newCode
                                verifyError = null
                            },
                            codeLength = codeLength,
                            enabled = !verifyInProgress && !resendInProgress,
                            modifier = Modifier.testTag("acceptance.verify.code")
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        URInlineErrorText(verifyError)

                        Spacer(modifier = Modifier.height(32.dp))

                        ResendCode(
                            resendCode = {
                                resendCode()
                            },
                            resendBtnEnabled = resendBtnEnabled,
                            resendInProgress = resendInProgress,
                            resendError = sendNoticeText
                        )
                    }
                }
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
    resendBtnEnabled: Boolean,
    resendInProgress: Boolean,
    resendError: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
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

            if (resendInProgress) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = TextMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        URInlineErrorText(resendError)
    }
}

fun AuthVerifySendError.toVerifySendError() = VerifySendError(
    code = code ?: "",
    message = message ?: "",
    retryAfterSeconds = retryAfterSeconds,
)

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
