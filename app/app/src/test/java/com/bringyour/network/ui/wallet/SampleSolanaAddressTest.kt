package com.bringyour.network.ui.wallet

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Samples and tests use fixture Solana keys, never the official merchant's address: the
 * app pays the merchant a Solana Pay quote names. Checked on the app's sources, read
 * from the module directory where Gradle runs unit tests.
 */
class SampleSolanaAddressTest {

    // the official merchant's address by its sha256 (hex), so the address itself
    // stays out of test data
    private val officialMerchantSha256 = "b6fed7b0a3462afeda2f9703ecc17076b664bb8b1129bb0b62c70304bd50ab2c"

    private val base58Token = Regex("[1-9A-HJ-NP-Za-km-z]{32,44}")

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sourceFiles(roots: List<File>, extensions: Set<String>): List<File> =
        roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in extensions }.toList() }

    @Test
    fun `no app source carries the official merchant`() {
        // the app pays the merchant a quote names; samples and tests use fixture keys
        val found = sourceFiles(listOf(File("src")), setOf("kt", "java", "xml", "json")).flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> base58Token.findAll(line).any { sha256Hex(it.value) == officialMerchantSha256 } }
                .map { (index, _) -> "${file.path}:${index + 1}" }
        }
        assertEquals(emptyList<String>(), found)
    }
}
