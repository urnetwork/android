package com.bringyour.network.ui.settings

import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URCodeInput
import com.bringyour.network.ui.components.URInlineErrorText
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.login.SSO_OAUTH_PURPOSE_ADD
import com.bringyour.network.ui.login.SsoProvider
import com.bringyour.network.ui.login.ResendCode
import com.bringyour.network.ui.login.SolanaChallengeSignResult
import com.bringyour.network.ui.login.VerifySendError
import com.bringyour.network.ui.login.VerifySendNotice
import com.bringyour.network.ui.login.launchAppleOAuth
import com.bringyour.network.ui.login.launchBittensorBridge
import com.bringyour.network.ui.login.requestAndSignSolanaChallenge
import com.bringyour.network.ui.login.ssoJwtPayload
import com.bringyour.network.ui.login.ssoOAuthAttempts
import com.bringyour.network.ui.login.toVerifySendError
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.wallet.BittensorProofFlow
import com.bringyour.network.ui.wallet.BittensorProofSheets
import com.bringyour.network.ui.wallet.BittensorWallets
import com.bringyour.network.ui.wallet.bittensorWalletDisplayName
import com.bringyour.sdk.AddAuthArgs
import com.bringyour.sdk.AuthVerifyArgs
import com.bringyour.sdk.AuthVerifySendArgs
import com.bringyour.sdk.WalletAuthArgs
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val verifyCodeLength = 6

