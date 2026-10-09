package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Reported defect: connecting the Solana payout wallet with Brave Wallet installed said
 * "No Solana wallets were found installed on this device. Please install a wallet and try
 * again." No wallet app answered the Mobile Wallet Adapter request there, but the sheet
 * behind the alert takes the address by hand, which worked. The payout alert now points at
 * that manual entry in the sheet's own words and offers it; signing in needs a wallet's
 * signature, so its alert keeps the install line.
 */
class NoWalletManualEntryTest {

    private val res = File("src/main/res")

    private val english = "No compatible Solana wallet app was found on this device. " +
        "Choose “Enter address manually” to paste your wallet address instead."

    // the values of strings.xml, with android's resource escapes undone
    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { element ->
                element.getAttribute("name") to element.textContent
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
                    .replace("\\\\", "\\")
            }
    }

    @Test
    fun `the payout no-wallet line points at Enter address manually`() {
        val values = strings(File(res, "values"))
        assertEquals(english, values["no_wallets_found_enter_address_manually"])
        assertEquals("Enter address manually", values["enter_address_manually"])
    }

    @Test
    fun `every locale names Enter address manually in its own words`() {
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val wrong = mutableListOf<String>()
        for (locale in locales) {
            val values = strings(locale)
            val line = values["no_wallets_found_enter_address_manually"]
            val label = values["enter_address_manually"]
            when {
                line.isNullOrEmpty() -> wrong.add("${locale.name}: not translated")
                line == english -> wrong.add("${locale.name}: English")
                label.isNullOrEmpty() -> wrong.add("${locale.name}: no enter_address_manually")
                !line.contains(label) -> wrong.add("${locale.name}: does not name \"$label\": $line")
                !line.contains("Solana") -> wrong.add("${locale.name}: drops Solana: $line")
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    private fun source(path: String) = File("src/$path").readText()

    // the call of NoSolanaWalletsAlert that starts at or after `from`, up to its closing paren
    private fun alertCall(text: String, from: Int = 0): String? {
        val start = text.indexOf("NoSolanaWalletsAlert(", from)
        if (start < 0) {
            return null
        }
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    @Test
    fun `the payout alert offers manual entry`() {
        val screen = source("main/java/com/bringyour/network/ui/wallet/EarningsScreen.kt")
        val noWalletApp = screen.indexOf("if (solanaState is SolanaConnectState.NoWalletApp)")
        assertTrue("the earnings screen has no no-wallet alert", 0 <= noWalletApp)
        val call = alertCall(screen, noWalletApp)
        assertNotNull("the no-wallet branch shows no NoSolanaWalletsAlert", call)
        // the sheet stays open under the alert: its manual step is one call away
        assertTrue(
            "the payout alert does not open the sheet's manual entry: $call",
            call!!.contains("onEnterManually = { solanaWalletViewModel.showManualStep() }"),
        )
        assertTrue(call.contains("onDismiss = { solanaWalletViewModel.dismissConnectState() }"))
    }

    @Test
    fun `the alert says and offers manual entry only when it can open it`() {
        val alert = source("main/java/com/bringyour/network/ui/login/NoSolanaWalletsAlert.kt")
        assertTrue("the alert cannot be given manual entry", alert.contains("onEnterManually: (() -> Unit)? = null"))
        // with manual entry: the line that names it, and the control itself
        assertTrue(
            "the alert never shows the line that names manual entry",
            alert.contains("R.string.no_wallets_found_enter_address_manually"),
        )
        assertTrue("the alert never offers Enter address manually", alert.contains("R.string.enter_address_manually"))
        assertTrue("Enter address manually does not open manual entry", alert.contains("onClick = onEnterManually"))
        // without (signing in): the install line
        assertTrue("signing in lost its install line", alert.contains("R.string.no_wallets_found_alert_content"))
    }

    @Test
    fun `signing in keeps the install line, since it has no manual entry`() {
        val flavors = listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")
        for (flavor in flavors) {
            val login = source("$flavor/java/com/bringyour/network/ui/login/LoginInitial.kt")
            val call = alertCall(login)
            assertNotNull("$flavor shows no NoSolanaWalletsAlert", call)
            assertFalse("$flavor offers manual entry at sign-in: $call", call!!.contains("onEnterManually"))
        }
        // and its line says nothing of an address field it does not have
        val install = strings(File(res, "values"))["no_wallets_found_alert_content"]
        assertNotNull(install)
        assertFalse(install!!.contains("Enter address manually"))
    }
}
