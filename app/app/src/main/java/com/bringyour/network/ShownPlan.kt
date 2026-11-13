package com.bringyour.network

/**
 * The plan a plan card shows, as the sdk decides it
 * (SubscriptionProTracker.GetShownPlan, through JwtManager.plan): Pro or Free
 * as the server answered (Pro also by the stored jwt's claim before it
 * answers), or neither until the server answers: Loading while the first
 * balance read is on its way, Unreachable once a read failed, which a card
 * shows with a retry. Only the server says Free. In December 2025 the balance
 * read failed under database load and the app showed paying networks Free,
 * with its upgrade prompts (support inbox 4074).
 */
enum class ShownPlan {
    /** The server answered Pro, or before it answers, the stored jwt's claim is Pro. */
    Pro,

    /** The server answered Free. */
    Free,

    /** No answer yet and the claim is not Pro: the first read is on its way. */
    Loading,

    /** No answer yet and the claim is not Pro: the last read failed. */
    Unreachable;

    /**
     * A plan card offers Pro (Change, Get Pro, the upgrade tip) only on the
     * server's Free: never while the plan is unknown.
     */
    val sellsPro: Boolean
        get() = this == Free

    companion object {
        /**
         * From the sdk's decision: its plan, one of the sdk's ShownPlan*
         * values ("pro", "free", "unknown"; written out, as the jvm unit
         * tests cannot load the sdk's class), and whether its last read
         * failed.
         */
        fun of(sdkPlan: String, lastReadFailed: Boolean): ShownPlan = when (sdkPlan) {
            "pro" -> Pro
            "free" -> Free
            else -> if (lastReadFailed) Unreachable else Loading
        }
    }
}
