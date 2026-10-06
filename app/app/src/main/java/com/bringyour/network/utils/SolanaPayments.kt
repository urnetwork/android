package com.bringyour.network.utils

import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SolanaPaymentUrlArgs

// Solana Pay urls are built by the sdk so every platform produces the same thing and
// the rules are tested once (sdk/solana_pay_test.go).
//
// This file used to build the url by hand, with two bugs that cost real money:
//
//   - the amount was hardcoded to "40" with the message "Yearly Supporter
//     Subscription", so the monthly plan could not be sold at all, and the price was a
//     client-side constant that no longer had to agree with what the server quoted.
//     The webhook checks the arriving payment against the intent, so a disagreement
//     means the money lands and is never credited.
//   - the merchant address was a literal here, with the previous one left in a comment
//     above it. It has rotated at least once already.
//
// The amount must always come from SolanaPaymentIntentResult.amountUsd -- the price the
// server quoted from pro.yml. Never a constant. Where to pay comes with it: the merchant
// address and the mint (recipient, splTokenMint), the receiver the server credits when it
// quotes. The app keeps no address of its own, so a rotation on the server reaches it
// with the next quote, and a quote that does not say where to pay (from a server that
// predates the fields) is not paid at all.

const val SOLANA_PLAN_MONTHLY = "monthly"
const val SOLANA_PLAN_YEARLY = "yearly"

/**
 * What the server quoted for a Solana Pay intent: the amount in USD (paid in USDC) and
 * where to pay it, the merchant address and the SPL token mint, both base58.
 */
data class SolanaPaymentQuote(
    val amountUsd: Double,
    val recipient: String,
    val splTokenMint: String,
)

/**
 * The quote in a payment intent result, or null when it cannot be paid: a missing or
 * non-positive amount (the webhook's check is `amount >= quoted - tolerance`, so a zero
 * quote is satisfied by any payment at all, including none), or no Solana address for
 * the merchant or the mint (a server that predates them). There is no fallback address.
 */
fun solanaPaymentQuote(
    amountUsd: Double,
    recipient: String?,
    splTokenMint: String?,
): SolanaPaymentQuote? {
    // also refuses NaN
    if (!(0.0 < amountUsd)) {
        return null
    }
    if (recipient == null || !SolanaAddress.isValidSyntax(recipient)) {
        return null
    }
    if (splTokenMint == null || !SolanaAddress.isValidSyntax(splTokenMint)) {
        return null
    }
    return SolanaPaymentQuote(
        amountUsd = amountUsd,
        recipient = recipient,
        splTokenMint = splTokenMint,
    )
}

/**
 * Build the wallet deep link for a purchase.
 *
 * @param reference from [createPaymentReference], already registered with the server.
 * @param quote what the server quoted for that intent: the amount and where to pay it.
 * @param plan [SOLANA_PLAN_MONTHLY] or [SOLANA_PLAN_YEARLY], used for the wallet's
 *   description only -- the price comes from the quote.
 *
 * Throws if any field would produce a payment that cannot be credited.
 */
fun buildSolanaPaymentUrl(
    reference: String,
    quote: SolanaPaymentQuote,
    plan: String,
): String {
    val args = SolanaPaymentUrlArgs()
    args.recipient = quote.recipient
    args.amountUsd = quote.amountUsd
    args.splTokenMint = quote.splTokenMint
    args.reference = reference
    args.label = "URnetwork"
    args.message = when (plan) {
        SOLANA_PLAN_YEARLY -> "UR Pro — Yearly"
        else -> "UR Pro — Monthly"
    }
    return Sdk.buildSolanaPaymentUrl(args)
}

/**
 * A fresh Solana Pay reference: 32 random bytes, base58 encoded, which is the wire
 * form of a Solana public key. The wallet attaches it to the transaction as a
 * read-only account and the webhook matches on it, so the format is load-bearing.
 */
val createPaymentReference = {
    Sdk.createPaymentReference()
}
