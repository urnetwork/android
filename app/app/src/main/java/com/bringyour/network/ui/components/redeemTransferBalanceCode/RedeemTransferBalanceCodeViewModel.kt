package com.bringyour.network.ui.components.redeemTransferBalanceCode

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.RedeemBalanceCodeArgs
import com.bringyour.sdk.RedeemBalanceCodeResult
import com.bringyour.sdk.RedeemedBalanceCodeList
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Why a redeem failed. These MUST stay distinguishable: a transport failure can
 * arrive after the server already committed the redeem, so reporting it as "bad
 * code" tells the user their (consumed) code is invalid. The server's `result.error`
 * payload, by contrast, is an authoritative rejection.
 */
sealed class RedeemBalanceCodeFailure {
    /** No server answer (network failure, device/api not up). The redeem may have committed. */
    object Transport : RedeemBalanceCodeFailure()

    /** This network already redeemed the code: the data is already on its balance. */
    object AlreadyRedeemed : RedeemBalanceCodeFailure()

    /** Server answered: unknown/invalid code (or one another network redeemed). */
    object Invalid : RedeemBalanceCodeFailure()
}

/**
 * The UI result for an SDK BalanceCodeRedeemOutcome* value; null is success.
 * `unknown` (the call failed and the redeemed-code list does not show the code)
 * stays a transport failure: the redeem may still have committed.
 */
internal fun redeemFailureForOutcome(outcome: String): RedeemBalanceCodeFailure? =
    when (outcome) {
        Sdk.BalanceCodeRedeemOutcomeRedeemed -> null
        Sdk.BalanceCodeRedeemOutcomeAlreadyRedeemed -> RedeemBalanceCodeFailure.AlreadyRedeemed
        Sdk.BalanceCodeRedeemOutcomeInvalid -> RedeemBalanceCodeFailure.Invalid
        else -> RedeemBalanceCodeFailure.Transport
    }

/**
 * Redeem, then classify with the network's own redeemed-code list (UPGRADE.md N7).
 *
 * The server answers "Unknown balance code." both for a code that does not exist and
 * for one already redeemed, so the old message match on "already"/"redeemed" never
 * fired: a retry after a network failure that had committed told the user their
 * consumed code was invalid. The classification is the SDK's
 * (Sdk.classifyBalanceCodeRedeem); the redeemed-code list is fetched only when the
 * redeem did not plainly succeed. Generic over the SDK types so it runs without the
 * native library.
 */
internal class BalanceCodeRedeemFlow<R, L>(
    private val redeem: (secret: String, callback: (result: R?, error: Exception?) -> Unit) -> Unit,
    private val fetchRedeemedCodes: (callback: (redeemedCodes: L?) -> Unit) -> Unit,
    private val classify: (result: R?, redeemedCodes: L?, secret: String) -> String,
) {
    fun run(secret: String, onResult: (RedeemBalanceCodeFailure?) -> Unit) {
        redeem(secret) { result, error ->
            // a transport error carries no authoritative answer
            val answer = if (error != null) null else result
            val firstOutcome = redeemFailureForOutcome(classify(answer, null, secret))
            if (firstOutcome == null) {
                onResult(null)
                return@redeem
            }
            fetchRedeemedCodes { redeemedCodes ->
                onResult(redeemFailureForOutcome(classify(answer, redeemedCodes, secret)))
            }
        }
    }
}

@HiltViewModel
class RedeemTransferBalanceCodeViewModel @Inject constructor(
    deviceManager: DeviceManager
): ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _code = MutableStateFlow(TextFieldValue(""))
    val code: TextFieldValue get() = _code.value

    val onTextChanged: (newCode: TextFieldValue) -> Unit = {
        _code.value = it
        // the SDK owns the code format (trimmed length), shared with every app
        _codeIsValid.value = Sdk.isBalanceCodeFormatValid(it.text)
    }

    private val _codeIsValid = MutableStateFlow(false)
    val codeIsValid: StateFlow<Boolean> = _codeIsValid.asStateFlow()

    val redeem: (
            onSuccess: () -> Unit,
            onError: (RedeemBalanceCodeFailure) -> Unit
            ) -> Unit = { onSuccess, onError ->

        if (!_isLoading.value && _codeIsValid.value) {

            _isLoading.value = true

            val api = deviceManager.device?.api
            if (api != null) {
                val flow = BalanceCodeRedeemFlow<RedeemBalanceCodeResult, RedeemedBalanceCodeList>(
                    redeem = { secret, callback ->
                        val args = RedeemBalanceCodeArgs()
                        args.secret = secret
                        api.redeemBalanceCode(args) { result, error -> callback(result, error) }
                    },
                    fetchRedeemedCodes = { callback ->
                        api.getNetworkRedeemedBalanceCodes { result, error ->
                            callback(if (error == null && result?.error == null) result?.balanceCodes else null)
                        }
                    },
                    classify = { result, redeemedCodes, secret ->
                        Sdk.classifyBalanceCodeRedeem(result, redeemedCodes, secret)
                    },
                )
                flow.run(code.text.trim()) { failure ->
                    viewModelScope.launch {
                        _isLoading.value = false
                        if (failure == null) {
                            onSuccess()
                        } else {
                            onError(failure)
                        }
                    }
                }
            } else {
                _isLoading.value = false
                onError(RedeemBalanceCodeFailure.Transport)
            }

        }

    }

}
