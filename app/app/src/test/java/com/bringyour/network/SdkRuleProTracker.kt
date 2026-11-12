package com.bringyour.network

/**
 * The sdk tracker's rule (sdk subscription_pro_tracker.go, tested there), for
 * the jvm tests, which cannot load the sdk's native library. Test jwts are
 * "pro-<n>" or "free-<n>": the claim, and a renewal count, of one network.
 *
 * - The plan is the server's answer once a balance has loaded; a failed read
 *   after that keeps it. Before that it is "pro" by a Pro claim, else
 *   "unknown": never "free".
 * - A claim that disagrees with the server asks for one refresh per jwt; the
 *   same jwt again changes nothing and asks for nothing.
 */
internal class SdkRuleProTracker : ProTracker {
    private var byJwt = ""
    private var claimPro = false
    private var loaded = false
    private var serverIsPro = false
    private var readFailed = false
    private var refreshRequested = false

    override fun setJwt(byJwt: String): Boolean {
        if (byJwt.isEmpty() || byJwt == this.byJwt) {
            return false
        }
        val before = shownPlan
        this.byJwt = byJwt
        claimPro = byJwt.startsWith("pro-")
        refreshRequested = false
        return before != shownPlan
    }

    override fun balanceLoaded(serverIsPro: Boolean): Boolean {
        loaded = true
        this.serverIsPro = serverIsPro
        readFailed = false
        if (byJwt.isEmpty() || serverIsPro == claimPro || refreshRequested) {
            return false
        }
        refreshRequested = true
        return true
    }

    override fun balanceFailed(): Boolean {
        val changed = !readFailed
        readFailed = true
        return changed
    }

    override val isPro: Boolean
        get() = shownPlan == "pro"

    override val shownPlan: String
        get() = when {
            loaded && serverIsPro -> "pro"
            loaded -> "free"
            claimPro -> "pro"
            else -> "unknown"
        }

    override val lastReadFailed: Boolean
        get() = readFailed

    override fun reset() {
        byJwt = ""
        claimPro = false
        loaded = false
        serverIsPro = false
        readFailed = false
        refreshRequested = false
    }
}
