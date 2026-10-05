package com.bringyour.network.ui.connect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The connect screen shows the sdk's CONNECT_FAILED the way the windows and
 * linux apps do: "Couldn't connect" with the coral indicator, a warning in
 * place of the provider grid, and Retry beside Disconnect (the drawer's
 * buttons are connectActionButtons, tested in InsufficientBalancePolicyTest).
 * The composables need a device, so this reads their source.
 */
class ConnectFailedStateTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.exists() }
            assertNotNull("$path not found", file)
            return file!!
        }

        private const val CONNECT_UI = "src/main/java/com/bringyour/network/ui/connect"

        private fun source(name: String): String = moduleFile("$CONNECT_UI/$name").readText()
    }

    @Test
    fun theStatusLineSaysCouldNotConnectWithTheCoralIndicator() {
        val indicator = source("ConnectStatusIndicator.kt")
        assertTrue(
            indicator.contains("status == ConnectStatus.CONNECT_FAILED -> stringResource(id = R.string.conn_failed)"),
        )
        assertTrue(
            indicator.contains("status == ConnectStatus.CONNECT_FAILED -> R.drawable.circle_indicator_red"),
        )
        // the desktop apps' coral (windows kUrCoral, android Red)
        val drawable = moduleFile("src/main/res/drawable/circle_indicator_red.xml").readText()
        assertTrue(drawable.contains("android:fillColor=\"#FF6C58\""))
    }

    @Test
    fun theConnectButtonShowsAWarningInPlaceOfTheGrid() {
        val button = source("ConnectButton.kt")
        assertTrue(button.contains("visible = updatedStatus == ConnectStatus.CONNECT_FAILED && !insufficientBalance,"))
        assertTrue(button.contains("if (!insufficientBalance && updatedStatus != ConnectStatus.CONNECT_FAILED) {"))
    }

    @Test
    fun theDrawerOffersRetryBesideDisconnect() {
        val actions = source("ConnectActions.kt")
        val retry = actions.indexOf("if (actionButtons.retry) {")
        assertTrue("no retry row", 0 <= retry)
        val row = actions.substring(retry, actions.indexOf("} else if (actionButtons.disconnect) {", retry))
        assertTrue(row.contains("onClick = connect,"))
        assertTrue(row.contains("stringResource(id = R.string.retry)"))
        assertTrue(row.contains("disconnectButton(Modifier.weight(1f))"))
    }

    @Test
    fun couldNotConnectIsInEveryCatalog() {
        val res = moduleFile("src/main/res")
        val catalogs = res.listFiles { dir -> dir.name == "values" || dir.name.startsWith("values-") }!!
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue(catalogs.size > 1)
        for (catalog in catalogs) {
            assertTrue(
                "${catalog.parentFile?.name} has no conn_failed",
                catalog.readText().contains("<string name=\"conn_failed\">"),
            )
        }
        val english = Regex("<string name=\"conn_failed\">(.*?)</string>")
            .find(File(res, "values/strings.xml").readText())?.groupValues?.get(1)
        assertEquals("Couldn\\'t connect", english)
    }
}
