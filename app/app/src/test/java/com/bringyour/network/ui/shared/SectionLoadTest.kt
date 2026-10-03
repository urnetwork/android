package com.bringyour.network.ui.shared

import com.bringyour.network.ui.shared.models.SectionLoad
import com.bringyour.network.ui.shared.models.accountPointsLoadAfterFetch
import com.bringyour.network.ui.shared.models.referralCodeLoadAfterFetch
import com.bringyour.network.ui.shared.models.referralStatLoads
import com.bringyour.network.ui.wallet.EarningsProtocolSource
import com.bringyour.network.ui.wallet.NoProtocolSource
import com.bringyour.network.ui.wallet.SnWalletState
import com.bringyour.network.ui.wallet.loadWallet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reported defect: when their fetch failed, the account points, earnings
 * wallet and referral code sections showed an endless spinner or wrong data
 * (0 points, a "connect wallet" offer) with no error and no retry.
 */
class SectionLoadTest {

    private val wallet = SnWalletState(coldkeySs58 = "5SyntheticColdkeyForTests", clientId = null, setAtMillis = 1L)

    /** A protocol source whose wallet read answers with a fixed result. */
    private fun source(cached: SnWalletState?, fetched: Result<SnWalletState?>): EarningsProtocolSource =
        object : EarningsProtocolSource by NoProtocolSource {
            override val available: Boolean = true
            override fun currentWallet(): SnWalletState? = cached
            override suspend fun fetchWallet(): Result<SnWalletState?> = fetched
        }

    @Test
    fun aFailedFirstPointsFetchIsAnErrorNotZeroPoints() {
        assertEquals(SectionLoad.Failed, accountPointsLoadAfterFetch(failed = true, loadedBefore = false))
    }

    @Test
    fun aFailedPointsRefreshKeepsThePointsShown() {
        assertEquals(SectionLoad.Loaded, accountPointsLoadAfterFetch(failed = true, loadedBefore = true))
        assertEquals(SectionLoad.Loaded, accountPointsLoadAfterFetch(failed = false, loadedBefore = false))
    }

    @Test
    fun aFailedReferralCodeFetchEndsTheSpinner() {
        assertEquals(SectionLoad.Failed, referralCodeLoadAfterFetch(failed = true, code = null, shownCode = ""))
    }

    @Test
    fun aReferralReplyWithoutACodeEndsTheSpinner() {
        assertEquals(SectionLoad.Failed, referralCodeLoadAfterFetch(failed = false, code = "", shownCode = ""))
    }

    @Test
    fun aFailedReferralPollKeepsTheCodeShown() {
        assertEquals(SectionLoad.Loaded, referralCodeLoadAfterFetch(failed = true, code = null, shownCode = "SYNTH1"))
        assertEquals(SectionLoad.Loaded, referralCodeLoadAfterFetch(failed = false, code = "SYNTH1", shownCode = ""))
    }

    @Test
    fun tryAgainShowsTheSpinnerOnlyAfterAFailure() {
        assertEquals(SectionLoad.Loading, SectionLoad.retrying(SectionLoad.Failed))
        assertEquals(SectionLoad.Loaded, SectionLoad.retrying(SectionLoad.Loaded))
    }

    @Test
    fun aFailedWalletReadIsAnErrorNotTheConnectOffer() = runBlocking<Unit> {
        val load = loadWallet(source(cached = null, fetched = Result.failure(IllegalStateException("offline"))), shown = null)

        assertNull(load.wallet)
        assertEquals(SectionLoad.Failed, load.load)
    }

    @Test
    fun aFailedWalletReadKeepsAKnownWallet() = runBlocking<Unit> {
        val load = loadWallet(source(cached = wallet, fetched = Result.failure(IllegalStateException("offline"))), shown = null)

        assertEquals(wallet, load.wallet)
        assertEquals(SectionLoad.Loaded, load.load)
    }

    @Test
    fun noWalletOnTheServerIsTheConnectOffer() = runBlocking<Unit> {
        val load = loadWallet(source(cached = null, fetched = Result.success(null)), shown = null)

        assertNull(load.wallet)
        assertEquals(SectionLoad.Loaded, load.load)
    }

    /**
     * Reported defect: on Refer and earn, a failed points fetch showed
     * "Referral points 0" and a failed referral read showed "Total referrals
     * 0", with no error and no retry.
     */
    @Test
    fun aFailedPointsFetchIsNotZeroReferralPoints() {
        val loads = referralStatLoads(codeLoad = SectionLoad.Loaded, pointsLoad = SectionLoad.Failed)

        assertEquals(SectionLoad.Failed, loads.referralPoints)
        assertEquals(SectionLoad.Loaded, loads.totalReferrals)
    }

    @Test
    fun aFailedReferralReadIsNotZeroReferrals() {
        val loads = referralStatLoads(codeLoad = SectionLoad.Failed, pointsLoad = SectionLoad.Loaded)

        assertEquals(SectionLoad.Failed, loads.totalReferrals)
        assertEquals(SectionLoad.Loaded, loads.referralPoints)
    }

    @Test
    fun theReferralCountWaitsForItsRead() {
        val loads = referralStatLoads(codeLoad = SectionLoad.Loading, pointsLoad = SectionLoad.Loading)

        assertEquals(SectionLoad.Loading, loads.totalReferrals)
        assertEquals(SectionLoad.Loading, loads.referralPoints)
    }
}
