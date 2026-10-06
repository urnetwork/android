package com.bringyour.network.ui.wallet

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sample Solana address (SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS) is a
 * fixture key, not the official merchant, and no production code path reads it:
 * only previews and the sample source do, and the sample source is built only
 * behind the Developer screen's sample data switch, which only debug builds show
 * (DeveloperScreen, BuildConfig.DEBUG). Checked on the app's sources, read from the
 * module directory where Gradle runs unit tests.
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

    // every source set an app build compiles: all but the test source sets
    private fun appSourceSets(): List<File> {
        val testSourceSets = setOf("test", "androidTest", "testPlay")
        return File("src").listFiles().orEmpty().filter { it.isDirectory && it.name !in testSourceSets }
    }

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

    @Test
    fun `only previews and the sample source read the sample address`() {
        val function = Regex("""^\s*(?:(?:private|internal|public|override)\s+)*fun\s""")
        val type = Regex("""^\s*(?:(?:private|internal|public|data|sealed|abstract|open|enum|inner|companion)\s+)*(?:class|object|interface)\b""")
        val readers = sourceFiles(appSourceSets(), setOf("kt", "java")).flatMap { file ->
            val lines = file.readLines()
            lines.indices
                .filter { lines[it].contains("SAMPLE_SOLANA_ADDRESS") && !lines[it].contains("const val SAMPLE_SOLANA_ADDRESS") }
                .filterNot { index ->
                    val functionIndex = (index downTo 0).firstOrNull { function.containsMatchIn(lines[it]) }
                    val classIndex = (index downTo 0).firstOrNull { type.containsMatchIn(lines[it]) }
                    val inPreview = functionIndex != null &&
                        lines.subList(maxOf(0, functionIndex - 3), functionIndex).any { it.trim().startsWith("@Preview") }
                    val inSampleSource = classIndex != null && lines[classIndex].contains("class SampleLegacyWalletSource(")
                    inPreview || inSampleSource
                }
                .map { "${file.path}:${it + 1}" }
        }
        assertEquals(emptyList<String>(), readers)
    }

    @Test
    fun `the sample source is built only behind the sample data switch`() {
        // EarningsDebugFlags.useSampleData is set only by the Developer screen's sample
        // data switch
        val constructions = sourceFiles(appSourceSets(), setOf("kt", "java")).flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.contains("SampleLegacyWalletSource(") && !line.contains("class SampleLegacyWalletSource(") }
                .filterNot { (_, line) -> line.contains("EarningsDebugFlags.useSampleData") }
                .map { (index, _) -> "${file.path}:${index + 1}" }
        }
        assertEquals(emptyList<String>(), constructions)
    }
}
