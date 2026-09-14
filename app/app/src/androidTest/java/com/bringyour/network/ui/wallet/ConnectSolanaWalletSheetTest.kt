package com.bringyour.network.ui.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bringyour.network.R
import com.bringyour.network.ui.theme.URNetworkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The two steps of the Solana connect sheet (the stateless content the sheet hosts). */
@RunWith(AndroidJUnit4::class)
class ConnectSolanaWalletSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(
        step: SolanaSheetStep,
        state: SolanaConnectState = SolanaConnectState.Idle,
        validation: AddressValidation = AddressValidation.Empty,
        onContinue: () -> Unit = {},
    ) {
        compose.setContent {
            URNetworkTheme {
                ConnectSolanaWalletSheetContent(
                    step = step,
                    state = state,
                    address = TextFieldValue(SampleLegacyWalletSource.SAMPLE_SOLANA_ADDRESS),
                    onAddressChange = {},
                    validation = validation,
                    onConnectWalletApp = {},
                    onEnterManually = {},
                    onBack = {},
                    onContinue = onContinue,
                    onDismissState = {},
                )
            }
        }
    }

    @Test
    fun anInvalidAddressSaysSoAndCannotConnect() {
        show(SolanaSheetStep.MANUAL, validation = AddressValidation.InvalidSyntax)

        compose.onNodeWithText(context.getString(R.string.invalid_solana_address)).assertIsDisplayed()
        compose.onNodeWithTag(SOLANA_MANUAL_CONNECT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aValidatedAddressConnects() {
        var continues = 0
        show(SolanaSheetStep.MANUAL, validation = AddressValidation.Ok, onContinue = { continues += 1 })

        compose.onNodeWithTag(SOLANA_MANUAL_CONNECT_TAG)
            .assertIsEnabled()
            .performClick()

        compose.runOnIdle { assertEquals(1, continues) }
    }

    @Test
    fun aFailedWalletAppConnectShowsTheReason() {
        show(SolanaSheetStep.CHOOSE, state = SolanaConnectState.Failed("boom"))

        compose.onNodeWithTag(SOLANA_CONNECT_ERROR_TAG)
            .assertTextEquals(context.getString(R.string.error_connecting_wallet_with_reason, "boom"))
    }
}
