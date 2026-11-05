package com.bringyour.network

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign-out used to clear only local state: the device's client stayed active and
 * its session valid, and a failed sign-in left the client the server allocated
 * for it. Each counted toward the network's active top-level clients until the
 * idle reap. The wiring is read from the source, since MainApplication needs a
 * device (as ConnectSignOutFenceWiringTest).
 */
class ServerSignOutTest {
    private val allocated = "00000000-0000-0000-0000-000000000008"
    private val deviceClient = "00000000-0000-0000-0000-000000000009"

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    private fun between(source: String, start: String, end: String): String {
        val from = source.indexOf(start)
        assertTrue("missing $start", 0 <= from)
        val to = source.indexOf(end, from)
        assertTrue("missing after $start: $end", 0 <= to)
        return source.substring(from, to)
    }

    @Test
    fun `logout signs out on the server after the device stops and before the credential is cleared`() {
        val logout = between(source("MainApplication.kt"), "    private fun logoutInternal() {", "\n    }\n")
        val stop = logout.indexOf("stop()")
        val signOut = logout.indexOf(".signOut {")
        val localLogout = logout.indexOf("asyncLocalState?.localState?.logout()")
        val clear = logout.indexOf("api?.byJwt = null")
        assertTrue(logout, 0 <= stop && stop < signOut)
        assertTrue(logout, signOut < localLogout && localLogout < clear)
        assertTrue(logout, logout.contains("BoundedServerCall(SERVER_SIGN_OUT_MILLIS)"))
    }

    @Test
    fun `a sign-in attempt removes its abandoned client before it answers`() {
        val authenticate = between(
            source("MainApplication.kt"),
            "    internal fun authenticateLoginClient(",
            "\n    }\n",
        )
        val removal = authenticate.indexOf("removeAbandonedLoginClient(request.api,")
        val answer = authenticate.indexOf("callback(completion)")
        assertTrue(authenticate, authenticate.contains("abandonedLoginClientId(completion,"))
        assertTrue(authenticate, 0 <= removal && removal < answer)
    }

    @Test
    fun `a server call answered on the sdk thread ends the wait`() {
        val started = AtomicInteger()
        val answered = BoundedServerCall(5_000).await { done ->
            started.incrementAndGet()
            Thread { done() }.start()
        }
        assertTrue(answered)
        assertEquals(1, started.get())
    }

    @Test
    fun `an unanswered server call ends at the bound`() {
        val begin = System.nanoTime()
        val answered = BoundedServerCall(100).await { }
        val elapsedMillis = (System.nanoTime() - begin) / 1_000_000
        assertFalse(answered)
        assertTrue("$elapsedMillis ms", elapsedMillis in 90 until 5_000)
    }

    @Test
    fun `a server call that cannot start does not wait`() {
        val begin = System.nanoTime()
        val answered = BoundedServerCall(5_000).await { throw IllegalStateException("no api") }
        assertFalse(answered)
        assertTrue((System.nanoTime() - begin) / 1_000_000 < 5_000)
    }

    @Test
    fun `a failed sign-in leaves its allocated client to remove`() {
        val failed = LoginClientCompletion.Failed(
            LoginStartupFailure.DEVICE_LOCAL_CONFIGURATION_FAILED,
            clientId = allocated,
        )
        assertEquals(allocated, abandonedLoginClientId(failed, deviceClient))
        assertEquals(allocated, abandonedLoginClientId(failed, null))
    }

    @Test
    fun `a ready sign-in, a failure without a client and the device's own client leave nothing`() {
        assertNull(abandonedLoginClientId(LoginClientCompletion.Ready(allocated), deviceClient))
        assertNull(
            abandonedLoginClientId(
                LoginClientCompletion.Failed(LoginStartupFailure.AUTH_CLIENT_REQUEST_FAILED),
                deviceClient,
            ),
        )
        assertNull(
            abandonedLoginClientId(
                LoginClientCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT, clientId = deviceClient),
                deviceClient,
            ),
        )
    }
}
