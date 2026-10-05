package com.bringyour.network.ui.settings

import com.bringyour.network.ui.login.ResendCooldown
import com.bringyour.network.ui.login.VerifySendError
import com.bringyour.network.ui.login.VerifySendNotice
import com.bringyour.network.ui.login.at

/**
 * How a sign-in method being added proves it belongs to the user.
 * An email or phone with a password is unproven until the user enters the code
 * sent to it. Google (and Apple) SSO tokens are verified by the provider, and a
 * wallet by its signature, so those count as added as soon as AddAuth succeeds.
 */
enum class AddedSignInMethod {
    // email or phone + password
    PASSWORD,
    GOOGLE,
    APPLE,
    WALLET;

    val needsVerification: Boolean get() = this == PASSWORD
}

/**
 * What the add sheet needs from the app. Every answer must arrive on the main
 * thread.
 */
interface AddSignInSession<A> {
    fun addAuth(args: A, onSuccess: () -> Unit, onError: (AddAuthRefusal) -> Unit)

    /**
     * authVerifySend{user_auth, use_numeric, result_errors}. `transportError` is
     * any request failure; `sendError` is the server's reason no code was sent,
     * null when one was.
     */
    fun sendCode(userAuth: String, done: (transportError: Boolean, sendError: VerifySendError?) -> Unit)

    /**
     * authVerify{user_auth, verify_code}, without installing the jwt it returns:
     * the session stays on the current network. `error` is null on success.
     */
    fun verifyCode(userAuth: String, code: String, done: (error: String?) -> Unit)
}

enum class AddSignInStep {
    ENTER,
    // AddAuth in flight
    ADDING,
    // the email or phone was added; verify it with the code sent to it
    ENTER_CODE,
    // authVerify in flight
    VERIFYING,
    ADDED,
}

/**
 * The add-sign-in-method sheet's flow (Settings, and a legacy guest's in-place
 * conversion). AddAuth adds an email or phone sign-in unverified, so the sheet
 * then sends a code and the sign-in counts as added only once authVerify
 * accepts it; SSO and wallet sign-ins are added at once.
 *
 * Resend waits out a server rate limit (`retry_after_seconds`) and, after a
 * sent code, `RESEND_AFTER_SENT_MILLIS` like the login verify screen. Times come
 * from `nowMillis` so tests inject the clock.
 *
 * Not thread safe: driven from the main thread.
 */
class AddSignInFlow<A>(
    private val session: AddSignInSession<A>,
    private val nowMillis: () -> Long,
) {
    companion object {
        const val RESEND_AFTER_SENT_MILLIS = 30_000L
    }

    // called after every state change
    var onChanged: () -> Unit = {}

    var step: AddSignInStep = AddSignInStep.ENTER
        private set

    // the email or phone being verified
    var userAuth: String = ""
        private set

    // the last code send's outcome, null until one answers
    var sendNotice: VerifySendNotice? = null
        private set

    var sending: Boolean = false
        private set

    // the last authVerify failure, null for none
    var verifyError: String? = null
        private set

    private var cooldown: ResendCooldown? = null
    private var sentAtMillis: Long? = null
    private var onAdded: () -> Unit = {}

    // true once a code was sent, so the sheet may say one was
    val codeSent: Boolean get() = sentAtMillis != null

    val busy: Boolean
        get() = step == AddSignInStep.ADDING || step == AddSignInStep.VERIFYING || sending

    /** The send notice now: a rate limit counts down and then clears. */
    fun noticeNow(): VerifySendNotice? = sendNotice?.at(cooldown, nowMillis())

    fun canResend(): Boolean {
        if (step != AddSignInStep.ENTER_CODE || sending) {
            return false
        }
        val now = nowMillis()
        if (cooldown?.canResend(now) == false) {
            return false
        }
        val sentAt = sentAtMillis
        return sentAt == null || sentAt + RESEND_AFTER_SENT_MILLIS <= now
    }

    /** Milliseconds until `canResend` may change on its own, null when it will not. */
    fun resendWaitMillis(): Long? {
        if (step != AddSignInStep.ENTER_CODE) {
            return null
        }
        val now = nowMillis()
        val waits = listOfNotNull(
            cooldown?.takeIf { !it.canResend(now) }?.let { it.retryAtMillis - now },
            sentAtMillis?.let { it + RESEND_AFTER_SENT_MILLIS - now }?.takeIf { 0 < it },
        )
        return waits.maxOrNull()
    }

    /**
     * Adds a sign-in method. `onAdded` runs once it counts as added: right after
     * AddAuth for SSO and wallet, after authVerify for an email or phone.
     */
    fun add(
        method: AddedSignInMethod,
        args: A,
        userAuth: String,
        onAdded: () -> Unit,
        onError: (AddAuthRefusal) -> Unit,
    ) {
        if (step != AddSignInStep.ENTER) {
            return
        }
        step = AddSignInStep.ADDING
        changed()
        session.addAuth(
            args,
            {
                if (method.needsVerification) {
                    this.userAuth = userAuth.trim()
                    this.onAdded = onAdded
                    step = AddSignInStep.ENTER_CODE
                    changed()
                    send()
                } else {
                    step = AddSignInStep.ADDED
                    changed()
                    onAdded()
                }
            },
            { refusal ->
                step = AddSignInStep.ENTER
                changed()
                onError(refusal)
            }
        )
    }

    /** Sends a new code; refused while a send is in flight or Resend is waiting. */
    fun resend(): Boolean {
        if (!canResend()) {
            return false
        }
        send()
        return true
    }

    /** Verifies the entered code; the sign-in counts as added only on success. */
    fun submitCode(code: String) {
        val trimmed = code.trim()
        if (step != AddSignInStep.ENTER_CODE || sending || trimmed.isEmpty()) {
            return
        }
        step = AddSignInStep.VERIFYING
        verifyError = null
        changed()
        session.verifyCode(userAuth, trimmed) { error ->
            if (error != null) {
                step = AddSignInStep.ENTER_CODE
                verifyError = error
                changed()
                return@verifyCode
            }
            step = AddSignInStep.ADDED
            changed()
            onAdded()
        }
    }

    fun clearVerifyError() {
        if (verifyError != null) {
            verifyError = null
            changed()
        }
    }

    private fun send() {
        sending = true
        sendNotice = null
        changed()
        session.sendCode(userAuth) { transportError, sendError ->
            sending = false
            val now = nowMillis()
            val notice = VerifySendNotice.from(transportError, sendError)
            sendNotice = notice
            // a transport error has no retry time from the server
            cooldown = if (transportError) null else ResendCooldown.after(sendError, now)
            if (notice == VerifySendNotice.Sent) {
                sentAtMillis = now
            }
            changed()
        }
    }

    private fun changed() {
        onChanged()
    }
}
