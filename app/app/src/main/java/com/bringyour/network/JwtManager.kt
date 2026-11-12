package com.bringyour.network

import com.bringyour.sdk.ByJwt
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SubscriptionProTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The calls JwtManager makes on the sdk's Pro and jwt refresh decision,
 * SubscriptionProTracker. The sdk's class needs the native library, which the
 * jvm unit tests do not have, so they hand JwtManager their own.
 */
interface ProTracker {
    fun setJwt(byJwt: String): Boolean
    fun balanceLoaded(serverIsPro: Boolean): Boolean
    val isPro: Boolean
    fun reset()
}

private class SdkProTracker(private val tracker: SubscriptionProTracker) : ProTracker {
    override fun setJwt(byJwt: String): Boolean = tracker.setJwt(byJwt)
    override fun balanceLoaded(serverIsPro: Boolean): Boolean = tracker.balanceLoaded(serverIsPro)
    override val isPro: Boolean get() = tracker.isPro
    override fun reset() = tracker.reset()
}

@Singleton
class JwtManager internal constructor(newProTracker: () -> ProTracker) {

    @Inject constructor() : this({ SdkProTracker(Sdk.newSubscriptionProTracker()) })

    private val _jwtFlow = MutableStateFlow<ByJwt?>(null)
    val jwtFlow: StateFlow<ByJwt?> = _jwtFlow.asStateFlow()

    /**
     * The sdk's Pro and jwt refresh decision (SubscriptionProTracker), fed the
     * stored jwt here and every balance by SubscriptionBalanceViewModel: Pro is
     * the stored jwt's `pro` claim until a balance loads and the server's
     * answer after, and a claim that disagrees asks for one refresh per stored
     * jwt. The refresh renews the client token and leaves the stored jwt as it
     * was, so asking on every balance read refreshed on every poll (support
     * inbox 8211). Made on first use: the jvm unit tests build this manager
     * without the sdk's native library.
     */
    private val proTrackerLazy = lazy(newProTracker)
    val proTracker: ProTracker by proTrackerLazy

    // each change of the decision and the Pro it publishes, in one order
    private val proLock = Any()
    private val _isPro = MutableStateFlow(false)

    /**
     * The Pro every screen shows (MainActivity hands it to MainNavHost): the
     * decision's, republished on each of its inputs (updateJwt, balanceLoaded,
     * clearJwt). The screens read the stored jwt's `pro` claim before, which is
     * minted at sign in and stays Free after a purchase until the network
     * token renews on its own schedule: a trial credited at once still read
     * "Free" above its Pro balance a day later (support inbox 8259).
     */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /** `storedByJwt` is the stored jwt `newJwt` was parsed from. */
    fun updateJwt(newJwt: ByJwt, storedByJwt: String?) {
        trackStoredJwt(storedByJwt)
        _jwtFlow.value = newJwt
    }

    /** Hands the decision the stored jwt; none (a read that found none) changes nothing. */
    internal fun trackStoredJwt(storedByJwt: String?) {
        if (storedByJwt.isNullOrEmpty()) {
            return
        }
        synchronized(proLock) {
            proTracker.setJwt(storedByJwt)
            _isPro.value = proTracker.isPro
        }
    }

    /**
     * A balance loaded; `serverIsPro` is whether it carries a current
     * subscription. Pro is the server's from here on. Returns whether to
     * refresh the jwt now (one refresh per stored jwt).
     */
    fun balanceLoaded(serverIsPro: Boolean): Boolean = synchronized(proLock) {
        val refreshJwt = proTracker.balanceLoaded(serverIsPro)
        _isPro.value = proTracker.isPro
        refreshJwt
    }

    fun clearJwt() {
        synchronized(proLock) {
            if (proTrackerLazy.isInitialized()) {
                proTracker.reset()
            }
            _isPro.value = false
        }
        _jwtFlow.value = null
    }

}
