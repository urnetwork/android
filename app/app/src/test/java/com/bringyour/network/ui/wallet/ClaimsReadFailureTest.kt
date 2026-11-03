package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a failed claims read says, from the SDK's "<code>: <reason>". Plain JVM. */
class ClaimsReadFailureTest {

    @Test
    fun `a chain that can't be read says so`() {
        for (message in listOf(
            "chain_rpc_unreachable: dial tcp: i/o timeout",
            "chain_rpc_error: execution reverted",
            "chain_not_configured: vault address not set",
            "artifact_unavailable: 404",
            "ledger_unavailable: 404",
            "",
            null,
        )) {
            assertEquals(message, R.string.chain_rpc_unreachable, claimsReadFailureRes(message))
        }
    }

    @Test
    fun `no wallet reads as no wallet`() {
        assertEquals(R.string.connect_wallet_first, claimsReadFailureRes("connect_wallet_first: no coldkey"))
        assertEquals(R.string.connect_wallet_first, claimsReadFailureRes("local_state_unavailable: no gas key"))
    }

    @Test
    fun `anything else shows the SDK's own words, not an rpc it never reached`() {
        for (message in listOf(
            "server_error: 500",
            "not_claimable: below the floor",
            "http request attempts exhausted",
        )) {
            assertNull(message, claimsReadFailureRes(message))
        }
        assertEquals("500", snLedgerClaimFailureDetail("server_error: 500"))
    }

    @Test
    fun `the view model's message keeps the SDK's code`() {
        assertEquals(
            "connect_wallet_first: no coldkey",
            claimsReadFailureMessage(SnProtocolException("connect_wallet_first", "no coldkey")),
        )
        assertEquals("no code", claimsReadFailureMessage(SnProtocolException(null, "no code")))
        assertEquals("plain", claimsReadFailureMessage(IllegalStateException("plain")))
    }
}
