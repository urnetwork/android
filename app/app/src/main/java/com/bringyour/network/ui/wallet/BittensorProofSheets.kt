package com.bringyour.network.ui.wallet

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.network.BuildConfig
import com.bringyour.network.R
import com.bringyour.network.ui.login.launchBittensorBridge
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.components.URTextInput
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.SheetBlack
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.sdk.Api
import com.bringyour.sdk.BittensorWalletSession
import com.bringyour.sdk.Sdk
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "BittensorProof"

/** The SDK session behind [BittensorProofSession]. */
class SdkBittensorProofSession(
    private val session: BittensorWalletSession,
) : BittensorProofSession {
    override val walletId: String = session.walletId()
    override val purpose: String = session.purpose()
    override val message: String get() = session.message()
    override val transport: String = session.transport()

    override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
        outcome(session.handleSignature(address, signature, nowMillis))

    override fun bridgeUrl(): String? = try {
        session.bridgeUrl()
    } catch (e: Exception) {
        Log.i(TAG, "bridge url: ${e.message}")
        null
    }

    override fun handleBridgeReturn(uri: String, nowMillis: Long): BittensorProofOutcome =
        outcome(session.handleBridgeReturn(uri, nowMillis))

    private fun outcome(result: com.bringyour.sdk.BittensorWalletResult): BittensorProofOutcome {
        val proof = result.proof
        if (result.errorCode.isNullOrEmpty() && proof != null) {
            return BittensorProofOutcome.Proven(
                BittensorProof(
                    walletId = proof.walletId,
                    purpose = proof.purpose,
                    address = proof.address,
                    message = proof.message,
                    signature = proof.signature,
                )
            )
        }
        return BittensorProofOutcome.Refused(
            result.errorCode ?: "",
            result.errorMessage?.takeIf { it.isNotEmpty() },
            result.bridgeErrorCode?.takeIf { it.isNotEmpty() },
        )
    }
}

/**
 * Starts the SDK session for a request: the session, then the single-use
 * /auth/wallet-challenge (bound to the expected address when there is one).
 */
suspend fun startBittensorProofSession(
    api: Api,
    request: BittensorProofRequest,
    nowMillis: () -> Long = System::currentTimeMillis,
    // the WalletConnect Cloud project id (local.properties) the bridge page pairs with
    walletConnectProjectId: String = BuildConfig.WALLETCONNECT_PROJECT_ID,
): Result<BittensorProofSession> {
    val session = try {
        Sdk.newBittensorWalletSession(
            request.walletId,
            BittensorWallets.PLATFORM,
            request.purpose,
            BittensorWallets.REDIRECT_LINK,
        )
    } catch (e: Exception) {
        return Result.failure(e)
    }
    session.setWalletConnectProjectId(walletConnectProjectId)
    val args = session.challengeArgs(request.expectedAddress ?: "")
    return suspendCancellableCoroutine { continuation ->
        api.authWalletChallenge(args) { result, err ->
            if (!continuation.isActive) {
                return@authWalletChallenge
            }
            if (err != null) {
                continuation.resume(Result.failure(err))
                return@authWalletChallenge
            }
            try {
                session.setChallenge(result, nowMillis())
                continuation.resume(Result.success(SdkBittensorProofSession(session)))
            } catch (e: Exception) {
                Log.i(TAG, "wallet challenge: ${e.message}")
                continuation.resume(Result.failure(e))
            }
        }
    }
}

/**
 * Starts a browser-bridge proof outside the chooser (the create-network second
 * signature after a WalletConnect sign-in): the session waits in
 * [BittensorBridgeReturns] and the page opens in a Custom Tab.
 */
suspend fun startBittensorBridgeProof(
    context: Context,
    api: Api,
    request: BittensorProofRequest,
    bridgeReturns: BittensorBridgeReturns = BittensorBridgeReturns.shared,
): Boolean {
    val session = startBittensorProofSession(api, request).getOrNull() ?: return false
    val url = session.bridgeUrl() ?: return false
    bridgeReturns.begin(session)
    if (!launchBittensorBridge(context, url)) {
        bridgeReturns.cancel()
        return false
    }
    return true
}

/** The product name (not translated). */
fun bittensorWalletDisplayName(walletId: String): String = Sdk.bittensorWalletDisplayName(walletId)

/**
 * The wallet chooser and the manual proof sheet for a [BittensorProofFlow].
 * `onChoose` starts the session for the chosen wallet; `onSubmit` hands the
 * pasted answer to the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BittensorProofSheets(
    flow: BittensorProofFlow,
    onChoose: (String) -> Unit,
    onSubmit: () -> Unit,
    displayName: (String) -> String = ::bittensorWalletDisplayName,
) {
    val stage by flow.stage.collectAsState()

    when (val s = stage) {
        BittensorProofStage.Hidden -> {}
        is BittensorProofStage.Choosing, is BittensorProofStage.Loading -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { flow.dismiss() },
                sheetState = sheetState,
                containerColor = SheetBlack,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.bittensor_choose_wallet),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val loadingWalletId = (s as? BittensorProofStage.Loading)?.request?.walletId
                    BittensorWallets.walletIds.forEach { walletId ->
                        URButton(
                            onClick = { onChoose(walletId) },
                            style = ButtonStyle.SECONDARY,
                            enabled = loadingWalletId == null,
                            isProcessing = loadingWalletId == walletId,
                        ) { buttonTextStyle ->
                            Text(displayName(walletId), style = buttonTextStyle)
                        }
                        BittensorWallets.subtitleRes(walletId)?.let { subtitleRes ->
                            Text(
                                stringResource(id = subtitleRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    (s as? BittensorProofStage.Choosing)?.errorRes?.let { errorRes ->
                        Text(
                            stringResource(id = errorRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Red
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        is BittensorProofStage.AwaitingBrowser -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { flow.dismiss() },
                sheetState = sheetState,
                containerColor = SheetBlack,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(displayName(s.walletId), style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(id = R.string.bittensor_walletconnect_continue),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    URButton(
                        onClick = { flow.dismiss() },
                        style = ButtonStyle.SECONDARY,
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.cancel), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        is BittensorProofStage.Signing -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val clipboardManager = LocalClipboardManager.current
            ModalBottomSheet(
                onDismissRequest = { flow.dismiss() },
                sheetState = sheetState,
                containerColor = SheetBlack,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        stringResource(id = R.string.bittensor_manual_sign_instructions, displayName(s.session.walletId)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(id = R.string.bittensor_message_to_sign),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(s.session.message, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    URButton(
                        onClick = { clipboardManager.setText(AnnotatedString(s.session.message)) },
                        style = ButtonStyle.SECONDARY,
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.copy), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    URTextInput(
                        value = TextFieldValue(s.address, TextRange(s.address.length)),
                        onValueChange = { flow.updateAddress(it.text) },
                        label = stringResource(id = R.string.bittensor_wallet),
                        placeholder = stringResource(id = R.string.earnings_address_placeholder),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    URTextInput(
                        value = TextFieldValue(s.signature, TextRange(s.signature.length)),
                        onValueChange = { flow.updateSignature(it.text) },
                        label = stringResource(id = R.string.bittensor_signature_label),
                        placeholder = stringResource(id = R.string.bittensor_signature_placeholder),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        onDone = onSubmit,
                        isValid = s.errorRes == null,
                        supportingText = s.errorRes?.let { stringResource(id = it) },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    URButton(
                        onClick = onSubmit,
                        enabled = s.address.isNotBlank() && s.signature.isNotBlank(),
                    ) { buttonTextStyle ->
                        Text(stringResource(id = R.string.continue_txt), style = buttonTextStyle)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
