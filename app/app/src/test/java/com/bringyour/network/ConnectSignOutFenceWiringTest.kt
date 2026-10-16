package com.bringyour.network

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every sign out fences the signed-out device's connects as it begins (owner
 * report 2026-10-16, as the Apple app's signOutStarted), read from the source
 * since MainApplication and DeviceManager need a device: the app's own sign
 * out before it tears anything down, and the sdk's (the server's, or the
 * account deletion's) on the sdk's thread, before the logout is posted to the
 * main thread, where a tap could still be admitted meanwhile. What the fence
 * does is ConnectCommandsTest's.
 */
class ConnectSignOutFenceWiringTest {

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
    fun theAppsOwnSignOutFencesBeforeItTearsDown() {
        val logout = between(source("MainApplication.kt"), "    private fun logoutInternal() {", "\n    }\n")
        val fence = logout.indexOf("connectCommands.signOutStarted(")
        val stop = logout.indexOf("stop()")
        assertTrue(logout, 0 <= fence && fence < stop)
    }

    @Test
    fun theSdksSignOutFencesOnItsThreadBeforeTheLogoutIsPosted() {
        val deviceManager = source("DeviceManager.kt")
        val listener = between(deviceManager, "authLogoutSub = newDevice.addAuthLogoutListener {", "}\n")
        val started = listener.indexOf("onLogoutStarted?.invoke(newDevice)")
        val logout = listener.indexOf("onAuthLogout?.invoke(")
        assertTrue(listener, 0 <= started && started < logout)

        val application = source("MainApplication.kt")
        val hook = between(application, "deviceManager.onLogoutStarted = { signedOutDevice ->", "\n        }\n")
        assertTrue(hook, hook.contains("connectCommands.signOutStarted(signedOutDevice)"))
    }
}
