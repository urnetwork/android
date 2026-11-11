package com.bringyour.network.ui.upgrade

import com.bringyour.network.analytics.ClientEvents
import com.bringyour.network.ui.shared.enums.PlanType
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The payment method row's rules (mmm/ZCASH-CONTRACT.md §6.1, the coin tabs of
 * mmm/BTCPAY.md §17.6) on the flavors that sell outside a store: whether the
 * row shows, which method is chosen and what a tap on a tab chooses, what a
 * coin buys and what it greys out, where a refused start goes, and what the
 * events name. Plain values: the tabs are the sdk's CryptoPaymentTabs layout
 * as fixtures, and the sdk's PaymentMethodAllowsPlan is a twin here.
 */
class CryptoPaymentTest {

    private val card = Sdk.PaymentMethodCard
    private val usdc = Sdk.CryptoAssetUsdcSolana
    private val zec = Sdk.CryptoAssetZec
    private val xmr = Sdk.CryptoAssetXmr
    private val tao = Sdk.CryptoAssetTao
    private val btc = Sdk.CryptoAssetBtc
    private val eth = Sdk.CryptoAssetEth
    private val sol = Sdk.CryptoAssetSol

    // the sdk's PaymentMethodAllowsPlan: false only for a coin with monthly
    private val allowsPlan: (String, String) -> Boolean = { method, plan ->
        !(method != Sdk.PaymentMethodCard && plan == Sdk.PlanMonthly)
    }

    /**
     * The sdk's tabs for a balance that lists [listed] (no unavailable list):
     * none without a coin; else USDC on Solana, Zcash and Monero, each greyed
     * when not listed, and More coins while one of its coins is listed.
     */
    private fun tabs(vararg listed: String): List<CryptoPayTab> {
        if (listed.isEmpty()) {
            return emptyList()
        }
        val tab = { id: String, assets: List<String> ->
            val coins = assets.map { asset ->
                CryptoPayCoin(asset = asset, label = asset, labelKey = "", iconKey = "", available = asset in listed)
            }
            CryptoPayTab(id = id, label = id, labelKey = "", iconKeys = emptyList(), coins = coins, available = coins.any { it.available })
        }
        val more = listOf(tao, btc, eth, sol)
        return listOfNotNull(
            tab(usdc, listOf(usdc)),
            tab(zec, listOf(zec)),
            tab(xmr, listOf(xmr)),
            if (more.any { it in listed }) tab(Sdk.CryptoPaymentTabMoreCoins, more) else null,
        )
    }

    @Test
    fun withNoCoinListedThereIsNoRow() {
        assertFalse(CryptoPayment.showsRow(emptyList()))
        assertFalse(CryptoPayment.showsRow(tabs()))
        assertTrue(CryptoPayment.showsRow(tabs(xmr)))
        assertTrue(CryptoPayment.showsRow(tabs(tao)))
        assertEquals("card", card)
        assertEquals("more_coins", Sdk.CryptoPaymentTabMoreCoins)
    }

    @Test
    fun theRememberedMethodIsChosenWhileItCanBePaidWith() {
        val all = tabs(usdc, zec, xmr, tao, btc, eth, sol)
        for (coin in listOf(usdc, zec, xmr, tao, btc, eth, sol)) {
            assertEquals(coin, CryptoPayment.chosenMethod(coin, all))
        }
        assertEquals(card, CryptoPayment.chosenMethod(card, all))
    }

    @Test
    fun aRememberedCoinThatCannotBePaidWithFallsBackToTheCard() {
        // no longer listed: its tab stays, greyed, and the card is chosen
        assertEquals(card, CryptoPayment.chosenMethod(zec, tabs(usdc, xmr)))
        assertEquals(card, CryptoPayment.chosenMethod(btc, tabs(tao)))
        // no coin listed at all
        assertEquals(card, CryptoPayment.chosenMethod(xmr, tabs()))
        // nothing remembered yet (the sdk answers "")
        assertEquals(card, CryptoPayment.chosenMethod("", tabs(xmr)))
        assertEquals(card, CryptoPayment.chosenMethod(null, tabs(xmr)))
        // a value the sdk would never keep
        assertEquals(card, CryptoPayment.chosenMethod("paypal", tabs(xmr)))
    }

