package com.bringyour.network.ui.login

import com.bringyour.network.ui.wallet.BittensorProof
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a /auth/login answer to a Bittensor proof leads to (UPGRADE.md 4.6). */
class BittensorLoginTest {

    private val proof = BittensorProof(
        walletId = "taocom",
        purpose = "login",
        address = "5GrwvaEF5zXb26Fz9rcQpDWS57CtERHpNehXCPcNoHGKutQY",
        message = "Sign in to URnetwork\nChallenge: abc\nTimestamp: 1757340000",
        signature = "0x" + "ab".repeat(64),
    )

    @Test
    fun `a bound wallet signs in`() {
        assertEquals(BittensorLoginNext.SignedIn("jwt"), bittensorLoginNext(proof, "jwt", false, null))
    }

    @Test
    fun `an unbound wallet signs again with the same wallet to create a network`() {
        assertEquals(
            BittensorLoginNext.CreateNetwork("taocom", proof.address),
            bittensorLoginNext(proof, null, unlinkedWallet = true, errorMessage = null),
        )
        assertEquals(
            BittensorLoginNext.CreateNetwork("taocom", proof.address),
            bittensorLoginNext(proof, "", unlinkedWallet = true, errorMessage = null),
        )
    }

    @Test
    fun `an error wins over anything else`() {
        assertEquals(
            BittensorLoginNext.Failed("401 invalid signature"),
            bittensorLoginNext(proof, "jwt", true, "401 invalid signature"),
        )
        assertEquals(BittensorLoginNext.Failed(null), bittensorLoginNext(proof, null, false, null))
    }

    @Test
    fun `the create bundle carries the proof as a TAO wallet auth`() {
        val bundle = bittensorCreateBundle(proof.copy(purpose = "create"))
        assertEquals(WalletCreateBundle("TAO", proof.address, proof.message, proof.signature), bundle)
    }
}
