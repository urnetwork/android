package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wallet chooser and manual proof state around the SDK session
 * (UPGRADE.md 4.6). The session is a fake that applies the SDK's address
 * rule; the SDK's own checks are covered in sdk/bittensor_wallet_test.go.
 */
class BittensorWalletProofTest {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val bob = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    private val message = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000"
    private val signature = "0x" + "ab".repeat(64)

    private class FakeSession(
        override val walletId: String,
        override val purpose: String,
        override val message: String,
        private val expectedAddress: String?,
        private val refuseWith: String? = null,
    ) : BittensorProofSession {
        val calls = mutableListOf<Triple<String, String, Long>>()

        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome {
            calls.add(Triple(address, signature, nowMillis))
            refuseWith?.let { return BittensorProofOutcome.Refused(it) }
            if (expectedAddress != null && address.trim() != expectedAddress) {
                return BittensorProofOutcome.Refused(BittensorWallets.ERROR_ADDRESS_MISMATCH)
            }
            return BittensorProofOutcome.Proven(
                BittensorProof(walletId, purpose, address.trim(), message, signature.trim())
            )
        }
    }

    private fun fakeFor(request: BittensorProofRequest, refuseWith: String? = null) =
        FakeSession(request.walletId, request.purpose, message, request.expectedAddress, refuseWith)

    @Test
    fun `exactly talisman and tao com are offered, talisman first`() {
        assertEquals(listOf("talisman", "taocom"), BittensorWallets.walletIds)
        assertEquals("android", BittensorWallets.PLATFORM)
    }

    @Test
    fun `choosing a wallet starts a session for that wallet and purpose`() {
        val flow = BittensorProofFlow { 1000L }
        flow.open(BittensorWallets.PURPOSE_CONNECT, " $alice ")
        val request = flow.choose(BittensorWallets.TAO_COM)
        assertEquals(BittensorProofRequest("taocom", "connect", alice), request)
        assertTrue(flow.stage.value is BittensorProofStage.Loading)
        // a second tap while loading does nothing
        assertNull(flow.choose(BittensorWallets.TALISMAN))
        // an unsupported wallet is never started
        val other = BittensorProofFlow { 0L }
        other.open(BittensorWallets.PURPOSE_LOGIN)
        assertNull(other.choose("subwallet-js"))
    }

    @Test
    fun `the typed address is prefilled and the challenge is shown`() {
        val flow = BittensorProofFlow { 1000L }
        flow.open(BittensorWallets.PURPOSE_CONNECT, alice)
        val request = flow.choose(BittensorWallets.TALISMAN)!!
        flow.sessionReady(request, fakeFor(request))
        val signing = flow.stage.value as BittensorProofStage.Signing
        assertEquals(alice, signing.address)
        assertEquals("", signing.signature)
        assertEquals(message, signing.session.message)
    }

    @Test
    fun `a stale session for another request is dropped`() {
        val flow = BittensorProofFlow { 1000L }
        flow.open(BittensorWallets.PURPOSE_LOGIN)
        val request = flow.choose(BittensorWallets.TALISMAN)!!
        flow.dismiss()
        flow.sessionReady(request, fakeFor(request))
        assertEquals(BittensorProofStage.Hidden, flow.stage.value)
    }

    @Test
    fun `a challenge failure returns to the chooser with an error`() {
        val flow = BittensorProofFlow { 1000L }
        flow.open(BittensorWallets.PURPOSE_LOGIN)
        val request = flow.choose(BittensorWallets.TALISMAN)!!
        flow.sessionFailed(request)
        assertEquals(BittensorProofStage.Choosing(R.string.login_error), flow.stage.value)
    }

    @Test
    fun `an accepted answer closes the sheets and carries the clock`() {
        val flow = BittensorProofFlow { 4242L }
        flow.open(BittensorWallets.PURPOSE_LOGIN)
        val request = flow.choose(BittensorWallets.TAO_COM)!!
        val session = fakeFor(request)
        flow.sessionReady(request, session)
        flow.updateAddress(bob)
        flow.updateSignature(signature)
        val proof = flow.submit()
        assertEquals(BittensorProof("taocom", "login", bob, message, signature), proof)
        assertEquals(listOf(Triple(bob, signature, 4242L)), session.calls)
        assertEquals(BittensorProofStage.Hidden, flow.stage.value)
    }

    @Test
    fun `a different account than the typed one is refused with the mismatch message`() {
        val flow = BittensorProofFlow { 1L }
        flow.open(BittensorWallets.PURPOSE_CONNECT, alice)
        val request = flow.choose(BittensorWallets.TALISMAN)!!
        flow.sessionReady(request, fakeFor(request))
        flow.updateAddress(bob)
        flow.updateSignature(signature)
        assertNull(flow.submit())
        val signing = flow.stage.value as BittensorProofStage.Signing
        assertEquals(R.string.earnings_wallet_mismatch, signing.errorRes)
        // editing clears the error; the corrected answer is accepted
        flow.updateAddress(alice)
        assertNull((flow.stage.value as BittensorProofStage.Signing).errorRes)
        assertEquals(alice, flow.submit()?.address)
    }

    @Test
    fun `session refusals map to their messages`() {
        val expected = mapOf(
            "invalid_signature" to R.string.bittensor_error_invalid_signature,
            "challenge_expired" to R.string.bittensor_error_challenge_expired,
            "message_mismatch" to R.string.bittensor_error_message_mismatch,
            "address_mismatch" to R.string.earnings_wallet_mismatch,
            "invalid_ss58_address" to R.string.invalid_ss58_address,
            "not_awaiting_wallet" to R.string.login_error,
            "" to R.string.login_error,
        )
        for ((code, res) in expected) {
            assertEquals(code, res, BittensorWallets.errorRes(code))
            val flow = BittensorProofFlow { 1L }
            flow.open(BittensorWallets.PURPOSE_LOGIN)
            val request = flow.choose(BittensorWallets.TALISMAN)!!
            flow.sessionReady(request, fakeFor(request, refuseWith = code))
            flow.updateAddress(alice)
            flow.updateSignature(signature)
            assertNull(flow.submit())
            assertEquals(code, res, (flow.stage.value as BittensorProofStage.Signing).errorRes)
        }
    }

    @Test
    fun `proofs route by purpose`() {
        fun proof(purpose: String) = BittensorProof("talisman", purpose, alice, message, signature)
        assertEquals(BittensorProofRoute.LOGIN, bittensorProofRoute(proof("login")))
        assertEquals(BittensorProofRoute.CREATE_NETWORK, bittensorProofRoute(proof("create")))
        assertEquals(BittensorProofRoute.CONNECT_WALLET, bittensorProofRoute(proof("connect")))
        assertNull(bittensorProofRoute(proof("add_auth")))
    }

    @Test
    fun `the create network hop skips the chooser and binds the address`() {
        val flow = BittensorProofFlow { 1L }
        val request = flow.openForWallet(BittensorWallets.TAO_COM, BittensorWallets.PURPOSE_CREATE, alice)
        assertEquals(BittensorProofRequest("taocom", "create", alice), request)
    }
}
