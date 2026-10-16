package com.bringyour.network

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs `work` on another thread for a burst of requests, at most one run
 * pending at a time: a request while a run is pending folds into it. The
 * pending mark clears as the run starts, before `work` reads anything, so a
 * request made after that posts another run and no change is left unread; a
 * request landing between the clear and the read costs one extra run.
 *
 * Safe for concurrent use: requests come from the sdk's listener threads.
 */
internal class CoalescedPost(
    /** Posts a run to the thread `work` runs on: the main looper in the app. */
    private val post: (Runnable) -> Unit,
    private val work: () -> Unit,
) {
    private val pending = AtomicBoolean(false)

    /** Asks for a run of `work`: posts one unless one is pending. */
    fun request() {
        if (pending.compareAndSet(false, true)) {
            post(Runnable {
                pending.set(false)
                work()
            })
        }
    }
}
