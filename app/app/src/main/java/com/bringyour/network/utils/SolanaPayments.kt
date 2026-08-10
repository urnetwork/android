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
// server quoted from pro.yml. Never a constant.

// The merchant address and the mainnet USDC mint. These belong in remote config so a
// rotation does not need an app release; keeping them here is the status quo, but the
// sdk now validates them, so a malformed value fails loudly instead of sending a
// payment nowhere recoverable.
private const val MERCHANT_ADDRESS = "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM"
private const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

const val SOLANA_PLAN_MONTHLY = "monthly"
const val SOLANA_PLAN_YEARLY = "yearly"

/**
 * Build the wallet deep link for a purchase.
 *
 * @param reference from [createPaymentReference], already registered with the server.
 * @param amountUsd the price the SERVER quoted, from the payment intent result.
 * @param plan [SOLANA_PLAN_MONTHLY] or [SOLANA_PLAN_YEARLY], used for the wallet's
 *   description only -- the price comes from amountUsd.
 *
 * Throws if any field would produce a payment that cannot be credited.
 */
fun buildSolanaPaymentUrl(
    reference: String,
    amountUsd: Double,
    plan: String,
): String {
    val args = SolanaPaymentUrlArgs()
    args.recipient = MERCHANT_ADDRESS
    args.amountUsd = amountUsd
    args.splTokenMint = USDC_MINT
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
