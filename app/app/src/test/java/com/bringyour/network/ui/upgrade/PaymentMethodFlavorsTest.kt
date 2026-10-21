package com.bringyour.network.ui.upgrade

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment method row and the crypto invoice exist only on the flavors that
 * sell outside a store (mmm/ZCASH-CONTRACT.md §6.3): github/F-Droid,
 * solana_dapp and ethos_dapp compile src/cryptoPay; Play sells through Play
 * Billing only and compiles none of it, so no toggle, no crypto copy and no
 * crypto code path can be reached there. Shared code reaches the crypto code
 * only through the hooks each side defines (PaymentMethodHooks.kt, empty in
 * src/google). Read from the module's sources and build file; no device.
 */
class PaymentMethodFlavorsTest {

    // what shared code may name: the hooks (empty on Play)
    private val hooks = setOf(
        "PaymentMethodsScope",
        "CryptoInvoiceHost",
        "rememberPlanMethodSlot",
        "AccountPaymentNotices",
        "SubscriptionManageLine",
    )

    // what only the crypto source set may name: its copy, its sdk calls and its own types
    private val cryptoOnly = listOf(
        Regex("""\bR\.string\.(crypto_|pay_method_)\w*"""),
        Regex("""\bCryptoInvoice\w*"""),
        Regex("""\bCryptoPay\w*"""),
        Regex("""\bCryptoStartRefusal\b"""),
        Regex("""\bLocalCryptoPay\b"""),
        Regex("""\bPaymentMethodRow\b"""),
        Regex("""\bNonPlayPlanSurface\b"""),
        Regex("""\bopenCryptoInvoiceViewController\b"""),
        Regex("""\.cryptoAssets\b"""),
        Regex("""\.cryptoTerm\b"""),
        Regex("""\bSdk\.(paymentMethods|cryptoAssetSells|paymentMethodAllowsPlan|cryptoAssetTicker|cryptoWalletSuggestions|checkCryptoPaymentUri|cryptoTermNoticeDue)\b"""),
        Regex("""\bSdk\.(CryptoAsset\w*|CryptoPayment\w*|CryptoInvoice\w*|PaymentMethodCard|EventStoreZcash|EventStoreMonero|SubscriptionStoreCrypto)\b"""),
    )

    private fun src(path: String) = File("src/$path")

    // a source without its comments (which may name what they explain)
    private fun code(text: String): String =
        text.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .joinToString("\n") { line -> line.replace(Regex("""(^|\s)//.*$"""), "") }

    private fun code(file: File): String = code(file.readText())

    private fun kotlinFiles(sourceSet: String): List<File> =
        src(sourceSet).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Each java source set's srcDirs in the build file, by its name. */
    private fun sourceSetDirs(): Map<String, String> {
        val gradle = code(File("build.gradle").readText())
        val sourceSets = gradle.substring(gradle.indexOf("    sourceSets {"))
        return Regex("""\b(\w+)\s*\{\s*java\s*\{\s*srcDirs\s*\+=\s*\[([^\]]*)]""")
            .findAll(sourceSets)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun onlyTheFlavorsThatSellOutsideAStoreCompileTheCryptoSourceSet() {
        val dirs = sourceSetDirs()
        for (flavor in listOf("github", "solana_dapp", "ethos_dapp")) {
            assertTrue("$flavor does not compile src/cryptoPay", dirs[flavor]?.contains("'src/cryptoPay/java'") == true)
        }
        assertEquals("play compiles more than src/google", "'src/google/java'", dirs["play"]?.trim())
        for (tests in listOf("testGithub", "testSolana_dapp", "testEthos_dapp")) {
            assertTrue("$tests does not run the crypto tests", dirs[tests]?.contains("'src/testCryptoPay/java'") == true)
        }
        assertFalse(dirs.containsKey("testPlay"))
        // the shared sets every flavor compiles carry none of it
        for (shared in listOf("main", "test", "androidTest")) {
            assertFalse("$shared compiles src/cryptoPay", dirs[shared]?.contains("cryptoPay") == true)
        }
    }

    @Test
    fun whatPlayCompilesNamesNoCryptoCodeOrCopy() {
        // play's own set, the sets Play compiles beside it, and the shared ones
        val playSourceSets = listOf("main", "play", "google", "debug")
        val found = playSourceSets.flatMap { kotlinFiles(it) }.flatMap { file ->
            val text = code(file)
            cryptoOnly.flatMap { pattern -> pattern.findAll(text).map { it.value } }
                .filter { it !in hooks }
                .map { "${file.path}: $it" }
        }
        assertEquals(emptyList<String>(), found)
    }

    // what each side's PaymentMethodHooks.kt defines at the top level, private ones aside
    private fun definedHooks(sourceSet: String): Set<String> =
        Regex("""(?m)^fun\s+(\w+)\s*\(""")
            .findAll(code(src("$sourceSet/java/com/bringyour/network/ui/upgrade/PaymentMethodHooks.kt")))
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun bothSidesDefineTheSameHooks() {
        // Play's are empty (whatPlayCompilesNamesNoCryptoCodeOrCopy reads them too)
        assertEquals(hooks, definedHooks("google"))
        assertEquals(hooks, definedHooks("cryptoPay"))
    }
}
