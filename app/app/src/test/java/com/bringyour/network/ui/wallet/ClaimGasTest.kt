package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gas the claim dialogs ask for (VERSION2-LAUNCH.md §2.6). On runtime 478
 * the SDK signs every v1 claim with its estimate plus a quarter and at least
 * 2,700,000 gas (below that the v1 vault defers the payment), and every v2
 * claim with at least 2,600,000, and sends a claim only when the gas key holds
 * that limit at 1.1 × the gas price, else needs_gas. Below MIN_GAS_TAO both
 * dialogs open on the gas top-up, which suggests SUGGESTED_GAS_TAO.
 *
 * Plain JVM: the v1 dialog's view model needs the device, so its wiring is read
 * from the sources; the v2 dialog is snLedgerClaimDialog's.
 */
class ClaimGasTest {

    private val gasKey = SnGasKeyState(SampleProtocolSource.SAMPLE_GAS_ADDRESS, SampleProtocolSource.SAMPLE_GAS_MIRROR)

    // the SDK's least v1 claim gas limit (sdk/sn_claims.go snClaimGasFloor) at its legacy
    // price, 1.1 × a 5 gwei gas price, in TAO: 0.01485
    private val v1FloorTao = 2_700_000 * 1.1 * 5e9 / 1e18

    private fun source(name: String): String =
        File("src/main/java/com/bringyour/network/ui/wallet/$name").readText()

    @Test
    fun `the threshold and the top-up cover the SDK's v1 gas floor`() {
        // VERSION2-LAUNCH.md §2.6: 0.02 TAO holds a claim up to about 6.7 gwei
        assertEquals(0.015, EarningsViewModel.MIN_GAS_TAO, 0.0)
        assertEquals(0.02, EarningsViewModel.SUGGESTED_GAS_TAO, 0.0)
        assertTrue(v1FloorTao <= EarningsViewModel.MIN_GAS_TAO)
        assertTrue(EarningsViewModel.MIN_GAS_TAO <= EarningsViewModel.SUGGESTED_GAS_TAO)
    }

    @Test
    fun `the v1 dialog opens on the top-up below the threshold`() {
        // VERSION2-LAUNCH.md §2.6: no Claim the SDK refuses as needs_gas, and the top-up names
        // the suggested amount
        assertTrue(
            source("EarningsViewModel.kt").contains(
                "_claimDialog.value = if (claimable.isNotEmpty() && gasTao != null && gasTao < MIN_GAS_TAO) {\n" +
                    "            ClaimDialogState.NeedsGas(_gasKey.value, gasTao, total)\n"
            )
        )
        assertTrue(source("ClaimDialog.kt").contains("EarningsFormat.tao(EarningsViewModel.SUGGESTED_GAS_TAO),"))
    }

    @Test
    fun `the v2 dialog opens on the top-up below the same threshold`() {
        // VERSION2-LAUNCH.md §2.6: above a v2 claim's 0.0143 TAO at 5 gwei
        val pool = SnLedgerAccountState(1, 42, 5_120_000_000, 4_120_000_000, 1_000_000_000, false, 0)
        val load = SnLedgerLoad.Loaded(listOf(pool))
        assertEquals(
            SnLedgerClaimDialogState.NeedsGas(gasKey, 0.0149, pool.claimableRao),
            snLedgerClaimDialog(load, gasKey, 0.0149)
        )
        assertEquals(
            SnLedgerClaimDialogState.Ready(listOf(pool), pool.claimableRao, gasKey, 0.015),
            snLedgerClaimDialog(load, gasKey, 0.015)
        )
    }
}
