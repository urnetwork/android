package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The account deletion signs out only the account deleted (owner report
 * 2026-10-16), read from the source since the screen and its view model need
 * a device: the device is captured when the user asks, before the server is
 * asked, and the sign out once it answers goes through the device-checked
 * MainApplication.logout(signedOutDevice) (signOutApplies, SignOutTargetTest).
 */
class DeleteAccountSignOutWiringTest {

    private fun source(name: String): String {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/$name") }
            .firstOrNull { it.isFile }
        assertNotNull(name, file)
        return file!!.readText()
    }

    @Test
    fun theDeletionCapturesItsDeviceBeforeAskingTheServer() {
        val viewModel = source("ui/settings/SettingsViewModel.kt")
        val from = viewModel.indexOf("    val deleteAccount: (")
        assertTrue(0 <= from)
        val deleteAccount = viewModel.substring(from, viewModel.indexOf("\n    }\n", from))
        val capture = deleteAccount.indexOf("val signedOutDevice = deviceManager.device")
        val ask = deleteAccount.indexOf("signedOutDevice?.api?.networkDelete")
        assertTrue(deleteAccount, 0 <= capture && capture < ask)
        assertTrue(deleteAccount.contains("onSuccess(signedOutDevice)"))
    }

    @Test
    fun theDeletionSignsOutOnlyItsDevice() {
        val screen = source("ui/settings/SettingsScreen.kt")
        val from = screen.indexOf("                                    deleteAccount(")
        assertTrue(0 <= from)
        val call = screen.substring(from, screen.indexOf("{ failed ->", from))
        assertTrue(call, call.contains("application?.logout(signedOutDevice)"))
        assertFalse(call, call.contains("application?.logout()"))
    }
}