    @Test
    fun aTapOnATabChoosesItsCoin() {
        val all = tabs(usdc, zec, xmr, btc, sol)
        val more = all.single { it.id == Sdk.CryptoPaymentTabMoreCoins }
        // More coins chooses its first coin that can be paid with: TAO is greyed here
        assertEquals(btc, CryptoPayment.tabChoice(more, card))
        assertEquals(btc, CryptoPayment.tabChoice(more, xmr))
        // the chosen coin stays chosen while it sits in the tab
        assertEquals(sol, CryptoPayment.tabChoice(more, sol))
        // a greyed coin is never the choice
        assertEquals(btc, CryptoPayment.tabChoice(more, tao))
        assertEquals(tao, CryptoPayment.tabChoice(tabs(tao, eth).single { it.id == Sdk.CryptoPaymentTabMoreCoins }, card))
        // a tab of one coin chooses that coin
        assertEquals(zec, CryptoPayment.tabChoice(all.single { it.id == zec }, card))
        // a tab none of whose coins can be paid with chooses nothing
        assertNull(CryptoPayment.tabChoice(tabs(btc).single { it.id == xmr }, card))
    }

    @Test
    fun aCoinBuysTheWelcomeOfferWhileItIsActiveElseTheYearlyPlan() {
        assertEquals(Sdk.PlanYearlyOnboarding, CryptoPayment.coinPlan(offerActive = true))
        assertEquals(Sdk.PlanYearly, CryptoPayment.coinPlan(offerActive = false))
        assertEquals("yearly_onboarding", CryptoPayment.coinPlan(offerActive = true))
        assertEquals("yearly", CryptoPayment.coinPlan(offerActive = false))
    }

    @Test
    fun pickingACoinMovesMonthlyToYearly() {
        for (coin in listOf(usdc, zec, xmr, tao, btc, eth, sol)) {
            assertEquals(PlanType.YEARLY, CryptoPayment.planFor(coin, PlanType.MONTHLY, allowsPlan))
            assertEquals(PlanType.YEARLY, CryptoPayment.planFor(coin, PlanType.YEARLY, allowsPlan))
        }
    }

    @Test
    fun theCardKeepsMonthly() {
        assertEquals(PlanType.MONTHLY, CryptoPayment.planFor(card, PlanType.MONTHLY, allowsPlan))
        assertEquals(PlanType.YEARLY, CryptoPayment.planFor(card, PlanType.YEARLY, allowsPlan))
    }

    @Test
    fun anUnavailableCoinIsSaidUnderTheRow() {
        assertEquals(CryptoStartRefusal.Unavailable, CryptoPayment.startRefusal("asset_unavailable", "Monero can't be quoted right now."))
        assertEquals("asset_unavailable", Sdk.PurchaseErrorCodeAssetUnavailable)
    }

    @Test
    fun aGuestNetworkAddsASignInMethodFirst() {
        assertEquals(CryptoStartRefusal.AddSignInMethod, CryptoPayment.startRefusal("guest_sign_in_required", "Add a sign-in method first."))
    }

    @Test
    fun everyOtherRefusalIsTheSurfacesErrorLine() {
        for (code in listOf("rate_limited", "offer_unavailable", "start_failed", "plan_unavailable", "invalid_request", "item_unavailable")) {
            assertEquals(CryptoStartRefusal.Refused(code, "words"), CryptoPayment.startRefusal(code, "words"))
        }
        // a transport failure has no code, only the transport's words
        assertEquals(CryptoStartRefusal.Refused("", "i/o timeout"), CryptoPayment.startRefusal("", "i/o timeout"))
        // codes compare exactly
        assertEquals(CryptoStartRefusal.Refused("ASSET_UNAVAILABLE", ""), CryptoPayment.startRefusal("ASSET_UNAVAILABLE", ""))
    }

