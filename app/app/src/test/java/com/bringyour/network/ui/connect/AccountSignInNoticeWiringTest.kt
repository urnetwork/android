package com.bringyour.network.ui.connect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account sign-in notice as wired into the app, read from the sources:
 * one subscription on the device's api, the api whose AuthLogout the app
 * handles, closed with the device's other subscriptions; the cause read in
 * the listener on the sdk's thread, then posted to the main thread for the
 * device that is still signed in; no sign-out and no disconnect for it; a
 * sign-out and a new sign-in drop the notice; and the connect screen shows it
 * in both arrangements. The device and the screens need a device, which these
 * JVM tests do not have; the behavior is AccountSignInNoticeTest's.
 */
class AccountSignInNoticeWiringTest {
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
    }

    @Test
    fun theDevicesApiIsSubscribedOnceAndClosedWithTheDevice() {
        val deviceManager = source("DeviceManager.kt")
        assertTrue(deviceManager.contains("var onAccountSignInRequired: ((device: DeviceLocal, cause: String) -> Unit)? = null"))
        // a retired device keeps the handler it was created with, as for AuthLogout
        val init = between(deviceManager, "fun initDevice(", "\n    }\n")
        assertTrue(init.contains("val onAccountSignInRequired = onAccountSignInRequired"))
        // the device's api, the one whose AuthLogout the device forwards; the
        // cause is read in the listener, on the sdk's thread
        val listener = between(init, "accountSignInRequiredSub = newDevice.api?.let { deviceApi ->", "deviceChanges.prepareUpdate(newDevice)")
        assertTrue(listener, listener.contains("deviceApi.addAccountSignInRequiredListener {"))
        assertTrue(listener, listener.contains("onAccountSignInRequired?.invoke(newDevice, deviceApi.accountSignInRequiredCause.orEmpty())"))
        assertEquals(1, Regex(Regex.escape(".addAccountSignInRequiredListener")).findAll(deviceManager).count())
        // closed with the device's other subscriptions: on a replaced device and at the clear
        val close = between(deviceManager, "private fun closeDeviceSubscriptionsLocked() {", "\n    }\n")
        assertTrue(close.contains("accountSignInRequiredSub?.close()"))
        assertTrue(close.contains("accountSignInRequiredSub = null"))
    }

    @Test
    fun theAppPostsTheNoticeForTheSignedInDeviceAndNeverSignsOut() {
        val application = source("MainApplication.kt")
        val handler = between(application, "deviceManager.onAccountSignInRequired = { signedInDevice, cause ->", "\n        }\n")
        val post = handler.indexOf("Handler(mainLooper).post {")
        val guard = handler.indexOf("if (authSessionOwner !== logoutOwner || device !== signedInDevice) {")
        val notice = handler.indexOf("accountSignInNotices.accountSignInRequired(cause)")
        assertTrue(handler, 0 <= post && post < guard && guard < notice)
        // the device stays signed in and connected
        for (call in listOf("logoutInternal(", "logout()", "stop()", "disconnect", "startActivity", "signInNotices", "clearDevice")) {
            assertFalse("$call in: $handler", handler.contains(call))
        }
        // set before the device is created, as the AuthLogout handler is
        val initDevice = between(application, "private fun initDevice(byClientJwt: String): DeviceInitResult {", "\n    }\n")
        assertTrue(
            initDevice.indexOf("deviceManager.onAccountSignInRequired = {") < initDevice.indexOf("val deviceInitResult = deviceManager.initDevice(")
        )
    }

    @Test
    fun aSignOutAndANewSignInDropTheNotice() {
        val application = source("MainApplication.kt")
        // every sign-out goes through logoutInternal: the user's, and the sdk's AuthLogout
        assertTrue(between(application, "    private fun logoutInternal() {", "\n    }\n").contains("accountSignInNotices.clear()"))
        assertTrue(between(application, "    fun logout() {", "\n    }\n").contains("logoutInternal()"))
        assertTrue(
            between(application, "deviceManager.onAuthLogout = { signedOutDevice, cause ->", "\n        }\n").contains("logoutInternal()")
        )
        // a new sign-in, once its device is ready
        val initDevice = between(application, "private fun initDevice(byClientJwt: String): DeviceInitResult {", "\n    }\n")
        val ready = initDevice.indexOf("if (deviceInitResult !is DeviceInitResult.Ready) {")
        val clear = initDevice.indexOf("accountSignInNotices.clear()")
        assertTrue(initDevice, 0 <= ready && ready < clear)
    }

    @Test
    fun theConnectScreenShowsTheNoticeInBothArrangements() {
        // the connect pane beside the statistics, and the drawer's above-the-fold notices
        val pane = between(source("ui/connect/ConnectScreen.kt"), "ClientLimitNotice(navController = navController)", "ConnectActionButtonRow(")
        assertTrue(pane.contains("AccountSignInNotice()"))
        val drawer = between(source("ui/connect/ConnectActions.kt"), "ClientLimitNotice(navController = navController)", "// the fold marker")
        assertTrue(drawer.contains("AccountSignInNotice()"))

        // its close is the user's dismissal, and the words are the cause's
        val notice = source("ui/connect/AccountSignInNotice.kt")
        assertTrue(notice.contains("onClick = { application.accountSignInNotices.clear() },"))
        assertTrue(notice.contains("current.message(context::getString)"))
        assertTrue(notice.contains("contentDescription = stringResource(id = R.string.close),"))
    }
}
