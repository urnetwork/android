package com.bringyour.network.ui.account

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Reset extenders action of the Extenders section (EXTENDER.md E7), read
 * from the sources: the row opens a confirmation, only the confirmation's
 * warning button resets, the reset goes through the view model to the sdk
 * controller, and the strings it shows are in the catalogs. The compose tree
 * itself needs a device to run, which these JVM tests do not have; the form's
 * behavior is ExtenderFormModelTest's.
 */
class ResetExtendersWiringTest {
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

        private const val ACCOUNT_DIR = "src/main/java/com/bringyour/network/ui/account"

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }

        private fun stringValue(locale: String, name: String): String? {
            val dir = if (locale == "en") "values" else "values-$locale"
            val xml = moduleFile("src/main/res/$dir/strings.xml").readText()
            return Regex("<string name=\"$name\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
        }
    }

    @Test
    fun theRowAsksBeforeTheResetRuns() {
        val screen = moduleFile("$ACCOUNT_DIR/ExtendersScreen.kt").readText()
        // the row only opens the confirmation, and only while a device is
        // there to reset through
        val rowCall = between(screen, "text = stringResource(id = R.string.reset_extenders),", "HorizontalDivider()")
        assertTrue(rowCall.contains("onClick = { showResetDialog = true },"))
        assertTrue(rowCall.contains("enabled = viewModel.editable,"))
        assertTrue(!rowCall.contains("resetExtenders()"))

        // the confirmation says what goes and resets from its warning button
        val dialog = between(screen, "private fun ResetExtendersDialog(", "\n}\n")
        assertTrue(dialog.contains("R.string.reset_extenders_confirm"))
        val confirm = between(dialog, "onClick = onConfirm,", "}")
        assertTrue(confirm.contains("style = ButtonStyle.WARNING,"))
        assertTrue(dialog.contains("R.string.cancel"))

        // confirmed: the view model resets, the form shows what is left, and
        // only a reset that ran is confirmed to the user
        val confirmed = between(screen, "onConfirm = {", "\n        )\n")
        assertTrue(confirmed.contains("showResetDialog = false"))
        val resetCall = confirmed.indexOf("if (viewModel.resetExtenders()) {")
        assertTrue(0 <= resetCall)
        val toast = confirmed.indexOf("R.string.extenders_reset_done")
        assertTrue(resetCall < toast)
        assertTrue(confirmed.indexOf("viewModel.settings?.let(fillSettings)") in resetCall..toast)
        assertTrue(confirmed.indexOf("fillPrivateExtender(viewModel.privateExtender)") in resetCall..toast)
    }

    @Test
    fun theViewModelResetsThroughTheSdkController() {
        val viewModel = moduleFile("$ACCOUNT_DIR/ExtendersViewModel.kt").readText()
        assertTrue(viewModel.contains("controllerOwner.controller?.let { settingsUi(it.resetExtenders()) }"))
        assertTrue(viewModel.contains("fun resetExtenders(): Boolean = form.reset()"))
    }

    @Test
    fun theStringsAreLocalized() {
        for (name in listOf("reset_extenders", "reset_extenders_confirm", "extenders_reset_done")) {
            for (locale in listOf("en", "ar", "de", "es", "ru", "zh")) {
                assertNotNull("$name is missing in $locale", stringValue(locale, name))
            }
        }
        assertTrue(stringValue("en", "reset_extenders_confirm")!!.contains("removes the extenders you added"))
    }
}
