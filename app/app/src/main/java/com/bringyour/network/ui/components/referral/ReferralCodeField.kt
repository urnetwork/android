package com.bringyour.network.ui.components.referral

import java.util.Locale

/** What the server has said about the code in the referral field. */
enum class ReferralCodeVerdict {
    /** Typed, not asked about yet (or the field is empty). */
    Unchecked,
    Checking,
    Valid,
    Invalid,
    /** Valid, but the code has reached its referral cap. */
    Capped,
    /** The check did not answer (no api, a transport error, a refused call). */
    CheckFailed,
}

/**
 * The always-visible, optional referral code field at sign-up (support inbox
 * 1698), the field the Windows sign-up already shows above Continue. It used
 * to sit behind a muted "Add referral code" link under Continue, which
 * invitees missed. Pure so the unit tests pin what the typed text means.
 */
object ReferralCodeField {

    /** The pause after typing before the code is checked (as on Windows). */
    const val CHECK_DELAY_MILLIS = 400L

    /** The code as the server reads it: trimmed, upper case (the server upper-cases codes). */
    fun normalize(input: String): String = input.trim().uppercase(Locale.ROOT)

    /** Only a non-empty code is worth a check; an empty field is no code at all. */
    fun shouldCheck(input: String): Boolean = normalize(input).isNotEmpty()

    /** The verdict for a check's answer: `valid` is null when the check did not answer. */
    fun verdictOf(valid: Boolean?, capped: Boolean): ReferralCodeVerdict = when {
        valid == null -> ReferralCodeVerdict.CheckFailed
        capped -> ReferralCodeVerdict.Capped
        valid -> ReferralCodeVerdict.Valid
        else -> ReferralCodeVerdict.Invalid
    }

    /**
     * The code the create call carries: the normalized code, unless the
     * server already said it is invalid or used up. A code that is still
     * unchecked (Continue right after typing) or whose check did not answer
     * goes along: the server checks it again on create and ignores a bad
     * one, so a typed code is never dropped without a word.
     */
    fun createCode(input: String, verdict: ReferralCodeVerdict): String? {
        val code = normalize(input)
        if (code.isEmpty()) {
            return null
        }
        return when (verdict) {
            ReferralCodeVerdict.Invalid, ReferralCodeVerdict.Capped -> null
            else -> code
        }
    }
}
