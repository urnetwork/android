package com.bringyour.network.ui.settings

import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotesTest {

    private fun file(locales: String, date: String = "2026-11-19") =
        """{"version": "2026.11", "date": "$date", "locales": {$locales}}"""

    // a file with a note in each of [codes]
    private fun notes(vararg codes: String): ReleaseNotes =
        ReleaseNotes.parse(file(codes.joinToString(", ") { "\"$it\": {\"title\": \"T\", \"sections\": []}" }))!!

    // the locale of the note chosen for these preferred tags
    private fun ReleaseNotes.localeFor(vararg tags: String): String? =
        noteFor(tags.map { Locale.forLanguageTag(it) })?.locale

    @Test
    fun parsesTheGeneratedShape() {
        val notes = ReleaseNotes.parse(
            """
            {
              "version": "2026.11",
              "date": "2026-11-19",
              "locales": {
                "en": {
                  "title": "What's new",
                  "intro": "Faster connections.",
                  "sections": [
                    { "heading": "Connect", "items": ["One", "Two"] },
                    { "items": ["Three"] }
                  ]
                },
                "de": { "title": "Neu", "sections": [] }
              }
            }
            """.trimIndent()
        )
        assertNotNull(notes)
        assertEquals("2026.11", notes!!.version)
        assertEquals(LocalDate.of(2026, 11, 19), notes.date)
        assertEquals(listOf("en", "de"), notes.notes.keys.toList())
        assertEquals(
            ReleaseNote(
                locale = "en",
                title = "What's new",
                intro = "Faster connections.",
                sections = listOf(
                    ReleaseNoteSection(heading = "Connect", items = listOf("One", "Two")),
                    ReleaseNoteSection(heading = null, items = listOf("Three")),
                ),
            ),
            notes.notes["en"],
        )
        assertEquals(ReleaseNote(locale = "de", title = "Neu", intro = null, sections = listOf()), notes.notes["de"])
    }

    @Test
    fun theBundledNoteParsesWithAnEnglishNote() {
        // what npm run gen wrote; gradle runs unit tests in the module directory
        val asset = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/assets/" + ReleaseNotes.ASSET) }
            .firstOrNull { it.isFile }
        assertNotNull("${ReleaseNotes.ASSET} not found", asset)
        val notes = ReleaseNotes.parse(asset!!.readText())
        assertNotNull(notes)
        val english = notes!!.notes["en"]
        assertNotNull(english)
        assertNotNull(notes.date)
        assertEquals(english, notes.noteFor(listOf(Locale.forLanguageTag("en-US"))))
    }

    @Test
    fun anythingElseIsNoNote() {
        val texts = listOf(
            "",
            "release notes",
            "null",
            "[]",
            "{}",
            // truncated
            """{"version": "2026.11", "date": "2026-11-19", "locales": {"en": {"title": "T"}}""",
            // a field missing
            """{"date": "2026-11-19", "locales": {"en": {"title": "T"}}}""",
            """{"version": "2026.11", "locales": {"en": {"title": "T"}}}""",
            """{"version": "2026.11", "date": "2026-11-19"}""",
            """{"version": "2026.11", "date": "2026-11-19", "locales": {"en": {"sections": []}}}""",
            // a wrong type
            """{"version": "2026.11", "date": "2026-11-19", "locales": []}""",
            """{"version": "2026.11", "date": "2026-11-19", "locales": {"en": {"title": null}}}""",
            """{"version": "2026.11", "date": "2026-11-19", "locales": {"en": {"title": "T", "sections": "x"}}}""",
            """{"version": "2026.11", "date": "2026-11-19", "locales": {"en": {"title": "T", "sections": [{"items": [1]}]}}}""",
            // no note with a title
            file(""),
            file(""""en": {"title": " "}"""),
        )
        for (text in texts) {
            assertNull(text, ReleaseNotes.parse(text))
        }
    }

    @Test
    fun keysANewerGeneratorAddsAreSkipped() {
        val notes = ReleaseNotes.parse(
            """{"version": "2026.11", "date": "2026-11-19", "channel": "beta",
               "locales": {"en": {"title": "T", "image": "x.png", "sections": [{"items": ["A"], "style": 1}]}}}"""
        )
        assertEquals(
            ReleaseNote(locale = "en", title = "T", intro = null, sections = listOf(ReleaseNoteSection(null, listOf("A")))),
            notes?.notes?.get("en"),
        )
    }

    @Test
    fun blankPartsAreDropped() {
        val notes = ReleaseNotes.parse(
            file(
                """
                "en": {"title": "T", "intro": " ", "sections": [
                  {"heading": "", "items": [" ", ""]},
                  {"heading": " ", "items": ["Kept"]},
                  {"heading": "Heading only", "items": []}
                ]},
                "de": {"title": ""}
                """
            )
        )!!
        assertEquals(listOf("en"), notes.notes.keys.toList())
        assertEquals(
            ReleaseNote(
                locale = "en",
                title = "T",
                intro = null,
                sections = listOf(
                    ReleaseNoteSection(heading = null, items = listOf("Kept")),
                    ReleaseNoteSection(heading = "Heading only", items = listOf()),
                ),
            ),
            notes.notes["en"],
        )
    }

    // the running build's version, as the Settings row shows it
    private val appVersion = "2026.11.17+1101053110"

    @Test
    fun aDateThatIsNoDayKeepsTheNote() {
        val notes = ReleaseNotes.parse(file(""""en": {"title": "T"}""", date = "2026-13-45"))!!
        assertNull(notes.date)
        assertEquals("T", notes.notes["en"]?.title)
        // the line under the title is then the running version alone
        assertEquals(appVersion, notes.versionLine(appVersion, Locale.US))
    }

    @Test
    fun theLineUnderTheTitleIsTheRunningVersionAndTheNotesDay() {
        val notes = ReleaseNotes.parse(file(""""en": {"title": "T"}"""))!!
        // the running build's version, never the note's own (2026.11)
        assertEquals("2026.11.17+1101053110 · Nov 19, 2026", notes.versionLine(appVersion, Locale.US))
        // the day in the reader's medium form
        assertEquals("2026.11.17+1101053110 · 19.11.2026", notes.versionLine(appVersion, Locale.GERMANY))
        // each part wrapped, as the sheet bidi-isolates them
        assertEquals("[2026.11.17+1101053110] · [Nov 19, 2026]", notes.versionLine(appVersion, Locale.US) { "[$it]" })
        // the file's calendar day, in any time zone
        val zone = TimeZone.getDefault()
        try {
            for (id in listOf("Pacific/Kiritimati", "Pacific/Pago_Pago")) {
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                assertEquals("2026.11.17+1101053110 · Nov 19, 2026", notes.versionLine(appVersion, Locale.US))
                assertEquals("2026.11.17+1101053110 · 19.11.2026", notes.versionLine(appVersion, Locale.GERMANY))
            }
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun theReadersLanguageElseEnglish() {
        val notes = notes("en", "de", "es", "es-MX", "pt", "pt-BR")
        // the language and region
        assertEquals("pt-BR", notes.localeFor("pt-BR"))
        assertEquals("es-MX", notes.localeFor("es-MX"))
        // else the bare language, before another region of it
        assertEquals("pt", notes.localeFor("pt-PT"))
        assertEquals("es", notes.localeFor("es-AR"))
        assertEquals("de", notes.localeFor("de-AT"))
        assertEquals("de", notes.localeFor("de"))
        // English in any region, and for a language the note does not carry
        assertEquals("en", notes.localeFor("en-GB"))
        assertEquals("en", notes.localeFor("fr-FR"))
        assertEquals("en", notes.localeFor())
    }

    @Test
    fun anotherRegionWhenThereIsNoBareLanguage() {
        val notes = notes("en", "pt-BR")
        assertEquals("pt-BR", notes.localeFor("pt-PT"))
        assertEquals("pt-BR", notes.localeFor("pt"))
    }

    @Test
    fun eachPreferredLanguageInTurnBeforeEnglish() {
        val notes = notes("en", "es")
        // Catalan, then Spanish: the Spanish note, as Android picks the app's strings
        assertEquals("es", notes.localeFor("ca-ES", "es-ES"))
        // the first language the note carries wins
        assertEquals("en", notes.localeFor("en-US", "es-ES"))
        assertEquals("es", notes.localeFor("es-ES", "en-US"))
    }

    @Test
    fun chineseMatchesByScript() {
        val notes = notes("en", "zh", "zh-HK")
        assertEquals("zh", notes.localeFor("zh"))
        assertEquals("zh", notes.localeFor("zh-CN"))
        assertEquals("zh", notes.localeFor("zh-Hans-CN"))
        assertEquals("zh-HK", notes.localeFor("zh-HK"))
        assertEquals("zh-HK", notes.localeFor("zh-Hant-HK"))
        assertEquals("zh-HK", notes.localeFor("zh-TW"))
        assertEquals("zh-HK", notes.localeFor("zh-Hant-TW"))
        // a Traditional reader is never handed Simplified, nor the other way
        assertEquals("en", notes("en", "zh").localeFor("zh-TW"))
        assertEquals("en", notes("en", "zh").localeFor("zh-Hant"))
        assertEquals("en", notes("en", "zh-HK").localeFor("zh-CN"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun retiredCodesAndExtensionsStillMatch() {
        val notes = notes("en", "de", "he", "id")
        // Android 14's regional preferences add extensions to the locale
        assertEquals("de", notes.localeFor("de-DE-u-mu-celsius-fw-mon"))
        assertEquals("he", notes.localeFor("he-IL"))
        assertEquals("he", notes.noteFor(listOf(Locale("iw", "IL")))?.locale)
        assertEquals("id", notes.noteFor(listOf(Locale("in", "ID")))?.locale)
    }

    @Test
    fun noMatchAndNoEnglishIsNoNote() {
        assertNull(notes("de").localeFor("fr-FR"))
        assertEquals("de", notes("de").localeFor("de-CH"))
    }
}
