package com.bringyour.network

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign out ends a quick connect start still waiting on its balance,
 * read from the source (review of the connect tap follow-ups, 2026-10-16).
 * The gate decides it up to five seconds later, by then possibly on the next
 * account's device. MainApplication needs a device, which these JVM tests do
 * not have; what the ending does (it never connects, and its surface is
 * answered) is QuickStartConnectTest's.
 */
class QuickConnectSignOutWiringTest {

    @Test
    fun theSignOutEndsAWaitingQuickConnectStart() {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/MainApplication.kt") }
            .firstOrNull { it.isFile }
        assertNotNull(file)
        val source = file!!.readText()
        val from = source.indexOf("    private fun logoutInternal() {")
        assertTrue("missing logoutInternal", 0 <= from)
        val logout = source.substring(from, source.indexOf("\n    }\n", from))
        assertTrue("the sign out leaves a waiting quick connect start", logout.contains("quickStartConnect.cancel()"))
    }
}
