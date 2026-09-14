package com.bringyour.network.ui.wallet

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** "Connect Solana wallet" lives behind the three-dot wallet options on the Earnings screen. */
@RunWith(AndroidJUnit4::class)
class WalletOverflowMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun connectSolanaWalletOpensFromTheWalletOptions() {
        var connects = 0
        compose.setContent {
            URNetworkTheme {
                WalletOverflowMenu(
                    contentDescription = stringResource(id = R.string.wallet_options),
                    items = listOf(connectSolanaOverflowItem { connects += 1 })
                )
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.wallet_options))
            .performClick()
        compose.onNodeWithTag(EARNINGS_CONNECT_SOLANA_ITEM_TAG)
            .assertTextEquals(context.getString(R.string.connect_solana_wallet))
            .performClick()

        compose.runOnIdle { assertEquals(1, connects) }
        compose.onNodeWithTag(EARNINGS_CONNECT_SOLANA_ITEM_TAG).assertDoesNotExist()
    }
}
