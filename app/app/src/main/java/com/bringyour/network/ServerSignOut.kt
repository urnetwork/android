package com.bringyour.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The server half of signing out. The server does not deactivate a client when
 * its session is revoked, so a client the app leaves behind counts toward the
 * network's active top-level clients until the idle reap: the device's own
 * client at sign-out, and a client the server allocated for a sign-in that then
 * failed. Both are removed while the API still holds the network credential.
 */

/**
 * Waits at most `boundMillis` for one sdk call's answer. `start` hands the sdk
 * the `answered` callback; the sdk answers on its own thread, so waiting on the
 * main thread cannot hold the answer. A start that throws answers nothing.
 * True when the sdk answered within the bound.
 */
internal class BoundedServerCall(private val boundMillis: Long) {
    fun await(start: (answered: () -> Unit) -> Unit): Boolean {
        val answered = CountDownLatch(1)
        try {
            start { answered.countDown() }
        } catch (_: Throwable) {
            return false
        }
        return try {
            answered.await(boundMillis, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
}

/**
 * The client a finished sign-in leaves behind, if any: one the server allocated
 * for an attempt that failed, unless it is the signed-in device's own client
 * (a stale completion of an attempt whose device did start).
 */
internal fun abandonedLoginClientId(completion: LoginClientCompletion, deviceClientId: String?): String? {
    val failed = completion as? LoginClientCompletion.Failed ?: return null
    val clientId = failed.clientId ?: return null
    return clientId.takeIf { it != deviceClientId }
}
