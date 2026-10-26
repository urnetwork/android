package com.bringyour.network.ui.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The manual proof sheet with a payout consent to sign (support inbox 8058,
 * 8073). The consent is about 2 KB of JSON, taller than a phone screen; the
 * sheet must still let the user reach the signature field and Continue below
 * it. The session is a fake (the SDK's needs the native library), and the
 * message is a synthetic statement of the server's shape and length.
 */
@RunWith(AndroidJUnit4::class)
class BittensorProofSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val coldkey = SampleProtocolSource.SAMPLE_COLDKEY

    private class ConsentSession(override val message: String) : BittensorProofSession {
        override val walletId = BittensorWallets.TALISMAN
        override val purpose = BittensorWallets.PURPOSE_CONSENT

        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Proven(BittensorProof(walletId, purpose, address, message, signature))
    }

    // the server's network consent: its prefix and a canonical statement with
    // synthetic ids, keys and hashes, as long as a real one (1,737 bytes; the
    // ones issued in October 2026 were 1,710 to 1,740)
    private fun consentMessage(): String {
        fun bytes(count: Int, first: Int) = (0 until count).joinToString(",", "[", "]") { "${(first + it * 37) % 256}" }
        val zeros = List(32) { 0 }.joinToString(",", "[", "]")
        return "Approve URnetwork network wallet mapping\n" +
            "{\"schema\":\"urnetwork-network-wallet-mapping-consent-v1\",\"scope\":\"network\"," +
            "\"domain\":{\"chain_id\":964,\"genesis_hash\":${bytes(32, 160)},\"netuid\":25," +
            "\"coordinator\":\"0x0000000000000000000000000000000000000007\"," +
            "\"settlement_vault\":\"0x0000000000000000000000000000000000000008\"," +
            "\"deployment_id_hash\":${bytes(32, 176)},\"policy_hash\":${bytes(32, 192)},\"no_id\":7}," +
            "\"user_id\":${bytes(16, 112)},\"network_id\":${bytes(16, 128)},\"coldkey\":${bytes(32, 144)}," +
            "\"generation\":1,\"previous_hash\":$zeros,\"nonce\":${bytes(32, 208)}," +
            "\"issued_at\":1791244800,\"expires_at\":1791245100,\"from_epoch\":43,\"through_epoch\":65578," +
            "\"prospective\":{\"boundary\":{\"epoch\":42,\"block\":2116800,\"hash\":${bytes(32, 224)}}," +
            "\"signer\":\"0x0000000000000000000000000000000000000009\",\"signature\":${bytes(65, 117)}}}"
    }

    @Test
    fun aConsentToSignByHandScrollsToTheSignatureAndContinue() {
        val message = consentMessage()
        // a real consent is 1.7 KB or more
        assertTrue(message.length.toString(), 1700 <= message.length)
        val flow = BittensorProofFlow(BittensorBridgeReturns(), nowMillis = { 0L })
        val request = flow.openForWallet(BittensorWallets.TALISMAN, BittensorWallets.PURPOSE_CONSENT, coldkey)
        assertNotNull(request)
        flow.sessionReady(request!!, ConsentSession(message))
        var submits = 0
        compose.setContent {
            URNetworkTheme {
                BittensorProofSheets(
                    flow = flow,
                    onChoose = {},
                    onSubmit = {
                        flow.submit()
                        submits += 1
                    },
                    displayName = { "Talisman" },
                )
            }
        }

        // the address field holds the coldkey; the second field is the signature
        compose.onAllNodes(hasSetTextAction())[1]
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("0x" + "ab".repeat(64))
        compose.onNodeWithText(context.getString(R.string.continue_txt))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        compose.runOnIdle {
            assertEquals(1, submits)
            assertEquals(BittensorProofStage.Hidden, flow.stage.value)
        }
    }
}
