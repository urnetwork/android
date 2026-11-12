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
 * The calls JwtManager makes on the sdk's plan and jwt refresh decision,
 * SubscriptionProTracker. The sdk's class needs the native library, which the
 * jvm unit tests do not have, so they hand JwtManager their own.
 */
interface ProTracker {
    fun setJwt(byJwt: String): Boolean
    fun balanceLoaded(serverIsPro: Boolean): Boolean
    fun balanceFailed(): Boolean
    val isPro: Boolean
    /** One of the sdk's ShownPlan* values ("pro", "free", "unknown"). */
    val shownPlan: String
    val lastReadFailed: Boolean
    fun reset()
}

private class SdkProTracker(private val tracker: SubscriptionProTracker) : ProTracker {
    override fun setJwt(byJwt: String): Boolean = tracker.setJwt(byJwt)
    override fun balanceLoaded(serverIsPro: Boolean): Boolean = tracker.balanceLoaded(serverIsPro)
    override fun balanceFailed(): Boolean = tracker.balanceFailed()
    override val isPro: Boolean get() = tracker.isPro
    override val shownPlan: String get() = tracker.shownPlan
    override val lastReadFailed: Boolean get() = tracker.lastReadFailed
    override fun reset() = tracker.reset()
}

@Singleton
class JwtManager internal constructor(newProTracker: () -> ProTracker) {

    @Inject constructor() : this({ SdkProTracker(Sdk.newSubscriptionProTracker()) })

    private val _jwtFlow = MutableStateFlow<ByJwt?>(null)
    val jwtFlow: StateFlow<ByJwt?> = _jwtFlow.asStateFlow()

    /**
     * The sdk's plan and jwt refresh decision (SubscriptionProTracker), fed the
     * stored jwt here and every balance read by SubscriptionBalanceViewModel:
     * the plan is the server's answer once a balance loads; before that Pro by
     * the stored jwt's `pro` claim, else unknown (never Free). A claim that
     * disagrees asks for one refresh per stored jwt. The refresh renews the
     * client token and leaves the stored jwt as it was, so asking on every
     * balance read refreshed on every poll (support inbox 8211). Made on first
     * use: the jvm unit tests build this manager without the sdk's native
     * library.
     */
    private val proTrackerLazy = lazy(newProTracker)
    val proTracker: ProTracker by proTrackerLazy

    // each change of the decision and the plan it publishes, in one order
    private val proLock = Any()
    private val _isPro = MutableStateFlow(false)
    private val _plan = MutableStateFlow(ShownPlan.Loading)

    /**
     * The Pro every screen shows (MainActivity hands it to MainNavHost): the
     * decision's, republished on each of its inputs (updateJwt, balanceLoaded,
     * balanceFailed, clearJwt). The screens read the stored jwt's `pro` claim
     * before, which is minted at sign in and stays Free after a purchase until
     * the network token renews on its own schedule: a trial credited at once
     * still read "Free" above its Pro balance a day later (support inbox 8259).
     */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    /**
     * The plan the plan cards show (ShownPlan): Pro exactly when [isPro]; Free
     * only once a balance has loaded and the server said so; Loading or
     * Unreachable until then (support inbox 4074).
     */
    val plan: StateFlow<ShownPlan> = _plan.asStateFlow()

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
            publishLocked()
        }
    }

    /**
     * A balance loaded; `serverIsPro` is whether it carries a current
     * subscription. The plan is the server's from here on. Returns whether to
     * refresh the jwt now (one refresh per stored jwt).
     */
    fun balanceLoaded(serverIsPro: Boolean): Boolean = synchronized(proLock) {
        val refreshJwt = proTracker.balanceLoaded(serverIsPro)
        publishLocked()
        refreshJwt
    }

    /**
     * A balance read failed (an error, or no balance). The plan stays the
     * server's last answer, or before one Pro by the claim or unknown, now
     * Unreachable: never Free. Asks for no jwt refresh.
     */
    fun balanceFailed() {
        synchronized(proLock) {
            proTracker.balanceFailed()
            publishLocked()
        }
    }

    fun clearJwt() {
        synchronized(proLock) {
            if (proTrackerLazy.isInitialized()) {
                proTracker.reset()
            }
            _isPro.value = false
            _plan.value = ShownPlan.Loading
        }
        _jwtFlow.value = null
    }

    // caller holds proLock
    private fun publishLocked() {
        _isPro.value = proTracker.isPro
        _plan.value = ShownPlan.of(proTracker.shownPlan, proTracker.lastReadFailed)
    }

}
