package com.bringyour.network.ui.components.redeemTransferBalanceCode

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.utils.formatBalanceBytes
import com.bringyour.sdk.RedeemBalanceCodeArgs
import com.bringyour.sdk.RedeemBalanceCodeResult
import com.bringyour.sdk.RedeemedBalanceCodeList
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * Why a redeem failed. These must stay distinguishable: a transport failure can
 * arrive after the server already committed the redeem, so reporting it as "bad
 * code" tells the user their (consumed) code is invalid. The server's `result.error`
 * payload, by contrast, is an authoritative rejection.
 */
sealed class RedeemBalanceCodeFailure {
    /** No server answer (network failure, device/api not up). The redeem may have committed. */
    object Transport : RedeemBalanceCodeFailure()

    /**
     * The code is already redeemed: a data code by this network (its data is already
     * on the balance), or any code by another network, as the server's answer says.
     */
    object AlreadyRedeemed : RedeemBalanceCodeFailure()

    /** Server answered: unknown/invalid code (or one another network redeemed). */
    object Invalid : RedeemBalanceCodeFailure()
}

/**
 * The year of Pro a gift code granted (the redeem answer's `pro`). It starts at
 * the redeem, or, when `queued`, after the network's current paid Pro period.
 * A time the answer leaves out is null. After a lost answer it comes from the
 * network's redeemed list, which gives only the end (BalanceCodeRedeemFlow).
 */
data class RedeemedProGrant(
    val startTimeMillis: Long?,
    val endTimeMillis: Long?,
    val queued: Boolean,
)

/**
 * A credited redeem: what the code added to this network, from the server's answer.
 *
 * There are two kinds of code (GIFTOFPRIVACY.md section 5). A data code adds data
 * (transfer_balance.balance_byte_count) and no Pro. A Pro gift code adds a year of
 * Pro (`pro`); its answer still carries a transfer balance, with 0 bytes. So `pro`,
 * not the byte count, tells the two apart: it is null for a data code, and for any
 * code on an older server, which never answers with it.
 */
data class RedeemedBalanceCode(
    val addedByteCount: Long,
    val pro: RedeemedProGrant? = null,
)

/**
 * A Pro period's date in the device's locale and time zone ("Nov 13, 2027"), the
 * form the crypto term notice gives its renew-by date.
 */
internal fun formatProDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

/**
 * The confirmation for a credited redeem.
 *
 * - A gift code: "A year of Pro is yours." When the year is queued after a paid
 *   period, the day it starts instead. A queued answer without a start time falls
 *   back to the plain message, which is still true.
 * - A data code: "Balance code redeemed." and the data it added. An answer without
 *   a byte count (an older server) confirms the redeem alone.
 */
