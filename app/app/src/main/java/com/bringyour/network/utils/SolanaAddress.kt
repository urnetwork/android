package com.bringyour.network.utils

/**
 * Local checks of Solana addresses: base58 encoded 32 byte public keys, such as the
 * merchant and the mint a Solana Pay quote names ([solanaPaymentQuote] refuses a quote
 * without them).
 */
object SolanaAddress {

    private const val PUBLIC_KEY_BYTES = 32

    // 32 bytes encode to 32 (all leading zero bytes) up to 44 base58 characters
    private const val MIN_ENCODED_LENGTH = 32
    private const val MAX_ENCODED_LENGTH = 44

    /** any 32 byte key, such as the mint and the merchant of a Solana Pay quote */
    fun isValidSyntax(address: String): Boolean {
        val a = address.trim()
        if (a.length < MIN_ENCODED_LENGTH || MAX_ENCODED_LENGTH < a.length) {
            return false
        }
        return Ss58.decodeBase58(a)?.size == PUBLIC_KEY_BYTES
    }
}
