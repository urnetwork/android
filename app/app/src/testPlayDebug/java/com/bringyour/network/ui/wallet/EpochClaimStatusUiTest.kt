package com.bringyour.network.ui.wallet

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.bringyour.network.ui.theme.URNetworkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The words Earnings shows for an epoch's SN25α claim (support inbox 8126): the
 * history row under each epoch's alpha, and the count under the claim dialog's
 * total. The SDK reports an epoch the vault finalized without a payout root as
 * not-finalized; its row reads No payout published instead of nothing. The
 * dialog's count is its claimable epochs, and says so.
 *
 * Drawn on the JVM by Robolectric in English; no sdk is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class EpochClaimStatusUiTest {

    @get:Rule
    val compose = createComposeRule()

    // synthetic: epoch 2, Oct 16 - 23 2026, no points
    private val epoch = AccountEpoch(2, 1_792_108_800_000, 1_792_713_600_000, 0.0, 0)

    private val statusWords = listOf("Unclaimed", "Claimed", "Expired", "No payout published")

    private fun showRow(status: EpochClaimStatus, amountRao: Long) {
        compose.setContent {
            URNetworkTheme {
                EpochRow(
                    epoch = epoch,
                    claim = EpochClaim(2, 71, amountRao, status, 0, 0, null),
                    showAlpha = true,
                    formatAlpha = { EarningsFormat.alpha(it) },
                    formatShareBps = { EarningsFormat.shareBps(it) },
                )
            }
        }
    }

    private fun assertOnlyWord(word: String?) {
        for (other in statusWords) {
            if (other == word) {
                compose.onNodeWithText(other).assertExists()
            } else {
                compose.onNodeWithText(other).assertDoesNotExist()
            }
        }
    }

    @Test
    fun `a claimable epoch reads Unclaimed`() {
        showRow(EpochClaimStatus.CLAIMABLE, 3_241_000_000)
        assertOnlyWord("Unclaimed")
    }

    @Test
    fun `a claimed epoch reads Claimed`() {
        showRow(EpochClaimStatus.CLAIMED, 2_905_500_000)
        assertOnlyWord("Claimed")
    }

    @Test
    fun `an expired epoch reads Expired`() {
        showRow(EpochClaimStatus.EXPIRED, 1_812_250_000)
        assertOnlyWord("Expired")
    }

    @Test
    fun `an epoch the vault has not finalized shows no status word`() {
        showRow(EpochClaimStatus.OPEN, 0)
        assertOnlyWord(null)
    }

    @Test
    fun `an epoch finalized without a payout reads No payout published`() {
        showRow(EpochClaimStatus.NOT_FINALIZED, 0)
        assertOnlyWord("No payout published")
    }

    @Test
    fun `the claim dialog counts its claimable epochs and says so`() {
        val claims = listOf(
            EpochClaim(5, 71, 3_241_000_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
            EpochClaim(4, 64, 2_905_500_000, EpochClaimStatus.CLAIMABLE, 0, 0, null),
        )
        compose.setContent {
            URNetworkTheme {
                ClaimDialog(
                    state = ClaimDialogState.Ready(claims, 6_146_500_000, null, 0.5),
                    onDismiss = {},
                    onClaim = {},
                    onRetry = {},
                    formatAlpha = { EarningsFormat.alpha(it) },
                    shortSs58 = { it },
                    explorerTxUrl = { it },
                )
            }
        }
        compose.onNodeWithText("Claimable epochs: 2").assertExists()
    }
}
