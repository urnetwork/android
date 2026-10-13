package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the payout consent block shows for each state of the read, the
 * wallet sheets and the last attempt. C4 is CONSENT.md §4's states, C8
 * amendment A2's meaning of active, C10 amendment A3's network credential.
 */
class SnConsentTileTest {

    private val alice = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY"

    private fun loaded(active: Boolean, coldkeySs58: String = alice) =
        SnConsentState(SnConsentLoad.Loaded(SnConsentStatusState(active, coldkeySs58)), networkCredential = true)

    private class Session : BittensorProofSession {
        override val walletId: String = BittensorWallets.TALISMAN
        override val purpose: String = BittensorWallets.PURPOSE_CONSENT
        override val message: String = "Approve URnetwork network wallet mapping\n{}"
        override fun handleSignature(address: String, signature: String, nowMillis: Long): BittensorProofOutcome =
            BittensorProofOutcome.Refused(BittensorWallets.ERROR_INVALID_SIGNATURE)
    }

    @Test
    fun `a status not read yet is a spinner without Sign`() {
        // C4
        assertEquals(SnConsentTile(true, false, null, false, null, false), snConsentTile(SnConsentState(), BittensorProofStage.Hidden))
    }

    @Test
    fun `an unreadable status is unavailable, never no consent`() {
        // C4: other errors keep the app's existing surface (the section's Try again)
        assertEquals(
            SnConsentTile(false, true, null, false, null, false),
            snConsentTile(SnConsentState(SnConsentLoad.Unavailable), BittensorProofStage.Hidden),
        )
    }

    @Test
    fun `no consent shows none with Sign`() {
        // C4, C8: sn_consent_none only when Active is false, here also for a consent whose range has passed
        assertEquals(SnConsentTile(false, false, null, false, null, true), snConsentTile(loaded(active = false), BittensorProofStage.Hidden))
        assertEquals(SnConsentTile(false, false, null, false, null, true), snConsentTile(loaded(false, ""), BittensorProofStage.Hidden))
    }

    @Test
    fun `an active consent shows its coldkey with Change, also while it starts next epoch`() {
        // C4: sn_consent_active with the coldkey, sn_consent_change and its note; C8: Active covers a
        // consent in force and one that starts next epoch alike
        assertEquals(SnConsentTile(false, false, alice, false, null, true), snConsentTile(loaded(active = true), BittensorProofStage.Hidden))
    }

    @Test
    fun `the waiting line shows from the challenge until the consent is submitted`() {
        // C4: sn_consent_signing while the challenge is fetched, the wallet signs, or the consent is submitted
        val request = BittensorProofRequest(BittensorWallets.TALISMAN, BittensorWallets.PURPOSE_CONSENT, alice)
        for (stage in listOf(
            BittensorProofStage.Loading(request),
            BittensorProofStage.Signing(Session(), alice, ""),
            BittensorProofStage.AwaitingBrowser(BittensorWallets.WALLET_CONNECT),
        )) {
            val tile = snConsentTile(loaded(active = false), stage)
            assertTrue(stage.toString(), tile.signing)
            assertFalse(stage.toString(), tile.actionEnabled)
        }
        val submitting = snConsentTile(loaded(active = true).copy(submitting = true), BittensorProofStage.Hidden)
        assertEquals(SnConsentTile(false, false, alice, true, null, false), submitting)
        // the chooser and the coldkey entry are not signing yet
        assertFalse(snConsentTile(loaded(active = false), BittensorProofStage.Choosing()).signing)
        val entering = loaded(active = false).copy(coldkeyEntry = SnConsentColdkeyEntry(BittensorWallets.TALISMAN))
        assertFalse(snConsentTile(entering, BittensorProofStage.Hidden).signing)
    }

    @Test
    fun `a refusal's line shows until the next attempt starts signing`() {
        // C4: the error lines; a new attempt's waiting line replaces them
        val expired = loaded(active = false).copy(outcome = SnConsentOutcome.Expired)
        assertEquals(SnConsentOutcome.Expired, snConsentTile(expired, BittensorProofStage.Hidden).outcome)
        assertTrue(snConsentTile(expired, BittensorProofStage.Hidden).actionEnabled)
        val request = BittensorProofRequest(BittensorWallets.TALISMAN, BittensorWallets.PURPOSE_CONSENT, alice)
        assertNull(snConsentTile(expired, BittensorProofStage.Loading(request)).outcome)
    }

    @Test
    fun `only the owner-only refusal holds Sign`() {
        // C4, C7: no other attempt of a session that is not the owner's can sign
        assertFalse(snConsentTile(loaded(false).copy(outcome = SnConsentOutcome.OwnerOnly), BittensorProofStage.Hidden).actionEnabled)
        for (outcome in listOf(SnConsentOutcome.Expired, SnConsentOutcome.SignatureMismatch, SnConsentOutcome.Failed("busy"))) {
            assertTrue(outcome.toString(), snConsentTile(loaded(true).copy(outcome = outcome), BittensorProofStage.Hidden).actionEnabled)
        }
    }

    @Test
    fun `without the network credential the block says only the owner can choose, without Sign`() {
        // C10, probe F2: whatever the status, and while no attempt is signing
        for (state in listOf(loaded(active = false), loaded(active = true))) {
            val tile = snConsentTile(state.copy(networkCredential = false), BittensorProofStage.Hidden)
            assertEquals(SnConsentOutcome.OwnerOnly, tile.outcome)
            assertFalse(tile.actionEnabled)
        }
        assertEquals(alice, snConsentTile(loaded(active = true).copy(networkCredential = false), BittensorProofStage.Hidden).activeColdkeySs58)
        // nothing read yet, nothing said
        assertNull(snConsentTile(SnConsentState(), BittensorProofStage.Hidden).outcome)
    }

    @Test
    fun `each refusal reads its pinned line, and others their detail`() {
        // C4: sn_consent_expired, sn_consent_failed, sn_consent_owner_only; other errors the detail
        assertEquals(R.string.sn_consent_expired, snConsentOutcomeRes(SnConsentOutcome.Expired))
        assertEquals(R.string.sn_consent_failed, snConsentOutcomeRes(SnConsentOutcome.SignatureMismatch))
        assertEquals(R.string.sn_consent_owner_only, snConsentOutcomeRes(SnConsentOutcome.OwnerOnly))
        assertNull(snConsentOutcomeRes(SnConsentOutcome.Failed("the server is busy")))
    }
}
