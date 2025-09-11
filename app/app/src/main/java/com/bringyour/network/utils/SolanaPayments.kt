package com.bringyour.network.utils

import android.net.Uri
import com.funkatronics.encoders.Base58
import java.security.SecureRandom

fun buildSolanaPaymentUrl(
    reference: String
): String {
    val recipient = "74UNdYRpvakSABaYHSZMQNaXBVtA6eY9Nt8chcqocKe7"
    val amountDecimal = "40" // 40 USDC yearly sub
    val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" // mainnet USDC

    val label = "URnetwork"
    val message = "Yearly Supporter Subscription"
    val memo = ""

    val url = buildString {
        append("solana:")
        append(recipient)
        append("?amount="); append(amountDecimal)
        append("&spl-token="); append(usdcMint)
        append("&reference="); append(reference)
        append("&label="); append(Uri.encode(label))
        append("&message="); append(Uri.encode(message))
        append("&memo="); append(Uri.encode(memo))
    }
    return url
}

val createPaymentReference = {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    val reference = Base58.encodeToString(bytes)
    reference
}