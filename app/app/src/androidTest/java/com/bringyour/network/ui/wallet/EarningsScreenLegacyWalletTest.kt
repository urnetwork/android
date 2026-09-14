package com.bringyour.network.ui.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.formatDecimalString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Solana payout wallet block of the Earnings screen. `EarningsScreenContent` itself
 * needs Hilt (it obtains the throughput view model), so the block it renders under the
 * Bittensor wallet section is exercised directly.
 */
@RunWith(AndroidJUnit4::class)
class EarningsScreenLegacyWalletTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val wallet = LegacyWallet(
        SampleLegacyWalletSource.SAMPLE_WALLET_ID,
        SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS,
        LegacyChain.SOLANA,
        hasSeekerToken = false
    )

    private val held = LegacyPayment("held", null, SampleLegacyWalletSource.SAMPLE_USDC_WAITING, 0.0, false, false, null)

    private fun show(legacy: LegacyWalletUi, showWaitingLine: Boolean = false, onRemove: () -> Unit = {}) {
        compose.setContent {
            URNetworkTheme {
                Column {
                    LegacyPayoutBlock(
                        legacy = legacy,
                        legacyLoaded = true,
                        showWaitingLine = showWaitingLine,
                        state = SolanaConnectState.Idle,
                        onRemove = onRemove,
                        onDismissState = {},
                    )
                }
            }
        }
    }

    @Test
    fun thePayoutWalletShowsItsCard() {
        show(LegacyWalletUi(listOf(wallet), wallet.walletId, listOf(held)))

        compose.onNodeWithText(context.getString(R.string.solana_wallet)).assertIsDisplayed()
        compose.onNodeWithText("4Fj9…SYCM").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.default_wallet).uppercase()).assertIsDisplayed()
        compose.onNodeWithTag(EARNINGS_USDC_WAITING_TAG)
            .assertTextEquals(
                context.getString(R.string.usdc_waiting, formatDecimalString(SampleLegacyWalletSource.SAMPLE_USDC_WAITING, 2))
            )
    }

    @Test
    fun aSolanaRowThatIsNotThePayoutWalletShowsNoCard() {
        show(LegacyWalletUi(listOf(wallet), payoutWalletId = null, payments = listOf(held)), showWaitingLine = true)

        compose.onNodeWithTag(EARNINGS_SOLANA_WALLET_CARD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(EARNINGS_USDC_WAITING_TAG).assertIsDisplayed()
    }

    @Test
    fun removeIsBehindTheCardsWalletOptions() {
        var removes = 0
        show(LegacyWalletUi(listOf(wallet), wallet.walletId), onRemove = { removes += 1 })

        compose.onNodeWithTag(EARNINGS_SOLANA_WALLET_OVERFLOW_TAG).performClick()
        compose.onNodeWithTag(EARNINGS_REMOVE_SOLANA_ITEM_TAG)
            .assertTextEquals(context.getString(R.string.remove))
            .performClick()

        compose.runOnIdle { assertEquals(1, removes) }
    }
}