/**
 * Adds a sign-in method to the current network (Settings, and a legacy guest's
 * in-place conversion through GuestConversionSheet). The options are the same
 * as every app's ([addAuthMethods]): Apple, Google, a Solana or Bittensor
 * wallet, and an email or phone. Apple, Google and wallet sign-ins are added
 * once AddAuth succeeds. An email or phone is added unverified, so the
 * sheet then sends a code and asks for it (AddSignInFlow); `onAdded` runs only
 * after authVerify accepts the code.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAuthMethodSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    // Apple and Google, as on the flavor's login screen (every flavor now;
    // github adds Google through the browser)
    showSsoOptions: Boolean,
    activityResultSender: ActivityResultSender?,
    isAddingAuth: Boolean,
    addAuth: (AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onAdded: () -> Unit,
) {
    if (!visible) {
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val verifyErrMsg = stringResource(id = R.string.verify_error)

    // bumped on every flow change so the sheet recomposes
    var flowVersion by remember { mutableIntStateOf(0) }
    var resendRequested by remember { mutableStateOf(false) }
    val flow = remember {
        AddSignInFlow(
            object : AddSignInSession<AddAuthArgs> {
                override fun addAuth(args: AddAuthArgs, onSuccess: () -> Unit, onError: (String) -> Unit) {
                    addAuth(args, onSuccess, onError)
                }

                override fun sendCode(
                    userAuth: String,
                    done: (transportError: Boolean, sendError: VerifySendError?) -> Unit,
                ) {
                    val api = (context.applicationContext as? MainApplication)?.api
                    if (api == null) {
                        done(true, null)
                        return
                    }
                    val args = AuthVerifySendArgs()
                    args.userAuth = userAuth
                    args.useNumeric = true
                    // a rate limit or failed send comes back in `result.error`, with the retry time
                    args.resultErrors = true
                    api.authVerifySend(args) { result, err ->
                        scope.launch {
                            val transportError = err != null || result == null
                            val sendError = result?.error?.toVerifySendError()
                            if (resendRequested && !transportError && sendError == null) {
                                Toast.makeText(context, context.getString(R.string.verification_code_sent_2), Toast.LENGTH_SHORT).show()
                            }
                            resendRequested = false
                            done(transportError, sendError)
                        }
                    }
                }

                override fun verifyCode(userAuth: String, code: String, done: (error: String?) -> Unit) {
                    val api = (context.applicationContext as? MainApplication)?.api
                    if (api == null) {
                        done(verifyErrMsg)
                        return
                    }
                    val args = AuthVerifyArgs()
                    args.userAuth = userAuth
                    args.verifyCode = code
                    // the jwt in the result is not installed: the session stays on this network
                    api.authVerify(args) { result, err ->
                        scope.launch {
                            done(
                                when {
                                    err != null -> err.message ?: verifyErrMsg
                                    result == null -> verifyErrMsg
                                    result.error != null -> result.error.message ?: verifyErrMsg
                                    else -> null
                                }
                            )
                        }
                    }
                }
            },
            nowMillis = System::currentTimeMillis,
        ).apply {
            onChanged = { flowVersion += 1 }
        }
    }
    var code by remember { mutableStateOf(List(verifyCodeLength) { "" }) }
    // read so every flow change recomposes
    @Suppress("UNUSED_VARIABLE") val observedFlowVersion = flowVersion
    val verifying = flow.step == AddSignInStep.ENTER_CODE || flow.step == AddSignInStep.VERIFYING

    val methods = remember(showSsoOptions) { addAuthMethods(showSsoOptions) }
    var selectedMethod by remember(methods) { mutableStateOf(methods.first()) }

    var email by remember { mutableStateOf(TextFieldValue("")) }
    var password by remember { mutableStateOf(TextFieldValue("")) }
    var addError by remember { mutableStateOf<String?>(null) }

    var walletConnectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isConnectingWallet by remember { mutableStateOf(false) }

    val addWallet: (WalletAuthArgs) -> Unit = { walletAuth ->
        val args = AddAuthArgs()
        args.walletAuth = walletAuth
        flow.add(
            AddedSignInMethod.WALLET,
            args,
            "",
            {
                Toast.makeText(context, context.getString(R.string.wallet_sign_in_method_added), Toast.LENGTH_SHORT).show()
                onAdded()
            },
            { msg -> addError = msg }
        )
    }

    val bittensorAdd = remember {
        BittensorAddSignInController(
            flow = BittensorProofFlow(nowMillis = System::currentTimeMillis),
            scope = scope,
            api = { (context.applicationContext as? MainApplication)?.api },
            setError = { addError = it },
            defaultError = { context.getString(R.string.error_connecting_to_wallet) },
            addWalletAuth = { auth ->
                val walletAuth = WalletAuthArgs()
                walletAuth.blockchain = auth.blockchain
                walletAuth.publicKey = auth.publicKey
                walletAuth.message = auth.message
                walletAuth.signature = auth.signature
                addWallet(walletAuth)
            },
            openUrl = { url -> launchBittensorBridge(context, url) },
            refusalError = { failed ->
                BittensorWallets.refusalText(
                    failed.code,
                    failed.detail,
                    failed.bridgeCode,
                    bittensorWalletDisplayName(failed.walletId),
                ) { res, walletName ->
                    if (walletName == null) context.getString(res) else context.getString(res, walletName)
                }
            },
        )
    }

    // back from the WalletConnect page: a bridge that already returned is done
    LifecycleResumeEffect(bittensorAdd) {
        bittensorAdd.onResumed()
        onPauseOrDispose {}
    }

    // a WalletConnect bridge return for this sheet, handed over by the LoginActivity
    val bittensorReturn by BittensorAddSignInReturns.pending.collectAsState()
    LaunchedEffect(bittensorReturn) {
        BittensorAddSignInReturns.take()?.let { bittensorAdd.handleReturn(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            walletConnectJob?.cancel()
            bittensorAdd.flow.dismiss()
        }
    }

    // the browser flows (Apple everywhere, Google on github) return through the
    // LoginActivity (ur://oauth/<provider>), which hands an add attempt's
    // return here; a login never takes it. Adding never changes the session jwt.
    val ssoReturn by SsoAddSignInReturns.pending.collectAsState()
    LaunchedEffect(ssoReturn) {
        val r = SsoAddSignInReturns.take() ?: return@LaunchedEffect
        val outcome = ssoAddOutcome(r.provider, ssoOAuthAttempts(context, r.provider), r.ssoReturn) { idToken ->
            ssoJwtPayload(idToken)?.optString("nonce")
        }
        when (outcome) {
            is SsoAddOutcome.Add -> {
                val args = AddAuthArgs()
                args.authJwt = outcome.auth.authJwt
                args.authJwtType = outcome.auth.authJwtType
                val (method, addedMessage) = when (r.provider) {
                    SsoProvider.APPLE -> AddedSignInMethod.APPLE to R.string.apple_sign_in_method_added
                    SsoProvider.GOOGLE -> AddedSignInMethod.GOOGLE to R.string.google_sign_in_method_added
                }
                flow.add(
                    method,
                    args,
                    "",
                    {
                        Toast.makeText(context, context.getString(addedMessage), Toast.LENGTH_SHORT).show()
                        onAdded()
                    },
                    { msg -> addError = msg }
                )
            }
            is SsoAddOutcome.Failed -> {
                addError = outcome.error ?: context.getString(R.string.login_error)
            }
            SsoAddOutcome.Stray -> {}
        }
    }

    // a full code verifies; a rejected one is cleared so it can be retyped
    LaunchedEffect(code) {
        val codeStr = code.joinToString("")
        if (codeStr.length == verifyCodeLength && !flow.busy) {
            flow.submitCode(codeStr)
        }
    }
    LaunchedEffect(flow.verifyError) {
        if (flow.verifyError != null) {
            code = List(verifyCodeLength) { "" }
        }
    }
    // re-enables Resend and counts a rate limit down
    LaunchedEffect(flowVersion) {
        while (flow.resendWaitMillis() != null) {
            delay(1000L)
            flowVersion += 1
        }
    }

    val formValid = when (selectedMethod) {
        AddAuthMethod.EMAIL -> email.text.isNotBlank() && password.text.length >= 12
        else -> true
    }

    val onAddClick: () -> Unit = {
        addError = null
        when (selectedMethod) {
            AddAuthMethod.EMAIL -> {
                val args = AddAuthArgs()
                args.userAuth = email.text
                args.password = password.text
                code = List(verifyCodeLength) { "" }
                flow.add(
                    AddedSignInMethod.PASSWORD,
                    args,
                    email.text,
                    {
                        Toast.makeText(context, context.getString(R.string.sign_in_method_added_successfully), Toast.LENGTH_SHORT).show()
                        onAdded()
                    },
                    { msg -> addError = msg }
                )
            }
            else -> { /* Google/Wallet complete on their own callback, no explicit Add click */ }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (verifying) {
                AddedSignInVerifyStep(
                    flow = flow,
                    code = code,
                    onCodeChange = { newCode ->
                        code = newCode
                        flow.clearVerifyError()
                    },
                    onResend = {
                        resendRequested = true
                        if (!flow.resend()) {
                            resendRequested = false
                        }
                    },
                )
            } else {
                Text(
                    stringResource(id = R.string.add_a_sign_in_method),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    stringResource(id = R.string.link_another_way_to_sign_in_to),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    methods.forEach { method ->
                        // URButton has no `modifier` parameter (checked against its
                        // real signature in URButton.kt) — wrap it in a weighted Box
                        // instead of trying to pass modifier through to URButton itself.
                        androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                            URButton(
                                style = if (method == selectedMethod) ButtonStyle.PRIMARY else ButtonStyle.SECONDARY,
                                onClick = {
                                    selectedMethod = method
                                    addError = null
                                }
                            ) { buttonTextStyle ->
                                Text(
                                    when (method) {
                                        AddAuthMethod.APPLE -> stringResource(id = R.string.apple)
                                        AddAuthMethod.GOOGLE -> stringResource(id = R.string.google)
                                        AddAuthMethod.WALLET -> stringResource(id = R.string.wallet)
                                        AddAuthMethod.EMAIL -> stringResource(id = R.string.site_app_email)
                                    },
                                    style = buttonTextStyle
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedMethod) {
                    AddAuthMethod.GOOGLE -> {
                        // Google Sign-In is per-flavor code (com.google.android.gms.*
                        // is not on github's classpath) -- every flavor's source set
                        // provides its own GoogleAddAuthButton with this exact
                        // signature (Play services on google/solana_dapp/ethos_dapp,
                        // the browser flow on ungoogle/github, whose return
                        // arrives through SsoAddSignInReturns above).
                        GoogleAddAuthButton(
                            addAuth = { args, onSuccess, onError ->
                                flow.add(AddedSignInMethod.GOOGLE, args, "", onSuccess, onError)
                            },
                            isAddingAuth = isAddingAuth || flow.busy,
                            onAdded = onAdded,
                            onError = { msg -> addError = msg }
                        )
                    }
                    AddAuthMethod.APPLE -> {
                        // Apple has no Android SDK: the login's web flow in a Custom Tab,
                        // started as an add attempt (AppleOAuthSession purpose add)
                        Text(
                            stringResource(id = R.string.sign_in_with_your_apple_id_to),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        URButton(
                            onClick = {
                                addError = null
                                val apiUrl = (context.applicationContext as? MainApplication)
                                    ?.networkSpaceManagerProvider?.getNetworkSpace()?.apiUrl
                                if (!launchAppleOAuth(context, apiUrl, SSO_OAUTH_PURPOSE_ADD)) {
                                    addError = context.getString(R.string.login_error)
                                }
                            },
                            enabled = !isAddingAuth && !flow.busy,
                            isProcessing = flow.busy
                        ) { buttonTextStyle ->
                            Text(stringResource(id = R.string.sign_in_with_apple), style = buttonTextStyle)
                        }
                    }
                    AddAuthMethod.WALLET -> {
                        Text(
                            stringResource(id = R.string.connect_solana_wallet_to_add_sign_in_method),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(id = R.string.connect_bittensor_wallet_to_add_sign_in_method),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            addAuthWalletChains.forEach { chain ->
                                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                                    when (chain) {
                                        AddAuthWalletChain.SOLANA -> URButton(
                                            onClick = {
                                                walletConnectJob = scope.launch {
                                                    activityResultSender?.let { sender ->
                                                        val api = (context.applicationContext as? MainApplication)?.api
                                                        if (api == null) {
                                                            addError = context.getString(R.string.error_connecting_to_wallet)
                                                            return@launch
                                                        }
                                                        isConnectingWallet = true
                                                        when (val result = requestAndSignSolanaChallenge(sender, api)) {
                                                            is SolanaChallengeSignResult.Success -> {
                                                                val walletAuth = WalletAuthArgs()
                                                                walletAuth.publicKey = result.signed.publicKey
                                                                walletAuth.signature = result.signed.signature
                                                                walletAuth.message = result.signed.message
                                                                walletAuth.blockchain = "solana"
                                                                addWallet(walletAuth)
                                                            }
                                                            is SolanaChallengeSignResult.NoWalletFound -> {
                                                                addError = context.getString(R.string.no_compatible_wallet_app_found)
                                                            }
                                                            is SolanaChallengeSignResult.Failure -> {
                                                                Log.i("AddAuthMethodSheet", "Error connecting to wallet: ${result.error}")
                                                                addError = context.getString(R.string.error_connecting_to_wallet)
                                                            }
                                                        }
                                                        isConnectingWallet = false
                                                    }
                                                }
                                            },
                                            style = ButtonStyle.SECONDARY,
                                            enabled = !isAddingAuth && !isConnectingWallet && !flow.busy,
                                            isProcessing = isConnectingWallet
                                        ) { buttonTextStyle ->
                                            Text(stringResource(id = R.string.solana_wallet), style = buttonTextStyle)
                                        }
                                        AddAuthWalletChain.BITTENSOR -> URButton(
                                            onClick = { bittensorAdd.start() },
                                            style = ButtonStyle.SECONDARY,
                                            enabled = !isAddingAuth && !isConnectingWallet && !flow.busy,
                                        ) { buttonTextStyle ->
                                            Text(stringResource(id = R.string.bittensor_wallet), style = buttonTextStyle)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AddAuthMethod.EMAIL -> {
                        URTextInput(
                            value = email,
                            onValueChange = { email = it },
                            label = stringResource(id = R.string.site_app_email),
                            placeholder = stringResource(id = R.string.your_email_com),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        URTextInput(
                            value = password,
                            onValueChange = { password = it },
                            label = stringResource(id = R.string.password_label),
                            placeholder = stringResource(id = R.string.enter_a_password),
                            isPassword = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(id = R.string.password_support_txt),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                if (addError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    URInlineErrorText(addError)
                }

                if (selectedMethod == AddAuthMethod.EMAIL) {
                    Spacer(modifier = Modifier.height(16.dp))
                    URButton(
                        onClick = onAddClick,
                        enabled = !isAddingAuth && formValid,
                        isProcessing = isAddingAuth
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.add_sign_in_method_2), style = buttonTextStyle)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // the wallet chooser and manual proof, the login's "Sign in with Bittensor" sheets
    BittensorProofSheets(
        flow = bittensorAdd.flow,
        onChoose = bittensorAdd::choose,
        onSubmit = bittensorAdd::submit,
    )
}

/**
 * The code step after an email or phone was added: the code input, the verify
 * error, and Resend with the send notice (LoginVerify's).
 */
@Composable
private fun AddedSignInVerifyStep(
    flow: AddSignInFlow<*>,
    code: List<String>,
    onCodeChange: (List<String>) -> Unit,
    onResend: () -> Unit,
) {
    val isEmail = Patterns.EMAIL_ADDRESS.matcher(flow.userAuth).matches()
    val noticeText = when (val notice = flow.noticeNow()) {
        null, VerifySendNotice.Sent -> null
        VerifySendNotice.SendFailed -> stringResource(id = R.string.error_sending_verification_code)
        is VerifySendNotice.RateLimited -> pluralStringResource(
            id = R.plurals.verify_code_rate_limited,
            count = notice.minutes,
            notice.minutes,
        )
        is VerifySendNotice.ServerMessage -> notice.message
    }

    // both lines say a code was sent
    if (flow.codeSent) {
        Text(
            stringResource(id = if (isEmail) R.string.login_verify_header else R.string.login_verify_check_phone),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.login_verify_details),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    URCodeInput(
        value = code,
        onValueChange = onCodeChange,
        codeLength = verifyCodeLength,
        enabled = !flow.busy,
    )

    Spacer(modifier = Modifier.height(8.dp))
    URInlineErrorText(flow.verifyError)

    Spacer(modifier = Modifier.height(16.dp))

    ResendCode(
        resendCode = onResend,
        resendBtnEnabled = flow.canResend(),
        resendInProgress = flow.sending,
        resendError = noticeText
    )
}
