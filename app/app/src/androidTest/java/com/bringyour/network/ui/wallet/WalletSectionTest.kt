package com.bringyour.network.ui.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import com.bringyour.network.utils.Ss58
import com.bringyour.network.utils.formatDecimalString
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where the wallet options menu sits in the Bittensor wallet section: beside "Connect
 * Bittensor wallet", in the connected wallet's header, and nowhere while a new-looking
 * address waits for confirmation.
 */
@RunWith(AndroidJUnit4::class)
class WalletSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(
        wallet: SnWalletState? = null,
        connectState: WalletConnectState = WalletConnectState.Idle,
        protocolAvailable: Boolean = true,
        usdcWaiting: Double? = null,
        onConnectSolana: () -> Unit = {},
    ) {
        compose.setContent {
            URNetworkTheme {
                WalletSection(
                    protocolAvailable = protocolAvailable,
                    wallet = wallet,
                    walletLoaded = true,
                    connectState = connectState,
                    onConnectWallet = {},
                    onEnterManually = {},
                    onContinueLooksNew = {},
                    onDismissConnectState = {},
                    onConnectSolana = onConnectSolana,
                    usdcWaiting = usdcWaiting,
                    shortSs58 = { Ss58.short(it) },
                )
            }
        }
    }

    @Test
    fun theOverflowSitsBesideConnectBittensorWalletEvenWithoutTheProtocol() {
        var connects = 0
        show(protocolAvailable = false, usdcWaiting = 3.87, onConnectSolana = { connects += 1 })

        compose.onNodeWithTag(EARNINGS_WALLET_OVERFLOW_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
        compose.onNodeWithTag(EARNINGS_USDC_WAITING_TAG)
            .assertTextEquals(context.getString(R.string.usdc_waiting, formatDecimalString(3.87, 2)))

        compose.onNodeWithTag(EARNINGS_WALLET_OVERFLOW_TAG).performClick()
        compose.onNodeWithTag(EARNINGS_CONNECT_SOLANA_ITEM_TAG).performClick()

        compose.runOnIdle { assertEquals(1, connects) }
    }

    @Test
    fun theOverflowSitsInTheConnectedWalletHeader() {
        show(wallet = SnWalletState(SampleProtocolSource.SAMPLE_COLDKEY, null, 0))

        compose.onNodeWithTag(EARNINGS_WALLET_OVERFLOW_TAG).assertIsDisplayed()
    }

    @Test
    fun aNewLookingAddressHidesTheOverflow() {
        show(
            connectState = WalletConnectState.LooksNew(
                SampleProtocolSource.SAMPLE_NEW_COLDKEY,
                "signature",
                "message",
                null
            )
        )

        compose.onNodeWithTag(EARNINGS_WALLET_OVERFLOW_TAG).assertDoesNotExist()
    }
}
