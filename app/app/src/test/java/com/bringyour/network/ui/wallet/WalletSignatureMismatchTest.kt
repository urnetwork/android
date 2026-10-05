package com.bringyour.network.ui.wallet

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * POST /sn/wallet refuses a pasted coldkey signature that does not verify for the
 * entered address with error.code signature_mismatch, and the SDK keeps that code in
 * SnError.code. The user signed with another account in their wallet; the server
 * cannot say which. After a manual entry (Talisman or TAO.com on Android) the card
 * says what to do in that wallet. A bridge signature and every other refusal keep the
 * server's message.
 *
 * Plain JVM: the protocol source's exception and the generated string resources; no
 * SDK, no device.
 */
class WalletSignatureMismatchTest {

    private val serverText = "The signature does not match this coldkey address. Sign the challenge with this address."

    @Test
    fun `a manual entry's signature from another account says to sign with the entered address`() {
        val refusal = SnProtocolException("signature_mismatch", serverText)
        assertEquals(
            WalletConnectState.SignatureMismatch(BittensorWallets.TAO_COM),
            walletConnectFailure(refusal, BittensorWallets.TAO_COM),
        )
        assertEquals(
            WalletConnectState.SignatureMismatch(BittensorWallets.TALISMAN),
            walletConnectFailure(refusal, BittensorWallets.TALISMAN),
        )
        assertEquals("signature_mismatch", EarningsViewModel.SN_CODE_SIGNATURE_MISMATCH)
    }

    @Test
    fun `a bridge signature and other refusals keep the server's message`() {
        // the bridge signed: the user pasted nothing
        assertEquals(
            WalletConnectState.Failed(serverText),
            walletConnectFailure(SnProtocolException("signature_mismatch", serverText), null),
        )
        assertEquals(
            WalletConnectState.Failed("400 invalid signature encoding"),
            walletConnectFailure(SnProtocolException("server_error", "400 invalid signature encoding"), BittensorWallets.TAO_COM),
        )
        assertEquals(
            WalletConnectState.Failed("timeout"),
            walletConnectFailure(IllegalStateException("timeout"), BittensorWallets.TAO_COM),
        )
    }

    private val res = File("src/main/res")

    /** The name -> text of a values directory's strings.xml. */
    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
    }

    @Test
    fun `the line names the wallet in every locale`() {
        val key = "bittensor_error_signature_mismatch"
        val english = strings(File(res, "values"))[key]
        assertEquals(
            "This signature isn\\'t from the address you entered. In %1\$s, sign the message with that address, then paste the signature again.",
            english,
        )
        val locales = res.listFiles { file -> file.name.startsWith("values-") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(locales.isNotEmpty())
        val missing = mutableListOf<String>()
        for (locale in locales) {
            val value = strings(locale)[key]
            if (value.isNullOrEmpty()) {
                missing.add(locale.name)
            } else {
                assertNotEquals("${locale.name} is English", english, value)
                assertTrue("${locale.name} drops the wallet name: $value", value.contains("%1\$s"))
            }
        }
        assertTrue("missing: $missing", missing.isEmpty())
    }
}
