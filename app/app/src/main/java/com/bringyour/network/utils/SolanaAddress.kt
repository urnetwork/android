package com.bringyour.network.utils

/**
 * Local checks of Solana addresses: base58 encoded 32 byte public keys, the form a
 * Solana USDC payout address takes.
 *
 * The server is the authority on a payout address: it checks one against its chain when
 * a wallet is added (`POST /account/wallet`) or checked (`POST /wallet/validate-address`).
 * These only gate input before anything is sent anywhere.
 */
object SolanaAddress {

    // the USDC token mint; a token mint is not a wallet
    const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    private const val PUBLIC_KEY_BYTES = 32

    // 32 bytes encode to 32 (all leading zero bytes) up to 44 base58 characters
    private const val MIN_ENCODED_LENGTH = 32
    private const val MAX_ENCODED_LENGTH = 44

    // 32 zero bytes, which base58 writes only as 32 ones
    private val ALL_ZERO_KEY = "1".repeat(PUBLIC_KEY_BYTES)

    /** any 32 byte key, such as the mint and the merchant of a Solana Pay quote */
    fun isValidSyntax(address: String): Boolean {
        val a = address.trim()
        if (a.length < MIN_ENCODED_LENGTH || MAX_ENCODED_LENGTH < a.length) {
            return false
        }
        return Ss58.decodeBase58(a)?.size == PUBLIC_KEY_BYTES
    }

    /**
     * The server's rule for a wallet on chain SOL, on the trimmed address: a 32 byte key
     * that is neither the all-zero key nor the USDC mint. The payout address field
     * applies it before any request (support inbox 8045).
     */
    fun isValidWalletAddress(address: String): Boolean {
        val a = address.trim()
        return isValidSyntax(a) && a != ALL_ZERO_KEY && a != USDC_MINT
    }

    /** "7Xk9…3fQa", the same first four and last four form as [Ss58.short] */
    fun short(address: String): String {
        val a = address.trim()
        return if (a.length <= 12) a else "${a.take(4)}…${a.takeLast(4)}"
    }
}
