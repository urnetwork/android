package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payout consent's flow, in every app's steps: Sign or Change wallet
 * opens the wallet chooser; the coldkey the consent will name is entered and
 * checked locally; a consent session of the chosen wallet takes the server's
 * challenge for it; the wallet answers on the manual sheet or the
 * WalletConnect bridge; the consent is submitted and its status read again.
 * C4 is CONSENT.md §4's flow, C7 amendment A1 (the session and the error
 * codes), C8 amendment A2 (a consent starts next epoch), C10 amendment A3
 * (the review's fixes: the network credential, forged returns).
 *
 * Plain JVM: a fake protocol source in place of the SDK, and a fake wallet
 * session that signs (or refuses) as the SDK's consent session does.
 */
class SnConsentFlowTest {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"
    private val bob = "5FHneW46xGXgs5mUiveU4sbTyGBzmstUspZC92UhjJM694ty"
    private val signature = "0x" + "ab".repeat(64)
    private val bridgeSignature = "0x" + "cd".repeat(64)

    // the server's challenge for a coldkey: the network statement after the protocol prefix
    private fun serverMessage(coldkeySs58: String) = "Approve URnetwork network wallet mapping\n" +
        "{\"schema\":\"urnetwork-network-wallet-mapping-consent-v1\",\"scope\":\"network\"," +
        "\"coldkey\":\"$coldkeySs58\",\"from_epoch\":1220,\"through_epoch\":66755}"

    private val none = SnConsentStatusState(active = false, coldkeySs58 = "")

    // a consent signed this epoch: active, its range starting next epoch (C8)
    private fun active(coldkeySs58: String) = SnConsentStatusState(active = true, coldkeySs58 = coldkeySs58)

    /**
     * The wallet: the SDK consent session over one challenge. Its proof
     * carries what the wallet signed, which is the session's message unless a
     * test hands back another. It records every message it signed.
     */
    private class FakeSigner(
        override val walletId: String,
        override val message: String,
        private val coldkeySs58: String,
        override val transport: String,
        private val bridgeSignature: String,
    ) : BittensorProofSession {
        override val purpose: String = BittensorWallets.PURPOSE_CONSENT
        var refuseWith: String? = null
        var signedMessage: String = message
        var signedBy: String = coldkeySs58
        val signed = mutableListOf<String>()

        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome {
            refuseWith?.let { return BittensorProofOutcome.Refused(it) }
            if (address.trim() != coldkeySs58) {
                return BittensorProofOutcome.Refused(BittensorWallets.ERROR_ADDRESS_MISMATCH)
            }
            signed.add(signedMessage)
            return BittensorProofOutcome.Proven(BittensorProof(walletId, purpose, signedBy, signedMessage, signature))
        }

        override fun bridgeUrl(): String = "https://ur.io/wallet-connect?provider=bittensor&wallet=$walletId&message=$message"

        // the session's verdicts on the next returns; then the wallet's own return
        val bridgeOutcomes = ArrayDeque<BittensorProofOutcome>()

        override fun handleBridgeReturn(uri: String, nowMillis: Long): BittensorProofOutcome {
            bridgeOutcomes.removeFirstOrNull()?.let { return it }
            signed.add(signedMessage)
            return BittensorProofOutcome.Proven(BittensorProof(walletId, purpose, signedBy, signedMessage, bridgeSignature))
        }
    }

    /** The SDK: the status, a consent session per challenge, and the submissions it was sent. */
    private inner class FakeSource : EarningsProtocolSource by NoProtocolSource {
        override val available: Boolean = true
        var status: Result<SnConsentStatusState> = Result.success(none)
        var statusReads = 0
        var networkCredential = true
        var transport = BittensorWallets.TRANSPORT_MANUAL
        // the challenge's refusal, when set
        var challengeError: Throwable? = null
        // when set, the next challenge waits for it
        var challengeGate: CompletableDeferred<Unit>? = null
        val challenges = mutableListOf<Pair<String, String>>()
        val signers = mutableListOf<FakeSigner>()
        var submitError: Throwable? = null
        // when set, a submission waits for it
        var submitGate: CompletableDeferred<Unit>? = null
        val submits = mutableListOf<Triple<String, String, String>>()

        override suspend fun consentStatus(): Result<SnConsentStatusState> {
            statusReads += 1
            return status
        }

        override fun hasNetworkCredential(): Boolean = networkCredential

        override suspend fun consentSession(walletId: String, coldkeySs58: String): Result<BittensorProofSession> {
            challenges.add(walletId to coldkeySs58)
            challengeGate?.let {
                challengeGate = null
                it.await()
            }
            challengeError?.let { return Result.failure(it) }
            val signer = FakeSigner(walletId, serverMessage(coldkeySs58), coldkeySs58, transport, bridgeSignature)
            signers.add(signer)
            return Result.success(signer)
        }

        override suspend fun consentSubmit(coldkeySs58: String, message: String, signature: String): Result<Unit> {
            submits.add(Triple(coldkeySs58, message, signature))
            submitGate?.await()
            submitError?.let { return Result.failure(it) }
            // the server keeps the new consent, starting next epoch
            status = Result.success(active(coldkeySs58))
            return Result.success(Unit)
        }
    }

    private fun flowFor(source: FakeSource, bridgeReturns: BittensorBridgeReturns = BittensorBridgeReturns()): SnConsentFlow =
        SnConsentFlow(
            CoroutineScope(Dispatchers.Unconfined),
            BittensorProofFlow(bridgeReturns) { 1_000L },
        ).also {
            it.reset(source)
            runBlocking { it.refresh() }
        }

    private fun tile(flow: SnConsentFlow) = snConsentTile(flow.state.value, flow.proofFlow.stage.value)

    /** Sign or Change, the wallet, the coldkey, Continue: the challenge for it. */
    private fun requestChallenge(
        flow: SnConsentFlow,
        coldkeySs58: String,
        walletId: String = BittensorWallets.TALISMAN,
        openUrl: (String) -> Boolean = { false },
    ) {
        flow.start()
        flow.chooseWallet(walletId)
        flow.updateColdkey(coldkeySs58)
        flow.continueWithColdkey(openUrl)
    }

    /** The steps on a manual wallet, the signature pasted, Continue. */
    private fun signManually(flow: SnConsentFlow, coldkeySs58: String, walletId: String = BittensorWallets.TALISMAN) {
        requestChallenge(flow, coldkeySs58, walletId)
        flow.proofFlow.updateSignature(signature)
        flow.submit()
    }

    // ---- 1. no consent, then sign, then active

    @Test
    fun `no consent shows none and Sign, and a signed consent is active from the next epoch`() {
        // C4: sn_consent_none with sn_consent_sign; the steps in order; C8: right after signing the
        // status is active, starting next epoch
        val source = FakeSource()
        val flow = flowFor(source)
        assertEquals(SnConsentTile(false, false, null, false, null, true), tile(flow))

        // the wallet first
        flow.start()
        assertEquals(BittensorProofStage.Choosing(), flow.proofFlow.stage.value)
        flow.chooseWallet(BittensorWallets.TALISMAN)
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        // then the coldkey, not prefilled; nothing is requested yet
        assertEquals(SnConsentColdkeyEntry(BittensorWallets.TALISMAN), flow.state.value.coldkeyEntry)
        assertTrue(source.challenges.isEmpty())
        flow.updateColdkey(" $alice ")
        flow.continueWithColdkey()
        assertNull(flow.state.value.coldkeyEntry)
        // then the challenge for it, and the wallet's signature is awaited on the sheet, bound to it
        assertEquals(listOf(BittensorWallets.TALISMAN to alice), source.challenges)
        val signing = flow.proofFlow.stage.value as BittensorProofStage.Signing
        assertEquals(alice, signing.address)
        assertEquals(SnConsentTile(false, false, null, true, null, false), tile(flow))

        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertEquals(listOf(Triple(alice, serverMessage(alice), signature)), source.submits)
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        // the status is read again: active, with Change and its note
        assertEquals(2, source.statusReads)
        assertEquals(SnConsentTile(false, false, alice, false, null, true), tile(flow))
    }

    @Test
    fun `walletconnect signs on the bridge page and its return is submitted`() {
        // C4: the existing connector's WalletConnect wallet, its return handed over by the LoginActivity
        val bridgeReturns = BittensorBridgeReturns()
        val source = FakeSource().apply { transport = BittensorWallets.TRANSPORT_BROWSER_BRIDGE }
        val flow = flowFor(source, bridgeReturns)
        val opened = mutableListOf<String>()
        requestChallenge(flow, alice, BittensorWallets.WALLET_CONNECT) { url -> opened.add(url) }
        assertEquals(listOf(source.signers.single().bridgeUrl()), opened)
        assertEquals(BittensorProofStage.AwaitingBrowser(BittensorWallets.WALLET_CONNECT), flow.proofFlow.stage.value)
        assertTrue(tile(flow).signing)

        // the LoginActivity hands the waiting session's verdict over
        val action = bittensorReturnAction("ur://bittensor-sign-message?purpose=consent", 2_000L, bridgeReturns)
        flow.handleReturn(snConsentReturn(action, bridgeReturns)!!)
        assertEquals(listOf(Triple(alice, serverMessage(alice), bridgeSignature)), source.submits)
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        assertEquals(alice, tile(flow).activeColdkeySs58)
    }

    @Test
    fun `a submission in flight shows the waiting line and holds Sign`() {
        // C4: sn_consent_signing until the consent is submitted and its status read again
        val source = FakeSource().apply { submitGate = CompletableDeferred() }
        val flow = flowFor(source)
        signManually(flow, alice)
        assertTrue(flow.state.value.submitting)
        assertEquals(SnConsentTile(false, false, null, true, null, false), tile(flow))
        // Sign or Change does nothing until it ends
        flow.start()
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        source.submitGate!!.complete(Unit)
        assertEquals(SnConsentTile(false, false, alice, false, null, true), tile(flow))
    }

    // ---- 2. change wallet

    @Test
    fun `change wallet signs a new consent for the entered coldkey`() {
        // C4: Change reruns the steps with a coldkey the user enters; it takes effect from the next epoch (C8)
        val source = FakeSource().apply { status = Result.success(active(alice)) }
        val flow = flowFor(source)
        assertEquals(alice, tile(flow).activeColdkeySs58)

        flow.start()
        flow.chooseWallet(BittensorWallets.TAO_COM)
        // not prefilled with the coldkey paid now
        assertEquals("", flow.state.value.coldkeyEntry!!.address)
        flow.updateColdkey(bob)
        flow.continueWithColdkey()
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertEquals(listOf(BittensorWallets.TAO_COM to bob), source.challenges)
        assertEquals(listOf(Triple(bob, serverMessage(bob), signature)), source.submits)
        assertEquals(SnConsentTile(false, false, bob, false, null, true), tile(flow))
    }

    // ---- 3. expired challenge

    @Test
    fun `an expired challenge says the request expired`() {
        // C7: the session's expiry on the pasted answer, which closes the sheet: that challenge is spent
        val source = FakeSource()
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        source.signers.single().refuseWith = BittensorWallets.ERROR_EXPIRED
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        assertEquals(SnConsentTile(false, false, null, false, SnConsentOutcome.Expired, true), tile(flow))
        assertEquals(R.string.sn_consent_expired, snConsentOutcomeRes(SnConsentOutcome.Expired))
        assertTrue(source.submits.isEmpty())

        // C7: a challenge already past its expiry when it arrives (setNetworkConsentChallenge)
        source.challengeError = Exception("challenge_expired: the network consent challenge expired")
        requestChallenge(flow, alice)
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        assertEquals(SnConsentOutcome.Expired, tile(flow).outcome)

        // C7: the server's refusal of an expired message; the status is not read again
        source.challengeError = null
        source.submitError = Exception("challenge_expired: network wallet mapping original challenge is expired or not yet effective")
        signManually(flow, alice)
        assertEquals(1, source.submits.size)
        assertEquals(1, source.statusReads)
        assertEquals(SnConsentOutcome.Expired, tile(flow).outcome)

        // C7, C10: on the bridge page, only the session's own expiry ends the wait
        val bridgeReturns = BittensorBridgeReturns()
        val bridge = FakeSource().apply { transport = BittensorWallets.TRANSPORT_BROWSER_BRIDGE }
        val bridged = flowFor(bridge, bridgeReturns)
        requestChallenge(bridged, alice, BittensorWallets.WALLET_CONNECT) { true }
        bridge.signers.single().bridgeOutcomes.add(BittensorProofOutcome.Refused(BittensorWallets.ERROR_EXPIRED))
        val action = bittensorReturnAction("ur://bittensor-sign-message?purpose=consent", 400_000L, bridgeReturns)
        assertEquals(
            BittensorReturnAction.Failed(BittensorWallets.PURPOSE_CONSENT, BittensorWallets.ERROR_EXPIRED, null, null, BittensorWallets.WALLET_CONNECT),
            action,
        )
        assertFalse(bridgeReturns.waiting)
        bridged.handleReturn(snConsentReturn(action, bridgeReturns)!!)
        assertEquals(BittensorProofStage.Hidden, bridged.proofFlow.stage.value)
        assertEquals(SnConsentOutcome.Expired, tile(bridged).outcome)
        assertTrue(bridge.submits.isEmpty())

        // Sign again starts over and clears the line
        flow.start()
        assertNull(tile(flow).outcome)
    }

    // ---- 4. signature mismatch

    @Test
    fun `a signature that is not the coldkey's says it didn't match`() {
        // C4, C7: SnSetWalletResult.Error.Code signature_mismatch shows sn_consent_failed; Sign stays to try again
        val source = FakeSource().apply {
            submitError = SnProtocolException(EarningsViewModel.SN_CODE_SIGNATURE_MISMATCH, "the signature does not verify for the entered address")
        }
        val flow = flowFor(source)
        signManually(flow, alice)
        assertEquals(SnConsentTile(false, false, null, false, SnConsentOutcome.SignatureMismatch, true), tile(flow))
        assertEquals(R.string.sn_consent_failed, snConsentOutcomeRes(SnConsentOutcome.SignatureMismatch))
    }

    // ---- 5. not the owner

    @Test
    fun `a session that is not the network owner's says only the owner can choose`() {
        // C7, C8: "network_owner_only: " from the challenge (no network credential, or the server's 403)
        val source = FakeSource().apply { challengeError = Exception("network_owner_only: this call administers the network") }
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        // no other attempt of this session can sign: Sign is disabled
        assertEquals(SnConsentTile(false, false, null, false, SnConsentOutcome.OwnerOnly, false), tile(flow))
        assertEquals(R.string.sn_consent_owner_only, snConsentOutcomeRes(SnConsentOutcome.OwnerOnly))

        // and from the submission
        val submitting = FakeSource().apply { submitError = Exception("network_owner_only: 403 Forbidden") }
        val other = flowFor(submitting)
        signManually(other, alice)
        assertEquals(SnConsentOutcome.OwnerOnly, tile(other).outcome)
        assertFalse(tile(other).actionEnabled)
    }

    // ---- 6. other errors

    @Test
    fun `other errors show in the app's existing surfaces`() {
        // C4: a challenge the chooser could not get returns to the chooser with its error
        val source = FakeSource().apply { challengeError = Exception("server_error: 500 Internal Server Error") }
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        assertEquals(BittensorProofStage.Choosing(R.string.login_error), flow.proofFlow.stage.value)
        assertNull(tile(flow).outcome)

        // C4: a failed submission shows its detail, as a failed connect does
        source.challengeError = null
        source.submitError = SnProtocolException(null, "the server is busy")
        signManually(flow, alice)
        assertEquals(SnConsentOutcome.Failed("the server is busy"), tile(flow).outcome)
        assertNull(snConsentOutcomeRes(SnConsentOutcome.Failed("the server is busy")))
        assertTrue(tile(flow).actionEnabled)

        // C4: a malformed pasted signature is the sheet's own error, corrected in place
        source.submitError = null
        requestChallenge(flow, alice)
        source.signers.last().refuseWith = BittensorWallets.ERROR_INVALID_SIGNATURE
        flow.proofFlow.updateSignature("0x12")
        flow.submit()
        assertEquals(R.string.bittensor_error_invalid_signature, (flow.proofFlow.stage.value as BittensorProofStage.Signing).errorRes)

        // C4: a browser that does not open returns to the chooser
        val bridge = FakeSource().apply { transport = BittensorWallets.TRANSPORT_BROWSER_BRIDGE }
        val bridged = flowFor(bridge)
        requestChallenge(bridged, alice, BittensorWallets.WALLET_CONNECT) { false }
        assertEquals(BittensorProofStage.Choosing(R.string.login_error), bridged.proofFlow.stage.value)
    }

    @Test
    fun `an unreadable status is unavailable until Try again reads it`() {
        // C4: the existing section error with Try again, never "no consent"
        val source = FakeSource().apply { status = Result.failure(Exception("chain_rpc_unreachable: timeout")) }
        val flow = flowFor(source)
        assertEquals(SnConsentTile(false, true, null, false, null, false), tile(flow))
        source.status = Result.success(active(alice))
        flow.retry()
        assertEquals(SnConsentTile(false, false, alice, false, null, true), tile(flow))
    }

    @Test
    fun `refusal codes come from the server's code or the error's prefix`() {
        // C7: SnSetWalletResult.Error.Code, else the "code: message" form
        assertEquals(SnConsentOutcome.OwnerOnly, snConsentOutcome(Exception("network_owner_only: no network credential")))
        assertEquals(SnConsentOutcome.Expired, snConsentOutcome(Exception("challenge_expired: past ExpiresAt")))
        assertEquals(SnConsentOutcome.SignatureMismatch, snConsentOutcome(SnProtocolException("signature_mismatch", "x")))
        assertEquals(SnConsentOutcome.OwnerOnly, snConsentOutcome(SnProtocolException(null, "network_owner_only: y")))
        assertEquals(SnConsentOutcome.Failed("timeout"), snConsentOutcome(Exception("timeout")))
    }

    // ---- 7. the app signs only the server message

    @Test
    fun `the wallet is handed exactly the server's message, and only that is submitted`() {
        // C4: never sign anything but the server-issued message; the sheet shows it whole, domain included
        val source = FakeSource()
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        assertEquals(serverMessage(alice), (flow.proofFlow.stage.value as BittensorProofStage.Signing).session.message)
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertEquals(listOf(serverMessage(alice)), source.signers.single().signed)
        assertEquals(listOf(serverMessage(alice)), source.submits.map { it.second })
    }

    @Test
    fun `a proof of any other message or coldkey is never submitted and reads sn_consent_failed`() {
        // C4: submit only when the proof's message and coldkey are the challenge's
        val source = FakeSource()
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        source.signers.single().signedMessage = serverMessage(bob)
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertTrue(source.submits.isEmpty())
        assertEquals(SnConsentOutcome.SignatureMismatch, tile(flow).outcome)

        requestChallenge(flow, alice)
        source.signers.last().signedBy = bob
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertTrue(source.submits.isEmpty())
        assertEquals(SnConsentOutcome.SignatureMismatch, tile(flow).outcome)

        // a bridge return of another message, coldkey or purpose
        val message = serverMessage(alice)
        for (proof in listOf(
            BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONSENT, alice, "$message ", signature),
            BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONSENT, bob, message, signature),
            BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONNECT, alice, message, signature),
        )) {
            requestChallenge(flow, alice)
            flow.handleReturn(SnConsentReturn.Proven(proof))
            assertTrue(proof.toString(), source.submits.isEmpty())
            assertEquals(proof.toString(), SnConsentOutcome.SignatureMismatch, tile(flow).outcome)
        }
    }

    // ---- 8. invalid coldkey

    @Test
    fun `an invalid coldkey is refused locally before any request`() {
        // C4: validateSs58 before the challenge; the entry says why and keeps the text
        val source = FakeSource()
        val flow = flowFor(source)
        flow.start()
        flow.chooseWallet(BittensorWallets.TALISMAN)
        for (address in listOf("", "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQ", "0x8f1c3a0e5d2b7c9a4e6f1d2c3b4a596877889900")) {
            flow.updateColdkey(address)
            flow.continueWithColdkey()
            assertEquals(address, SnConsentColdkeyEntry(BittensorWallets.TALISMAN, address, invalid = true), flow.state.value.coldkeyEntry)
        }
        assertTrue(source.challenges.isEmpty())
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)

        // editing clears the line; a valid coldkey goes on to its challenge
        flow.updateColdkey(alice)
        assertFalse(flow.state.value.coldkeyEntry!!.invalid)
        flow.continueWithColdkey()
        assertEquals(listOf(BittensorWallets.TALISMAN to alice), source.challenges)

        // the entry opens only from the chooser, and closes without a request
        flow.reset(source)
        flow.chooseWallet(BittensorWallets.TALISMAN)
        assertNull(flow.state.value.coldkeyEntry)
        flow.start()
        flow.chooseWallet(BittensorWallets.TALISMAN)
        flow.dismissColdkey()
        assertNull(flow.state.value.coldkeyEntry)
        assertEquals(1, source.challenges.size)
    }

    // ---- 9. stale challenge

    @Test
    fun `a stale challenge is never shown, signed or submitted`() {
        // C4: a challenge that arrives after its attempt was left; a proof of an earlier challenge
        val source = FakeSource().apply { challengeGate = CompletableDeferred() }
        val gate = source.challengeGate!!
        val flow = flowFor(source)
        requestChallenge(flow, alice)
        assertTrue(flow.proofFlow.stage.value is BittensorProofStage.Loading)
        // the user starts over with another coldkey before alice's challenge arrives
        requestChallenge(flow, bob)
        val signing = flow.proofFlow.stage.value as BittensorProofStage.Signing
        assertEquals(serverMessage(bob), signing.session.message)
        gate.complete(Unit)
        // alice's late challenge changed nothing
        assertEquals(signing, flow.proofFlow.stage.value)
        flow.proofFlow.updateSignature(signature)
        flow.submit()
        assertEquals(listOf(Triple(bob, serverMessage(bob), signature)), source.submits)

        // a proof of the earlier challenge's message, after a new one was issued
        requestChallenge(flow, alice)
        flow.handleReturn(SnConsentReturn.Proven(
            BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONSENT, bob, serverMessage(bob), signature)
        ))
        assertEquals(1, source.submits.size)
        assertEquals(SnConsentOutcome.SignatureMismatch, tile(flow).outcome)

        // with no challenge out for signature (a new device), a return signs nothing either
        val next = FakeSource()
        flow.reset(next)
        assertEquals(SnConsentState(), flow.state.value)
        flow.handleReturn(SnConsentReturn.Proven(
            BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONSENT, alice, serverMessage(alice), signature)
        ))
        assertEquals(1, source.submits.size)
        assertTrue(next.submits.isEmpty())
    }

    // ---- 10. the review's fixes (C10)

    @Test
    fun `without the network credential Sign is not offered, and the block says only the owner can choose`() {
        // C10, probe F2: Api.hasNetworkCredential is false on a client token only, or once the
        // server rejected the network's; the challenge could only fail with network_owner_only
        val source = FakeSource().apply { networkCredential = false }
        val flow = flowFor(source)
        assertEquals(SnConsentTile(false, false, null, false, SnConsentOutcome.OwnerOnly, false), tile(flow))
        flow.start()
        assertEquals(BittensorProofStage.Hidden, flow.proofFlow.stage.value)
        assertTrue(source.challenges.isEmpty())
        // the consent paid now still shows, without Change
        source.status = Result.success(active(alice))
        flow.retry()
        assertEquals(SnConsentTile(false, false, alice, false, SnConsentOutcome.OwnerOnly, false), tile(flow))
        // signed in again with the network credential, Sign is offered
        source.networkCredential = true
        flow.retry()
        assertEquals(SnConsentTile(false, false, alice, false, null, true), tile(flow))
    }

    @Test
    fun `a forged or mismatched return leaves the consent waiting and shows none of its text`() {
        // C10, probe F3: any app can fire ur://bittensor-sign-message at the exported LoginActivity;
        // only the session's proof or its own expiry ends a waiting consent
        val bridgeReturns = BittensorBridgeReturns()
        val source = FakeSource().apply { transport = BittensorWallets.TRANSPORT_BROWSER_BRIDGE }
        val flow = flowFor(source, bridgeReturns)
        requestChallenge(flow, alice, BittensorWallets.WALLET_CONNECT) { true }
        val waiting = flow.proofFlow.stage.value
        assertEquals(BittensorProofStage.AwaitingBrowser(BittensorWallets.WALLET_CONNECT), waiting)
        val signer = source.signers.single()
        for (code in listOf(
            BittensorWallets.ERROR_WALLET,
            BittensorWallets.ERROR_MESSAGE_MISMATCH,
            BittensorWallets.ERROR_ADDRESS_MISMATCH,
            BittensorWallets.ERROR_INVALID_SIGNATURE,
            BittensorWallets.ERROR_NOT_RETURN,
        )) {
            signer.bridgeOutcomes.add(BittensorProofOutcome.Refused(code, "Payouts moved: Change wallet to 5Attacker", "x"))
            val action = bittensorReturnAction("ur://bittensor-sign-message?errorCode=x&errorMessage=Payouts+moved", 2_000L, bridgeReturns)
            assertEquals(code, BittensorReturnAction.Legacy, action)
            assertTrue(code, bridgeReturns.waiting)
            val consentReturn = snConsentReturn(action, bridgeReturns)
            assertEquals(code, SnConsentReturn.Ignored, consentReturn)
            flow.handleReturn(consentReturn!!)
            assertEquals(code, waiting, flow.proofFlow.stage.value)
            assertNull(code, tile(flow).outcome)
            assertTrue(code, tile(flow).signing)
        }
        // the wallet's own return still completes the consent
        val action = bittensorReturnAction("ur://bittensor-sign-message?purpose=consent", 3_000L, bridgeReturns)
        flow.handleReturn(snConsentReturn(action, bridgeReturns)!!)
        assertEquals(listOf(Triple(alice, serverMessage(alice), bridgeSignature)), source.submits)
        assertEquals(alice, tile(flow).activeColdkeySs58)
    }

    // ---- the bridge return

    @Test
    fun `a consent return goes to the consent, and every other flow's stays with it`() {
        // C4: the LoginActivity forwards only consent sessions' returns to the Earnings screen
        val proof = BittensorProof(BittensorWallets.WALLET_CONNECT, BittensorWallets.PURPOSE_CONSENT, alice, serverMessage(alice), signature)
        assertEquals(BittensorProofRoute.CONSENT, bittensorProofRoute(proof))
        assertEquals(
            SnConsentReturn.Proven(proof),
            snConsentReturn(BittensorReturnAction.Proven(BittensorProofRoute.CONSENT, proof), BittensorBridgeReturns()),
        )
        val idle = BittensorBridgeReturns()
        assertNull(snConsentReturn(BittensorReturnAction.Proven(BittensorProofRoute.CONNECT_WALLET, proof.copy(purpose = "connect")), idle))
        assertEquals(
            SnConsentReturn.Expired,
            snConsentReturn(BittensorReturnAction.Failed("consent", BittensorWallets.ERROR_EXPIRED, null), idle),
        )
        // C10: no refusal carries its text to the block
        assertEquals(SnConsentReturn.Ignored, snConsentReturn(BittensorReturnAction.Failed("consent", "wallet_error", "User rejected."), idle))
        assertNull(snConsentReturn(BittensorReturnAction.Failed("add", "wallet_error", "User rejected."), idle))
        // a return nothing waits for is the login's, unless a consent session waits
        assertNull(snConsentReturn(BittensorReturnAction.Legacy, idle))
        val waiting = BittensorBridgeReturns().apply { begin(FakeSigner("walletconnect", "m", alice, BittensorWallets.TRANSPORT_BROWSER_BRIDGE, signature)) }
        assertEquals(BittensorWallets.PURPOSE_CONSENT, waiting.waitingPurpose)
        assertEquals(SnConsentReturn.Ignored, snConsentReturn(BittensorReturnAction.Legacy, waiting))

        // held for the screen, taken once
        SnConsentReturns.deliver(SnConsentReturn.Proven(proof))
        assertEquals(SnConsentReturn.Proven(proof), SnConsentReturns.take())
        assertNull(SnConsentReturns.take())
    }
}
