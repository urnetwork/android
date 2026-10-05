package com.bringyour.network.ui.wallet

import com.bringyour.network.ui.login.BittensorLoginNext
import com.bringyour.network.ui.login.WalletCreateBundle
import com.bringyour.network.ui.login.bittensorCreateBundle
import com.bringyour.network.ui.login.bittensorLoginNext
import com.bringyour.network.ui.settings.AddWalletAuth
import com.bringyour.network.ui.settings.BittensorAddReturn
import com.bringyour.network.ui.settings.BittensorAddSignInController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign-in, network create and adding a sign-in method refuse a pasted Bittensor
 * signature from another account than the entered address with error.code
 * signature_mismatch (sign-in and create when they ask for result_errors). The server
 * cannot say which account signed. After the manual sheet (Talisman or TAO.com on
 * Android) each flow names the wallet to sign again in; a wallet that signed itself
 * (the WalletConnect bridge) and every other refusal keep the server's message.
 *
 * Plain JVM: the flows' decisions and their sources; no SDK, no device.
 */
class WalletAuthSignatureMismatchTest {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val message = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000"
    private val signature = "0x" + "ab".repeat(64)
    private val serverText = "The signature does not match this wallet address. Sign the challenge with this address."

    @Test
    fun `only the code after a pasted signature names the wallet`() {
        assertEquals("signature_mismatch", BittensorWallets.SIGNATURE_MISMATCH)
        assertEquals(BittensorWallets.TAO_COM, bittensorSignatureMismatchWallet("signature_mismatch", BittensorWallets.TAO_COM))
        assertEquals(BittensorWallets.TALISMAN, bittensorSignatureMismatchWallet("signature_mismatch", BittensorWallets.TALISMAN))
        // the bridge signed: the user pasted nothing
        assertNull(bittensorSignatureMismatchWallet("signature_mismatch", null))
        assertNull(bittensorSignatureMismatchWallet("signature_mismatch", ""))
        // no code, or one the apps have no words for
        assertNull(bittensorSignatureMismatchWallet(null, BittensorWallets.TAO_COM))
        assertNull(bittensorSignatureMismatchWallet("something_new", BittensorWallets.TAO_COM))
    }

    @Test
    fun `sign-in names the wallet the proof was pasted from`() {
        for (walletId in listOf(BittensorWallets.TAO_COM, BittensorWallets.TALISMAN)) {
            val proof = BittensorProof(walletId, BittensorWallets.PURPOSE_LOGIN, alice, message, signature)
            assertEquals(
                BittensorLoginNext.SignatureMismatch(walletId),
                bittensorLoginNext(proof, null, false, serverText, "signature_mismatch"),
            )
        }
        val proof = BittensorProof(BittensorWallets.TAO_COM, BittensorWallets.PURPOSE_LOGIN, alice, message, signature)
        // without the code (an older server, or another refusal) the message stands
        assertEquals(BittensorLoginNext.Failed(serverText), bittensorLoginNext(proof, null, false, serverText, null))
        assertEquals(BittensorLoginNext.Failed("timeout"), bittensorLoginNext(proof, null, false, "timeout"))
    }

    @Test
    fun `network create carries the wallet the proof was pasted from`() {
        val proof = BittensorProof(BittensorWallets.TALISMAN, BittensorWallets.PURPOSE_CREATE, alice, message, signature)
        assertEquals(BittensorWallets.TALISMAN, bittensorCreateBundle(proof).manualWalletId)
        // a bundle a wallet signed (the bridge) names none
        assertNull(WalletCreateBundle("TAO", alice, message, signature).manualWalletId)
    }

    // the manual sheet's session: proves whatever is pasted
    private class ManualSession(override val walletId: String, override val purpose: String, override val message: String) :
        BittensorProofSession {
        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Proven(BittensorProof(walletId, purpose, address.trim(), message, signature.trim()))
    }

    @Test
    fun `an added wallet pasted on the sheet carries its wallet, a bridge return does not`() {
        val added = mutableListOf<AddWalletAuth>()
        val flow = BittensorProofFlow(BittensorBridgeReturns()) { 0L }
        val controller = BittensorAddSignInController(
            flow = flow,
            scope = CoroutineScope(Dispatchers.Unconfined),
            api = { null },
            setError = {},
            defaultError = { "default" },
            addWalletAuth = { added += it },
        )
        flow.open(BittensorWallets.PURPOSE_ADD)
        val request = flow.choose(BittensorWallets.TAO_COM)!!
        flow.sessionReady(request, ManualSession(request.walletId, request.purpose, message))
        flow.updateAddress(alice)
        flow.updateSignature(signature)
        controller.submit()
        assertEquals(listOf(AddWalletAuth("TAO", alice, message, signature, manualWalletId = BittensorWallets.TAO_COM)), added)

        val bridged = BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_ADD, alice, message, signature)
        controller.handleReturn(BittensorAddReturn.Proven(bridged))
        assertNull(added.last().manualWalletId)
    }

    private fun source(path: String): String = File("src/main/java/com/bringyour/network/ui/$path").readText()

    // AuthLoginArgs and NetworkCreateArgs are gomobile classes (they need the native
    // library), so this reads the wiring: the requests ask for coded refusals, and
    // each refusal goes through bittensorSignatureMismatchWallet.
    @Test
    fun `sign-in, create and add ask for the code and read it`() {
        val login = source("login/BittensorLogin.kt")
        assertTrue(login.contains("args.resultErrors = true"))
        assertTrue(login.contains("errorCode = result?.error?.code"))
        assertTrue(login.contains("setLoginError(signatureMismatchText(next.walletId))"))
        assertTrue(source("login/LoginCreateNetworkViewModel.kt").contains("args.resultErrors = true"))
        val create = source("login/LoginCreateNetwork.kt")
        assertTrue(create.contains("bittensorSignatureMismatchWallet("))
        assertTrue(create.contains("result.error.code"))
        assertTrue(source("LoginNavHost.kt").contains("manualWalletId = walletBundle.manualWalletId"))
        assertTrue(
            source("settings/SettingsViewModel.kt")
                .contains("AddAuthRefusal(result.error.message ?: \"Failed to add sign-in method\", result.error.code)")
        )
        val sheet = source("settings/AddAuthMethodSheet.kt")
        assertTrue(sheet.contains("bittensorSignatureMismatchWallet(refusal.code, manualWalletId)"))
        assertTrue(sheet.contains("addWallet(walletAuth, auth.manualWalletId)"))
    }
}
