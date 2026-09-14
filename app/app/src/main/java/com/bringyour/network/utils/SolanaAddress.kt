package com.bringyour.network.utils

/**
 * Local syntax check for Solana addresses: a base58 encoded 32 byte public key, the
 * form a Solana USDC payout address takes.
 *
 * The server's `POST /wallet/validate-address` (chain SOL) is the authority (it also
 * refuses the USDC mint itself); this only gates a text field before anything is
 * sent anywhere.
 */
object SolanaAddress {

    private const val PUBLIC_KEY_BYTES = 32

    // 32 bytes encode to 32 (all leading zero bytes) up to 44 base58 characters
    private const val MIN_ENCODED_LENGTH = 32
    private const val MAX_ENCODED_LENGTH = 44

    fun isValidSyntax(address: String): Boolean {
        val a = address.trim()
        if (a.length < MIN_ENCODED_LENGTH || MAX_ENCODED_LENGTH < a.length) {
            return false
        }
        return Ss58.decodeBase58(a)?.size == PUBLIC_KEY_BYTES
    }

    /** "7Xk9…3fQa", the same first four and last four form as [Ss58.short] */
    fun short(address: String): String {
        val a = address.trim()
        return if (a.length <= 12) a else "${a.take(4)}…${a.takeLast(4)}"
    }
}
