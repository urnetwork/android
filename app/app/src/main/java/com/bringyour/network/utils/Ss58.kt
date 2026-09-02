package com.bringyour.network.utils

import java.math.BigInteger

/**
 * Local SS58 syntax check for Bittensor coldkeys.
 *
 * The SDK's `validateSs58` (which also verifies the blake2b checksum) is the
 * authority; this only gates a text field before anything is sent anywhere.
 */
object Ss58 {

    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    // the generic substrate prefix used by Bittensor coldkeys
    const val BITTENSOR_PREFIX = 42

    // one prefix byte (< 64) + a 32 byte public key + a two byte checksum
    private const val ENCODED_LENGTH = 35

    fun isValidSyntax(address: String): Boolean {
        val bytes = decodeBase58(address.trim()) ?: return false
        return bytes.size == ENCODED_LENGTH && (bytes[0].toInt() and 0xFF) == BITTENSOR_PREFIX
    }

    /** "5F3s…kQ9v" */
    fun short(address: String): String {
        val a = address.trim()
        return if (a.length <= 12) a else "${a.take(4)}…${a.takeLast(4)}"
    }

    fun decodeBase58(input: String): ByteArray? {
        if (input.isEmpty()) return null
        var num = BigInteger.ZERO
        val base = BigInteger.valueOf(58)
        for (c in input) {
            val digit = ALPHABET.indexOf(c)
            if (digit < 0) return null
            num = num.multiply(base).add(BigInteger.valueOf(digit.toLong()))
        }
        var bytes = num.toByteArray()
        // BigInteger prepends a sign byte when the high bit is set
        if (bytes.size > 1 && bytes[0] == 0.toByte()) {
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        if (num == BigInteger.ZERO) {
            bytes = ByteArray(0)
        }
        val leadingZeros = input.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + bytes
    }
}
