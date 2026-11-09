package com.bringyour.network

import com.bringyour.sdk.ByJwt
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.SubscriptionProTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JwtManager @Inject constructor() {

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
    private val proTrackerLazy = lazy { Sdk.newSubscriptionProTracker() }
    val proTracker: SubscriptionProTracker by proTrackerLazy

    /** `storedByJwt` is the stored jwt `newJwt` was parsed from. */
    fun updateJwt(newJwt: ByJwt, storedByJwt: String?) {
        if (!storedByJwt.isNullOrEmpty()) {
            proTracker.setJwt(storedByJwt)
        }
        _jwtFlow.value = newJwt
    }

    fun clearJwt() {
        if (proTrackerLazy.isInitialized()) {
            proTracker.reset()
        }
        _jwtFlow.value = null
    }

}