    // the store is the coin's market, the sdk's CryptoAssetMarket (CryptoPayViewModel.eventStore)
    @Test
    fun theEventsNameACoinsYearOfProByItsTicker() {
        assertEquals(ClientEvents.PRODUCT_SOLANA_PRO_YEARLY, CryptoPayment.eventProduct("USDC"))
        assertEquals("pro_yearly_usdc", CryptoPayment.eventProduct("USDC"))
        assertEquals("pro_yearly_zec", CryptoPayment.eventProduct("ZEC"))
        assertEquals("pro_yearly_xmr", CryptoPayment.eventProduct("XMR"))
        assertEquals("pro_yearly_tao", CryptoPayment.eventProduct("TAO"))
        assertEquals("pro_yearly_btc", CryptoPayment.eventProduct("BTC"))
        assertEquals("pro_yearly_eth", CryptoPayment.eventProduct("ETH"))
        assertEquals("pro_yearly_sol", CryptoPayment.eventProduct("SOL"))
        // not a coin: the sdk has no ticker for it
        assertNull(CryptoPayment.eventProduct(""))
        assertEquals("bittensor", Sdk.EventStoreBittensor)
        assertEquals("bitcoin", Sdk.EventStoreBitcoin)
        assertEquals("ethereum", Sdk.EventStoreEthereum)
        assertEquals("solana", Sdk.EventStoreSolana)
    }

    // the server's event schema knows yearly and monthly only (EventPlans)
    @Test
    fun theEventsNameTheWelcomeOfferAsTheYearlyPlan() {
        assertEquals("yearly", CryptoPayment.eventPlan(Sdk.PlanYearlyOnboarding))
        assertEquals("yearly", CryptoPayment.eventPlan(Sdk.PlanYearly))
        assertEquals("monthly", CryptoPayment.eventPlan(Sdk.PlanMonthly))
    }

    @Test
    fun theCountdownIsMinutesAndSeconds() {
        assertEquals("30:00", CryptoPayment.countdown(1800))
        assertEquals("29:59", CryptoPayment.countdown(1799))
        assertEquals("1:00", CryptoPayment.countdown(60))
        assertEquals("0:59", CryptoPayment.countdown(59))
        assertEquals("0:05", CryptoPayment.countdown(5))
        assertEquals("0:00", CryptoPayment.countdown(0))
        assertEquals("0:00", CryptoPayment.countdown(-3))
        // a USDC quote holds an hour
        assertEquals("60:00", CryptoPayment.countdown(3600))
    }

    @Test
    fun anExactAmountIsMoreThanZeroOrNot() {
        assertTrue(CryptoPayment.isPositive("0.000001"))
        assertTrue(CryptoPayment.isPositive("0.012345"))
        assertTrue(CryptoPayment.isPositive(" 1 "))
        assertFalse(CryptoPayment.isPositive("0"))
        assertFalse(CryptoPayment.isPositive("0.000000"))
        assertFalse(CryptoPayment.isPositive(""))
        assertFalse(CryptoPayment.isPositive("-0.1"))
        assertFalse(CryptoPayment.isPositive("not an amount"))
    }

    @Test
    fun theSdksTimesAreRfc3339() {
        assertEquals(1_792_521_000_000L, CryptoPayment.timeMillis("2026-10-20T18:30:00Z"))
        assertEquals(1_792_521_000_000L, CryptoPayment.timeMillis("2026-10-20T20:30:00+02:00"))
        assertEquals(1_792_521_000_123L, CryptoPayment.timeMillis("2026-10-20T18:30:00.123456789Z"))
        assertNull(CryptoPayment.timeMillis(""))
        assertNull(CryptoPayment.timeMillis(null))
        assertNull(CryptoPayment.timeMillis("yesterday"))
    }
}
