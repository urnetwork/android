package com.bringyour.network.acceptance

/**
 * Checks the Solana Pay request THIS CLIENT built against the price the server
 * quoted.
 *
 * Why this exists as its own contract. Every server-side payment test builds
 * the payment itself and so can never catch the client building the wrong one,
 * and all three shipped bugs in this area were exactly that: the reference was
 * generated as a hex uuid where Solana Pay requires a base58 32-byte pubkey, so
 * the webhook could never match the payment to its intent; the plan was omitted
 * and the server answered "Unknown plan." before the wallet even opened; and
 * the amount and the merchant address were hardcoded in the app, so a price
 * change took money and delivered nothing.
 *
 * It lives in acceptanceShared, so the rules are unit-tested on the JVM while
 * the instrumented test supplies the real inputs from the running app.
 */

/** Where a URnetwork USDC payment goes. */
internal const val SOLANA_MERCHANT_ADDRESS = "4Fj9RCwJqHLdLNK28DwWHunHqWapxKbbzeYZLmreSYCM"

/** USDC on Solana mainnet. USDC exists on many chains; this is the only one accepted. */
internal const val SOLANA_USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

/** A Solana Pay reference is a public key: 32 bytes, base58. */
internal const val SOLANA_REFERENCE_BYTES = 32

/** The payment the client asked a wallet to make. */
internal data class SolanaPayRequest(
    val reference: String,
    val amountUsd: Double,
    val plan: String,
    val url: String,
)

/**
 * Returns every way the request disagrees with the quote, or an empty list.
 *
 * A list rather than the first failure: when this breaks it is usually worth
 * seeing all of it at once, and an acceptance detail line has room.
 */
internal fun solanaPayQuoteProblems(
    request: SolanaPayRequest,
    quotedAmountUsd: Double,
): List<String> {
    val problems = mutableListOf<String>()

    if (!isSolanaPayReference(request.reference)) {
        problems += "reference ${quoted(request.reference)} is not a base58 32-byte pubkey"
    }
    if (request.plan.isBlank()) {
        // Without it the server answers "Unknown plan." and the purchase dies
        // before the wallet opens.
        problems += "no plan was sent with the payment intent"
    }
    if (quotedAmountUsd <= 0.0) {
        problems += "the server quoted a non-positive amount: $quotedAmountUsd"
    }
    if (!sameMoney(request.amountUsd, quotedAmountUsd)) {
        problems += "the client is paying ${request.amountUsd} but the server quoted $quotedAmountUsd"
    }

    val url = parseSolanaPayUrl(request.url)
    if (url == null) {
        problems += "the client built ${quoted(request.url)}, which is not a solana: payment url"
        return problems
    }
    if (url.recipient != SOLANA_MERCHANT_ADDRESS) {
        problems += "the payment url pays ${quoted(url.recipient)}, not the merchant address"
    }
    if (url.splTokenMint != SOLANA_USDC_MINT) {
        problems += "the payment url uses mint ${quoted(url.splTokenMint ?: "")}, not USDC on Solana"
    }
    if ((url.reference ?: "") != request.reference) {
        problems += "the payment url carries a different reference than the intent registered"
    }
    val urlAmount = url.amount?.toDoubleOrNull()
    if (urlAmount == null) {
        problems += "the payment url amount ${quoted(url.amount ?: "")} is not a number"
    } else if (!sameMoney(urlAmount, quotedAmountUsd)) {
        problems += "the payment url asks for $urlAmount but the server quoted $quotedAmountUsd"
    }
    return problems
}

/** Money compares to the cent: the server itself allows a one-cent tolerance. */
private fun sameMoney(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) < 0.005

private fun quoted(value: String): String = "\"$value\""

internal data class SolanaPayUrl(
    val recipient: String,
    val amount: String?,
    val splTokenMint: String?,
    val reference: String?,
)

/**
 * Parses `solana:<recipient>?amount=..&spl-token=..&reference=..`.
 *
 * Hand-rolled rather than via a URI class because `solana:` is an opaque
 * scheme: the standard parsers put the whole thing in the scheme-specific part
 * and hand back no query.
 */
internal fun parseSolanaPayUrl(value: String): SolanaPayUrl? {
    val prefix = "solana:"
    if (!value.startsWith(prefix)) return null
    val body = value.substring(prefix.length)
    if (body.isEmpty()) return null
    val separator = body.indexOf('?')
    val recipient = if (separator < 0) body else body.substring(0, separator)
    if (recipient.isEmpty()) return null
    val query = if (separator < 0) "" else body.substring(separator + 1)

    val parameters = mutableMapOf<String, String>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val equals = pair.indexOf('=')
        if (equals <= 0) continue
        parameters[pair.substring(0, equals)] = decodePercent(pair.substring(equals + 1))
    }
    return SolanaPayUrl(
        recipient = recipient,
        amount = parameters["amount"],
        splTokenMint = parameters["spl-token"],
        reference = parameters["reference"],
    )
}

private fun decodePercent(value: String): String {
    if (!value.contains('%')) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val c = value[index]
        if (c == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar())
                index += 3
                continue
            }
        }
        out.append(c)
        index += 1
    }
    return out.toString()
}

/** True when the value is a base58 encoding of exactly 32 bytes. */
internal fun isSolanaPayReference(value: String): Boolean =
    decodeBase58(value)?.size == SOLANA_REFERENCE_BYTES

private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

/**
 * Decodes base58 (the Bitcoin/Solana alphabet), or null when the value is not
 * base58 at all. Leading '1' characters are leading zero bytes.
 */
internal fun decodeBase58(value: String): ByteArray? {
    if (value.isEmpty()) return null
    var leadingZeros = 0
    while (leadingZeros < value.length && value[leadingZeros] == BASE58_ALPHABET[0]) {
        leadingZeros += 1
    }
    // Repeated division, base 58 to base 256.
    val digits = ByteArray(value.length)
    var digitLength = 0
    for (index in leadingZeros until value.length) {
        val carryStart = BASE58_ALPHABET.indexOf(value[index])
        if (carryStart < 0) return null
        var carry = carryStart
        var position = 0
        var scan = digits.size - 1
        while ((carry != 0 || position < digitLength) && scan >= 0) {
            carry += 58 * (digits[scan].toInt() and 0xff)
            digits[scan] = (carry % 256).toByte()
            carry /= 256
            scan -= 1
            position += 1
        }
        digitLength = position
    }
    val out = ByteArray(leadingZeros + digitLength)
    System.arraycopy(digits, digits.size - digitLength, out, leadingZeros, digitLength)
    return out
}
