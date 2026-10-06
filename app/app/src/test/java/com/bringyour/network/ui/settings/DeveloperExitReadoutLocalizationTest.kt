package com.bringyour.network.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No word of the developer screen's exit readout is a hardcoded literal: the
 * exit row and its presentation take every word from a string resource (the
 * dev_state_* keys the Windows and Linux pages use) or from the sdk (a window
 * type, a warning cause), and the store's translations reach the catalogs.
 * The row used to append English ("tier", "benched", "done", "proven") in
 * every language.
 *
 * Reads the sources and the generated catalogs only, so it does not depend on
 * the presentation's api.
 */
class DeveloperExitReadoutLocalizationTest {
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

        private const val DEVELOPER_SCREEN =
            "src/main/java/com/bringyour/network/ui/settings/DeveloperScreen.kt"
        private const val DEVELOPER_EXIT_PRESENTATION =
            "src/main/java/com/bringyour/network/ui/settings/DeveloperExitPresentation.kt"

        private fun stringValue(locale: String, name: String): String? {
            val dir = if (locale == "en") "values" else "values-$locale"
            val xml = moduleFile("src/main/res/$dir/strings.xml").readText()
            return Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                .find(xml)?.groupValues?.get(1)
        }

        // the source without its comments
        private fun code(source: String): String =
            source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("//[^\n]*"), "")

        // the string literals of the code, each without its template expressions
        private fun literals(code: String): List<String> =
            Regex("\"(?:[^\"\\\\\n]|\\\\.)*\"").findAll(code)
                .map { match ->
                    match.value.removeSurrounding("\"")
                        .replace(Regex("\\$\\{[^}]*\\}"), "")
                        .replace(Regex("\\$[A-Za-z_][A-Za-z0-9_]*"), "")
                }
                .toList()

        // the body of the function the signature declares, or "" (braces
        // balanced from the first '{' after the signature)
        private fun functionBody(source: String, signature: String): String {
            val at = source.indexOf(signature)
            if (at < 0) {
                return ""
            }
            val open = source.indexOf('{', at)
            var depth = 0
            for (i in open until source.length) {
                if (source[i] == '{') depth += 1
                if (source[i] == '}') {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(open, i + 1)
                    }
                }
            }
            return ""
        }
    }

    @Test
    fun noExitReadoutWordIsAHardcodedLiteral() {
        val row = functionBody(moduleFile(DEVELOPER_SCREEN).readText(), "private fun DeveloperExitRow(")
        assertTrue("DeveloperExitRow not found", row.isNotEmpty())
        val presentation = moduleFile(DEVELOPER_EXIT_PRESENTATION).readText()
        for ((name, source) in listOf("DeveloperExitRow" to row, "DeveloperExitPresentation.kt" to presentation)) {
            // a separator or an arrow has no letters; any word does
            val words = literals(code(source)).filter { literal -> literal.any { it.isLetter() } }
            assertEquals("$name shows hardcoded words", emptyList<String>(), words)
        }
    }

    @Test
    fun theStateWordsAreTheStoresTranslatedKeys() {
        val translations = mapOf(
            "en" to mapOf(
                "dev_state_tier" to "tier %1\$s",
                "dev_state_benched" to "benched",
                "dev_state_warned" to "warned",
                "dev_state_done" to "done",
                "dev_state_proven" to "proven",
            ),
            "de" to mapOf(
                "dev_state_tier" to "Stufe %1\$s",
                "dev_state_benched" to "auf der Ersatzbank",
                "dev_state_warned" to "gewarnt",
                "dev_state_done" to "fertig",
                "dev_state_proven" to "bewährt",
            ),
        )
        for ((locale, values) in translations) {
            for ((name, want) in values) {
                assertEquals("$name in $locale", want, stringValue(locale, name))
            }
        }
        for (locale in listOf("ar", "es", "ja", "ru", "zh")) {
            for (name in translations.getValue("en").keys) {
                assertNotNull("$name has no $locale translation", stringValue(locale, name))
            }
        }
        // p2p is a transport name: one untranslated value serves every locale
        val english = moduleFile("src/main/res/values/strings.xml").readText()
        assertTrue(english.contains("<string name=\"dev_state_p2p\" translatable=\"false\">p2p</string>"))
    }
}
