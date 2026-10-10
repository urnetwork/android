package com.bringyour.network.ui.login

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The remote sign-out notice as wired into the app's existing logout path,
 * read from the sources: the device's listener reads the sdk's cause on the
 * sdk thread before the logout is posted, the handler leaves the notice before
 * it opens the sign-in screen, the app's own sign-out and a new sign-in drop
 * it, and the sign-in host every flavor uses takes it once and shows it. The
 * device and the activities need a device, which these JVM tests do not have;
 * the behavior is SignInNoticeTest's.
 */
class SignInNoticeWiringTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val SOURCE_DIR = "src/main/java/com/bringyour/network"

        private fun source(path: String) = moduleFile("$SOURCE_DIR/$path").readText()

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }

        // the sign-in activity of every flavor (play, github, solana_dapp, ethos_dapp)
        private val LOGIN_ACTIVITIES = listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")
            .map { "src/$it/java/com/bringyour/network/LoginActivity.kt" }
    }

    @Test
    fun theDevicesListenerReadsTheCauseBeforeTheLogoutIsPosted() {
        val deviceManager = source("DeviceManager.kt")
        assertTrue(deviceManager.contains("var onAuthLogout: ((cause: String) -> Unit)? = null"))
        val listener = between(deviceManager, "authLogoutSub = newDevice.addAuthLogoutListener {", "}\n")
        // on the sdk's thread, from the device that fired
        assertTrue(listener, listener.contains("onAuthLogout?.invoke(newDevice.authLogoutCause.orEmpty())"))
    }

    @Test
    fun theLogoutHandlerLeavesTheNoticeThenOpensTheSignInScreen() {
        val application = source("MainApplication.kt")
        val handler = between(application, "deviceManager.onAuthLogout = { cause ->", "\n        }\n")
        val post = handler.indexOf("Handler(mainLooper).post {")
        val logout = handler.indexOf("logoutInternal()")
        val notice = handler.indexOf("signInNotices.authLoggedOut(cause)")
        val signIn = handler.indexOf("startActivity(intent)")
        assertTrue(handler, 0 <= post && post < logout && logout < notice && notice < signIn)
        // the generic logout is as before
        assertTrue(handler.contains("loginStartupTracker.authLoggedOut()"))
        assertTrue(handler.contains("Intent(applicationContext, LoginActivity::class.java)"))
        // only the sdk's logout leaves a notice
        assertEquals(1, Regex(Regex.escape("signInNotices.authLoggedOut(")).findAll(application).count())
    }

    @Test
    fun theAppsOwnSignOutAndANewSignInDropTheNotice() {
        val application = source("MainApplication.kt")
        assertTrue(between(application, "    fun logout() {", "\n    }\n").contains("signInNotices.clear()"))
        val initDevice = between(application, "private fun initDevice(byClientJwt: String): DeviceInitResult {", "\n    }\n")
        val ready = initDevice.indexOf("if (deviceInitResult !is DeviceInitResult.Ready) {")
        val clear = initDevice.indexOf("signInNotices.clear()")
        assertTrue(initDevice, 0 <= ready && ready < clear)

        // the app's own sign-outs go through logout() and leave no notice
        for (path in listOf("ui/components/AccountSwitcher.kt", "ui/settings/SettingsScreen.kt", "ui/login/SwitchAccountScreen.kt")) {
            val text = source(path)
            assertTrue(path, text.contains(".logout()"))
            assertFalse(path, text.contains("signInNotices"))
        }
    }

    @Test
    fun theSignInHostOfEveryFlavorTakesTheNoticeOnceAndShowsIt() {
        val host = source("ui/LoginNavHost.kt")
        val take = between(host, "LaunchedEffect(signInNotices, lifecycleOwner) {", "\n    }\n")
        // by the sign-in screen the user sees, not one stopped behind it
        assertTrue(take.contains("lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {"))
        assertTrue(take.contains("notices.waiting.collect { waiting ->"))
        assertTrue(take.contains("notices.take()?.let { signInNotice = it }"))
        // kept across an activity recreate until the user closes it
        assertTrue(host.contains("var signInNotice by rememberSaveable { mutableStateOf<SignInNotice?>(null) }"))
        val alert = between(host, "signInNotice?.let { notice ->", "\n        }\n")
        assertTrue(alert.contains("SignInNoticeAlert("))
        assertTrue(alert.contains("onDismiss = { signInNotice = null },"))

        for (path in LOGIN_ACTIVITIES) {
            assertTrue(path, moduleFile(path).readText().contains("LoginNavHost("))
        }
    }
}
