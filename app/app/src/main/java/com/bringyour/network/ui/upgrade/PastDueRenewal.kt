package com.bringyour.network.ui.upgrade

/**
 * The past-due notice's rules (support review 2026-10-26: Stripe failed
 * renewals): when it shows, and the link its action opens.
 *
 * Stripe retries a declined renewal for about two weeks and then cancels the
 * subscription. While it retries, the balance (GET /subscription/balance)
 * carries `payment_past_due`, and the app says so wherever it shows the
 * subscription: "Your UR Pro payment didn't go through. Update your payment
 * method to keep Pro." An account with no email (a wallet or phone sign-in)
 * learns of the failed renewal only here.
 *
 * The notice follows the latest balance alone. It shows exactly while that
 * balance carries the field, with no time rule and no state of the app's own,
 * and an absent field (no failing renewal, or an older server) hides it.
 *
 * "Update payment method" opens ur.io's Manage Subscription in the browser,
 * signed in with a one-time auth code (POST /auth/code-create), as the Apple
 * App Store builds open it for a plan billed elsewhere (support inbox 561).
 * ur.io takes the code out of the address bar and asks before it switches a
 * browser signed in to another network. The link is always
 * [MANAGE_SUBSCRIPTION_URL], never the balance's `update_payment_url` (the
 * same page today), so a sign-in code is never attached to a url the server
 * supplied. Play withholds the action (PaymentMethodHooks).
 *
 * Kept free of the sdk's generated classes, which load the native library, so
 * PastDueRenewalTest runs on the JVM.
 */
object PastDueRenewal {

    /** ur.io's Manage Subscription, which manages every billing store for the signed-in network. */
    const val MANAGE_SUBSCRIPTION_URL = "https://ur.io/app/subscription"

    /**
     * Whether a balance answer shows the notice: exactly while it carries
     * payment_past_due. [paymentPastDue] is the answer's field (the sdk's
     * SubscriptionBalanceResult.paymentPastDue), null when the field is absent,
     * and null when there is no answer yet.
     */
    fun noticeShows(paymentPastDue: Any?): Boolean = paymentPastDue != null

    /**
     * The link "Update payment method" opens: Manage Subscription signed in
     * with [authCode], percent-encoded as one query value. A code that could
     * not be created (null or blank) gives the page without one, where ur.io
     * asks the user to sign in.
     */
    fun manageSubscriptionUrl(authCode: String?): String {
        val code = authCode?.trim().orEmpty()
        if (code.isEmpty()) {
            return MANAGE_SUBSCRIPTION_URL
        }
        return "$MANAGE_SUBSCRIPTION_URL?auth_code=${encodeQueryValue(code)}"
    }

    // the characters besides ascii letters and digits that a query value keeps
    // as they are: the url query characters (RFC 3986) without "&", "=", "+",
    // "#" and "?", as the Apple apps encode (manageSubscriptionOnWebURL), so
    // nothing in a code can add a parameter or a fragment, and "+" is never
    // read as a space
    private const val QUERY_VALUE_KEPT_CHARS = "-._~!$'()*,;:@/"

    private const val HEX_DIGITS = "0123456789ABCDEF"

    /** [value] as one query value: every byte of its utf-8 but the kept ascii as %XX. */
    private fun encodeQueryValue(value: String): String {
        val encoded = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val b = byte.toInt() and 0xff
            val c = b.toChar()
            val kept = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
                (b < 0x80 && QUERY_VALUE_KEPT_CHARS.indexOf(c) >= 0)
            if (kept) {
                encoded.append(c)
            } else {
                encoded.append('%').append(HEX_DIGITS[b shr 4]).append(HEX_DIGITS[b and 0x0f])
            }
        }
        return encoded.toString()
    }
}
