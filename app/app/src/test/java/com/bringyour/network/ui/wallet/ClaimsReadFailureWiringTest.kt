package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claims read's failure as wired into the Earnings screen, read from the
 * sources: the screen needs a device. The Unclaimed tile and the claim dialog
 * used to say the chain RPC is unreachable for every failure, and the view model
 * dropped the SDK's code that tells them apart.
 */
class ClaimsReadFailureWiringTest {

    private fun source(name: String): String =
        File("src/main/java/com/bringyour/network/ui/wallet/$name").readText()

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        val to = source.indexOf(end, from + start.length)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun `the Unclaimed tile says what the failure was`() {
        val tile = between(source("EarningsScreen.kt"), "private fun UnclaimedTile(", "@Preview")
        assertFalse(tile.contains("R.string.chain_rpc_unreachable"))
        assertTrue(tile.contains("claimsReadFailureText(claimsError)"))
    }

    @Test
    fun `the claim dialog says what the failure was`() {
        val unavailable = between(source("ClaimDialog.kt"), "is ClaimDialogState.Unavailable -> {", "DialogButtons(")
        assertFalse(unavailable.contains("R.string.chain_rpc_unreachable"))
        assertTrue(unavailable.contains("claimsReadFailureText(state.detail)"))
    }

    @Test
    fun `the view model keeps the SDK's code`() {
        val model = source("EarningsViewModel.kt")
        assertTrue(model.contains("_claimsError.value = claimsReadFailureMessage(it)"))
    }
}