internal fun balanceCodeRedeemedMessage(
    redeemed: RedeemedBalanceCode,
    redeemedText: String,
    dataAddedText: (amount: String) -> String,
    proRedeemedText: String,
    proQueuedText: (startDate: String) -> String,
    formatBytes: (Long) -> String = formatBalanceBytes,
    formatDate: (Long) -> String = ::formatProDate,
): String {
    val pro = redeemed.pro
    if (pro != null) {
        val startTimeMillis = pro.startTimeMillis
        return if (pro.queued && startTimeMillis != null) {
            proQueuedText(formatDate(startTimeMillis))
        } else {
            proRedeemedText
        }
    }
    return if (0L < redeemed.addedByteCount) {
        "$redeemedText ${dataAddedText(formatBytes(redeemed.addedByteCount))}"
    } else {
        redeemedText
    }
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
 * A balance code as the server stores it and looks a typed one up
 * (GIFTOFPRIVACY.md 9.1, call 2): without whitespace, dashes or dots, in upper
 * case. A code typed as the card prints it ("abcd efgh-...") is the same code.
 */
internal fun normalizedBalanceCode(code: String): String =
    code.filterNot { it.isWhitespace() || it.category == CharCategory.DASH_PUNCTUATION || it == '.' }
        .uppercase()

/**
 * Whether a typed code can be redeemed: the SDK's format rule
 * (Sdk.isBalanceCodeFormatValid, its 26 characters) over the code normalized,
 * so a code typed as the card prints it, in groups of four with spaces (32
 * characters), enables Redeem. This SDK's rule only trims; the SDK normalizes
 * itself from the integration rebuild on, which normalizing first leaves as it
 * is. The redeem still sends the code as typed: the server tries it as given,
 * then normalized.
 */
internal fun isTypedBalanceCodeValid(
    typed: String,
    isFormatValid: (String) -> Boolean = Sdk::isBalanceCodeFormatValid,
): Boolean = isFormatValid(normalizedBalanceCode(typed))

/**
 * Whether the [typed] code is the [listed] one, in the forms the server tries
 * (balanceCodeSecretCandidates): normalized, then normalized with 0, 1 and 8
 * read as the O, I and B a card's monospace face makes them look like. A
 * base32 code has none of those digits, so no form turns one code into another.
 */
internal fun balanceCodeMatches(typed: String, listed: String): Boolean {
    val listedCode = normalizedBalanceCode(listed)
    if (listedCode.isEmpty()) {
        return false
    }
    val typedCode = normalizedBalanceCode(typed)
    return typedCode == listedCode ||
        typedCode.replace('0', 'O').replace('1', 'I').replace('8', 'B') == listedCode
}

/**
 * The server's refusal says the code is already redeemed. A gift code's does
 * ("Balance code already redeemed."); a redeemed data code's still reads
 * "Unknown balance code.". The server's own redeem retry reads its answer the
 * same way (subscription_controller.go).
 */
internal fun isAlreadyRedeemedAnswer(message: String?): Boolean =
    message?.contains("already redeemed", ignoreCase = true) == true

/** This code on the network's own redeemed list: what it granted. */
internal data class ListedBalanceCode(
    // a gift code's year of Pro (kind pro_gift); otherwise data
    val proGift: Boolean,
    // the end of its grant: for a gift, the end of its year of Pro
    val endTimeMillis: Long?,
)

/**
 * Redeem, then classify with the network's own redeemed-code list (UPGRADE.md N7).
 *
 * The server answers "Unknown balance code." both for a data code that does not
 * exist and for one already redeemed, so the old message match on
 * "already"/"redeemed" never fired: a retry after a network failure that had
 * committed told the user their consumed code was invalid. The classification is
 * the SDK's (Sdk.classifyBalanceCodeRedeem); the redeemed-code list is fetched only
 * when the redeem did not plainly succeed.
 *
 * A lost answer (GIFTOFPRIVACY.md 10.5): the redeem committed but its answer never
 * arrived, and the retry answers "already redeemed". When this network's own list
 * holds the code, matched however it was typed, a gift code is a redeem: its year
 * of Pro is this network's, so it is confirmed as one and the caller refreshes the
 * plan. A data code there stays AlreadyRedeemed, never a new credit. A code
 * redeemed elsewhere, or with no list, is AlreadyRedeemed when the server's answer
 * says so.
 *
 * Generic over the SDK types so it runs without the native library.
 */
internal class BalanceCodeRedeemFlow<R, L>(
    private val redeem: (secret: String, callback: (result: R?, error: Exception?) -> Unit) -> Unit,
    private val fetchRedeemedCodes: (callback: (redeemedCodes: L?) -> Unit) -> Unit,
    private val classify: (result: R?, redeemedCodes: L?, secret: String) -> String,
    // the data a credited answer added (its transfer balance)
    private val addedByteCount: (result: R) -> Long,
    // the year of Pro a credited answer granted (its `pro`); null for a data code
    private val proGrant: (result: R) -> RedeemedProGrant?,
    // the code on the network's own list (balanceCodeMatches); null when it is not there
    private val findListed: (redeemedCodes: L, secret: String) -> ListedBalanceCode?,
    // the server refused the redeem because the code is already redeemed (isAlreadyRedeemedAnswer)
    private val answeredAlreadyRedeemed: (result: R) -> Boolean,
) {
    /**
     * Exactly one of `onRedeemed` and `onFailure` is called. A credited redeem
     * carries what the code added, data or a year of Pro, so the confirmation can
     * say it.
     */
    fun run(
        secret: String,
        onRedeemed: (RedeemedBalanceCode) -> Unit,
        onFailure: (RedeemBalanceCodeFailure) -> Unit,
    ) {
        redeem(secret) { result, error ->
            // a transport error carries no authoritative answer
            val answer = if (error != null) null else result
            val firstOutcome = redeemFailureForOutcome(classify(answer, null, secret))
            if (firstOutcome == null && answer != null) {
                onRedeemed(RedeemedBalanceCode(addedByteCount(answer), proGrant(answer)))
                return@redeem
            }
            fetchRedeemedCodes { redeemedCodes ->
                val listed = redeemedCodes?.let { findListed(it, secret) }
                if (listed != null && listed.proGift) {
                    // a lost answer: the gift's year of Pro is this network's. The
                    // list does not say whether the year waits for a paid period, so
                    // it is confirmed as the year alone.
                    onRedeemed(
                        RedeemedBalanceCode(
                            addedByteCount = 0L,
                            pro = RedeemedProGrant(startTimeMillis = null, endTimeMillis = listed.endTimeMillis, queued = false),
                        )
                    )
                    return@fetchRedeemedCodes
                }
                onFailure(
                    when {
                        // a data code credited earlier: its data is on the balance
                        listed != null -> RedeemBalanceCodeFailure.AlreadyRedeemed
                        // redeemed, but not on this network's list (another network's
                        // code, or the list did not load)
                        answer != null && answeredAlreadyRedeemed(answer) -> RedeemBalanceCodeFailure.AlreadyRedeemed
                        else -> redeemFailureForOutcome(classify(answer, redeemedCodes, secret))
                            ?: RedeemBalanceCodeFailure.Transport
                    }
                )
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
        // the SDK owns the code format (its length), shared with every app; checked
        // on the code normalized, so the card's grouping enables Redeem
        _codeIsValid.value = isTypedBalanceCodeValid(it.text)
    }

    private val _codeIsValid = MutableStateFlow(false)
    val codeIsValid: StateFlow<Boolean> = _codeIsValid.asStateFlow()

    val redeem: (
            onSuccess: (RedeemedBalanceCode) -> Unit,
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
                    addedByteCount = { result -> result.transferBalance?.balanceByteCount ?: 0L },
                    proGrant = { result ->
                        result.pro?.let { pro ->
                            RedeemedProGrant(
                                startTimeMillis = pro.startTime?.unixMilli()?.takeIf { 0L < it },
                                endTimeMillis = pro.endTime?.unixMilli()?.takeIf { 0L < it },
                                queued = pro.queued,
                            )
                        }
                    },
                    findListed = { redeemedCodes, secret ->
                        (0 until redeemedCodes.len())
                            .mapNotNull { i -> redeemedCodes.get(i) }
                            .firstOrNull { listed -> balanceCodeMatches(secret, listed.secret ?: "") }
                            ?.let { listed ->
                                ListedBalanceCode(
                                    proGift = listed.kind == Sdk.BalanceCodeKindProGift,
                                    endTimeMillis = listed.endTime?.unixMilli()?.takeIf { 0L < it },
                                )
                            }
                    },
                    answeredAlreadyRedeemed = { result -> isAlreadyRedeemedAnswer(result.error?.message) },
                )
                flow.run(
                    code.text.trim(),
                    onRedeemed = { redeemed ->
                        viewModelScope.launch {
                            _isLoading.value = false
                            onSuccess(redeemed)
                        }
                    },
                    onFailure = { failure ->
                        viewModelScope.launch {
                            _isLoading.value = false
                            onError(failure)
                        }
                    },
                )
            } else {
                _isLoading.value = false
                onError(RedeemBalanceCodeFailure.Transport)
            }

        }

    }

}
