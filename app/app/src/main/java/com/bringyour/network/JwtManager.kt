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
    /** Takes the stored jwt; returns whether the plan changed. */
    fun setJwt(byJwt: String): Boolean

    /** Takes a loaded balance's plan; returns whether to refresh the jwt now. */
    fun balanceLoaded(serverIsPro: Boolean): Boolean

    /** Takes a failed balance read; returns whether the read state changed. */
    fun balanceFailed(): Boolean

    /** The plan is Pro. */
    val isPro: Boolean

    /** One of the sdk's ShownPlan* values ("pro", "free", "unknown"). */
    val shownPlan: String

    /** The last balance read failed, and none has loaded since. */
    val lastReadFailed: Boolean

    /** Forgets the jwt, the server's answer and a failed read, for a sign out. */
    fun reset()
}

/** The sdk's own tracker, as the app runs it. */
private class SdkProTracker(private val tracker: SubscriptionProTracker) : ProTracker {
    override fun setJwt(byJwt: String): Boolean = tracker.setJwt(byJwt)
    override fun balanceLoaded(serverIsPro: Boolean): Boolean = tracker.balanceLoaded(serverIsPro)
    override fun balanceFailed(): Boolean = tracker.balanceFailed()
    override val isPro: Boolean get() = tracker.isPro
    override val shownPlan: String get() = tracker.shownPlan
    override val lastReadFailed: Boolean get() = tracker.lastReadFailed
    override fun reset() = tracker.reset()
}

/**
 * The signed-in jwt and the plan every screen shows, for the life of the
 * process. Every input of the sdk's decision goes through here (the stored
 * jwt, each balance read, a sign out), so the published plan follows it. Safe
 * to call from any thread: the jwt refresh listener runs on an sdk thread, the
 * balance reads on the main thread.
 */
@Singleton
class JwtManager internal constructor(newProTracker: () -> ProTracker) {

    @Inject constructor() : this({ SdkProTracker(tracker = Sdk.newSubscriptionProTracker()) })

    private val _jwtFlow = MutableStateFlow<ByJwt?>(null)

    /** The parsed signed-in jwt; null when signed out. */
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
    private val stateLock = Any()
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

    /**
     * Takes a parsed jwt, at sign in and after every refresh, with the stored
     * jwt it was parsed from (`storedByJwt`), which the decision reads.
     */
    fun updateJwt(newJwt: ByJwt, storedByJwt: String?) {
        trackStoredJwt(storedByJwt)
        _jwtFlow.value = newJwt
    }

    /** Hands the decision the stored jwt; none (a read that found none) changes nothing. */
    internal fun trackStoredJwt(storedByJwt: String?) {
        if (storedByJwt.isNullOrEmpty()) {
            return
        }
        synchronized(stateLock) {
            proTracker.setJwt(storedByJwt)
            publishWithLock()
        }
    }

    /**
     * A balance loaded; `serverIsPro` is whether it carries a current
     * subscription. The plan is the server's from here on. Returns whether to
     * refresh the jwt now (one refresh per stored jwt).
     */
    fun balanceLoaded(serverIsPro: Boolean): Boolean = synchronized(stateLock) {
        val refreshJwt = proTracker.balanceLoaded(serverIsPro)
        publishWithLock()
        refreshJwt
    }

    /**
     * A balance read failed (an error, or no balance). The plan stays the
     * server's last answer, or before one Pro by the claim or unknown, now
     * Unreachable: never Free. Asks for no jwt refresh.
     */
    fun balanceFailed() {
        synchronized(stateLock) {
            proTracker.balanceFailed()
            publishWithLock()
        }
    }

    /** A sign out: no jwt, and the decision forgets the session. */
    fun clearJwt() {
        synchronized(stateLock) {
            if (proTrackerLazy.isInitialized()) {
                proTracker.reset()
            }
            _isPro.value = false
            _plan.value = ShownPlan.Loading
        }
        _jwtFlow.value = null
    }

    /** Publishes the decision's Pro and plan. The caller holds stateLock. */
    private fun publishWithLock() {
        _isPro.value = proTracker.isPro
        _plan.value = ShownPlan.of(sdkPlan = proTracker.shownPlan, lastReadFailed = proTracker.lastReadFailed)
    }

}
