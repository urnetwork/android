package com.bringyour.network.ui.wallet

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.bringyour.network.DeviceManager
import com.bringyour.network.JwtManager
import com.bringyour.network.NetworkSpaceManagerProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Support inbox 8045: a Solana key was saved as a Polygon wallet. The server now checks a
 * wallet address against its chain when a wallet is added or checked; for chain SOL it
 * takes a base58 public key of 32 bytes that is neither the all-zero key nor the USDC
 * mint. The payout address field applies the same rule before any request, so an address
 * the server refuses shows as invalid at once and is never sent.
 *
 * The sdk source needs the native library, which these JVM tests do not have, so it is
 * checked on its source. The view model runs without a device, and its main dispatcher
 * runs on the test's scheduler, so no time passes unless the test advances it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SolanaPayoutAddressTest {

    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"

    // 32 zero bytes
    private val allZeroKey = "1".repeat(32)

    // 44 characters, inside the length range, but 33 bytes
    private val longValue = "z".repeat(44)

    // a fixture key (sha256 of "urnetwork test solana wallet"), not a wallet
    private val key = "GES2rXHpUVwXKtXRDVXUH7uU2BLX3VPUPexTq4uTXuvt"

    private val sources = listOf(
        NoLegacyWalletSource,
        SampleLegacyWalletSource(startConnected = false, pendingUsd = 0.0, delayMillis = 0),
    )

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun assertRefusedBySources(address: String) {
        for (source in sources) {
            assertFalse("${source::class.simpleName}: \"$address\"", source.validateSolanaSyntax(address))
        }
    }

    @Test
    fun `the usdc mint is not a payout address`() {
        assertRefusedBySources(usdcMint)
        assertRefusedBySources(" $usdcMint\n")
    }

    @Test
    fun `the all-zero key is not a payout address`() {
        assertRefusedBySources(allZeroKey)
    }

    @Test
    fun `a value that is not 32 bytes is not a payout address`() {
        assertRefusedBySources(longValue)
    }

    @Test
    fun `a public key is a payout address`() {
        for (source in sources) {
            assertTrue("${source::class.simpleName}", source.validateSolanaSyntax(key))
            assertTrue("${source::class.simpleName}", source.validateSolanaSyntax(" $key\n"))
        }
    }

    @Test
    fun `the sdk source checks the same rule`() {
        val sdk = File("src/main/java/com/bringyour/network/ui/wallet/SdkLegacyWalletSource.kt").readText()
        assertTrue(
            sdk.contains(
                "override fun validateSolanaSyntax(address: String): Boolean = SolanaAddress.isValidWalletAddress(address)"
            )
        )
    }

    // a view model without a device, cleared after the test so nothing it started outlives it
    private fun withViewModel(test: (SolanaWalletViewModel) -> Unit) {
        val viewModel = SolanaWalletViewModel(DeviceManager(JwtManager(), NetworkSpaceManagerProvider()))
        try {
            test(viewModel)
        } finally {
            val store = ViewModelStore()
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
            ViewModelProvider(store, factory)[SolanaWalletViewModel::class.java]
            store.clear()
        }
    }

    @Test
    fun `the address field shows a refused address as invalid and sends nothing`() {
        withViewModel { viewModel ->
            for (address in listOf(usdcMint, allZeroKey, longValue)) {
                viewModel.updateManualAddress(TextFieldValue(address))
                // Checking is where the debounced server check starts
                assertEquals(address, AddressValidation.InvalidSyntax, viewModel.manualValidation.value)
                // continue creates the wallet only after the server check's Ok
                viewModel.continueManual()
                assertEquals(address, SolanaConnectState.Idle, viewModel.connectState.value)
                // a server check waits out the debounce first, so without one the clock stays at zero
                scheduler.advanceUntilIdle()
                assertEquals(address, 0L, scheduler.currentTime)
                assertEquals(address, AddressValidation.InvalidSyntax, viewModel.manualValidation.value)
            }
        }
    }

    @Test
    fun `the address field sends a public key on to the server check`() {
        withViewModel { viewModel ->
            viewModel.updateManualAddress(TextFieldValue(key))
            assertEquals(AddressValidation.Checking, viewModel.manualValidation.value)
        }
    }
}
