package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Settings > Language offers System default and then the languages the app
 * ships (res/xml/locales_config.xml), and shows the one the system applies to
 * the app (support inbox 7485).
 */
class AppLanguageTest {

    private val shipped = listOf("en", "es", "es-MX", "fa", "pt", "pt-BR", "tr", "zh")

    @Test
    fun systemDefaultComesFirstThenTheShippedLanguages() {
        val options = appLanguageOptions(listOf("en", "tr", "fa", "tr"), "System default") { "name:$it" }
        assertEquals(
            listOf(
                AppLanguageOption(tag = "", name = "System default"),
                AppLanguageOption(tag = "en", name = "name:en"),
                AppLanguageOption(tag = "tr", name = "name:tr"),
                AppLanguageOption(tag = "fa", name = "name:fa"),
            ),
            options,
        )
    }

    @Test
    fun noAppLanguageIsSystemDefault() {
        assertEquals("", selectedAppLanguage("", shipped))
    }

    @Test
    fun anExactLanguageIsSelected() {
        assertEquals("tr", selectedAppLanguage("tr", shipped))
        assertEquals("es-MX", selectedAppLanguage("es-MX", shipped))
        assertEquals("pt-BR", selectedAppLanguage("pt-br", shipped))
    }

    @Test
    fun aRegionalFormSelectsItsLanguage() {
        // the system's own settings may apply a regional tag
        assertEquals("fa", selectedAppLanguage("fa-IR", shipped))
        assertEquals("tr", selectedAppLanguage("tr-TR,en", shipped))
    }

    @Test
    fun aLanguageTheAppDoesNotShipIsSystemDefault() {
        assertEquals("", selectedAppLanguage("ka", shipped))
    }

    @Test
    fun eachLanguageIsNamedInItself() {
        assertEquals("Türkçe", languageName("tr"))
        assertEquals("فارسی", languageName("fa"))
        assertEquals("English", languageName("en"))
    }
}
