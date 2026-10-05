package com.bringyour.network.ui.components.referral

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.bringyour.network.R
import com.bringyour.sdk.Api
import com.bringyour.sdk.ValidateReferralCodeArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ReferralCodeChecker"

/**
 * Asks the server about a referral code. `done` runs once, on any thread,
 * with the answer: `valid` is null when the check did not answer. Returns
 * false, without calling `done`, when there is no api to ask.
 */
fun interface ReferralCodeChecker {
    fun check(code: String, done: (valid: Boolean?, capped: Boolean) -> Unit): Boolean
}

/** The SDK's validateReferralCode as a [ReferralCodeChecker]. */
fun apiReferralCodeChecker(api: () -> Api?): ReferralCodeChecker = ReferralCodeChecker { code, done ->
    val currentApi = api() ?: return@ReferralCodeChecker false
    try {
        val args = ValidateReferralCodeArgs()
        args.referralCode = code
        currentApi.validateReferralCode(args) { result, err ->
            if (err != null) {
                Log.i(TAG, "validateReferralCode callback err: ${err.message}")
                done(null, false)
            } else {
                done(result?.isValid ?: false, result?.isCapped ?: false)
            }
        }
        true
    } catch (e: Exception) {
        Log.i(TAG, "${e.message}")
        false
    }
}

/**
 * The sign-up screens' referral field (support inbox 1698): the code as
 * typed, and what the server said about it. Typing checks the code after a
 * pause ([ReferralCodeField.CHECK_DELAY_MILLIS]), an answer for text that has
 * changed since is dropped, and the create call carries [createCode].
 *
 * State is compose snapshot state, read by the sign-up screens. `scope` runs
 * on the main thread; checks post their answers there.
 */
class ReferralCodeInputController(
    private val scope: CoroutineScope,
    private val checker: ReferralCodeChecker,
    private val checkDelayMillis: Long = ReferralCodeField.CHECK_DELAY_MILLIS,
) {

    var code by mutableStateOf(TextFieldValue(""))
        private set

    var verdict by mutableStateOf(ReferralCodeVerdict.Unchecked)
        private set

    // a typed code becomes a new edit; answers carry the edit they were asked for
    private var edit = 0
    private var pendingCheck: Job? = null

    val setCode: (TextFieldValue) -> Unit = { value ->
        val changed = ReferralCodeField.normalize(value.text) != ReferralCodeField.normalize(code.text)
        code = value
        if (changed) {
            edit += 1
            verdict = ReferralCodeVerdict.Unchecked
            pendingCheck?.cancel()
            pendingCheck = if (ReferralCodeField.shouldCheck(value.text)) {
                scope.launch {
                    delay(checkDelayMillis)
                    pendingCheck = null
                    check {}
                }
            } else {
                null
            }
        }
    }

    val isValidating: Boolean get() = verdict == ReferralCodeVerdict.Checking

    val isValid: Boolean get() = verdict == ReferralCodeVerdict.Valid

    val isCapped: Boolean get() = verdict == ReferralCodeVerdict.Capped

    // the server judged the code (or could not be asked): the field shows an error
    val isRejected: Boolean
        get() = verdict == ReferralCodeVerdict.Invalid ||
            verdict == ReferralCodeVerdict.Capped ||
            verdict == ReferralCodeVerdict.CheckFailed

    val supportingTextRes: Int?
        get() = when (verdict) {
            ReferralCodeVerdict.CheckFailed -> R.string.something_went_wrong
            ReferralCodeVerdict.Invalid -> R.string.invalid_referral_code
            ReferralCodeVerdict.Capped -> R.string.referral_code_capped
            else -> null
        }

    /** The code the create call carries ([ReferralCodeField.createCode]). */
    val createCode: String? get() = ReferralCodeField.createCode(code.text, verdict)

    /**
     * Checks the code now (Done on the keyboard, a code a link filled in).
     * `onComplete` gets whether the code is accepted.
     */
    fun check(onComplete: (Boolean) -> Unit) {
        pendingCheck?.cancel()
        pendingCheck = null

        val normalized = ReferralCodeField.normalize(code.text)
        // nothing to check, or the check for this text is already out
        if (normalized.isEmpty() || verdict == ReferralCodeVerdict.Checking) {
            onComplete(false)
            return
        }

        val asked = edit
        verdict = ReferralCodeVerdict.Checking
        val started = checker.check(normalized) { valid, capped ->
            scope.launch {
                // the text changed while the check was out: its answer is stale
                if (asked == edit) {
                    verdict = ReferralCodeField.verdictOf(valid, capped)
                    onComplete(verdict == ReferralCodeVerdict.Valid)
                } else {
                    onComplete(false)
                }
            }
        }
        if (!started) {
            verdict = ReferralCodeVerdict.CheckFailed
            onComplete(false)
        }
    }
}
