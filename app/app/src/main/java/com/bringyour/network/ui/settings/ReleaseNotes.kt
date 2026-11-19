package com.bringyour.network.ui.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The release note the app bundles, which the version row in Account ->
 * Settings opens (ReleaseNotesSheet). `npm run gen` in the localizations
 * store writes it to assets/release_notes.json from its
 * release-notes/latest.yaml, the same JSON every app bundles: the latest
 * release only, with `en` always among its locales. Edit the yaml, never the
 * asset.
 */
class ReleaseNotes(
    /** The release the note describes, as people see it ("2026.11"). */
    val version: String,
    /** The release day; null when the file's date is not a day of the calendar. */
    val date: LocalDate?,
    /** The note in each locale the file carries, by the store's BCP 47 code, in the file's order. */
    val notes: Map<String, ReleaseNote>,
) {

    /**
     * The note in the reader's language. Each of [preferred], most preferred
     * first, takes a note in its language and region, else in the bare
     * language (pt-BR reads pt), else in another region of it; when none of
     * them has one, English. Chinese matches by script as well, so a
     * Traditional reader (zh-TW, zh-Hant) is never handed the Simplified
     * `zh`. Null only when the file has neither a match nor `en`.
     */
    fun noteFor(preferred: List<Locale>): ReleaseNote? {
        val available = notes.keys.map { code -> code to LanguageKey.of(Locale.forLanguageTag(code)) }
        for (locale in preferred) {
            val wanted = LanguageKey.of(locale)
            if (wanted.language.isEmpty()) {
                continue
            }
            val sameLanguage = available.filter { (_, key) -> key.sameLanguage(wanted) }
            val match = sameLanguage.firstOrNull { (_, key) -> key.region == wanted.region }
                ?: sameLanguage.firstOrNull { (_, key) -> key.region.isEmpty() }
                ?: sameLanguage.firstOrNull()
            if (match != null) {
                return notes.getValue(match.first)
            }
        }
        return notes[ENGLISH]
    }

    /**
     * The line under the note's title: the note's own version and day
     * ("2026.11 · Nov 19, 2026"), not the running build's version, since a
     * later build carries the same note. The day is the file's calendar day,
     * never shifted by a time zone, in [locale]'s medium form; [isolate]
     * wraps each part (the sheet bidi-isolates them).
     */
    fun versionLine(locale: Locale, isolate: (String) -> String = { it }): String {
        val day = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        return listOfNotNull(version, day).joinToString(" · ") { isolate(it) }
    }

    companion object {
        /** The note under src/main/assets. */
        const val ASSET = "release_notes.json"

        private const val ENGLISH = "en"

        // a key a newer generator adds is skipped, not an error
        private val json = Json { ignoreUnknownKeys = true }

        /** The bundled note, or null when it is missing or unreadable. Reads the asset: call it off the main thread. */
        fun loadBundled(context: Context): ReleaseNotes? {
            val text = try {
                context.assets.open(ASSET).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                return null
            }
            return parse(text)
        }

        /**
         * The note in [text], the file's JSON, or null when the text is not
         * that shape or carries no note with a title. Blank intros, headings
         * and items are dropped, and so is a section left with nothing.
         */
        fun parse(text: String): ReleaseNotes? {
            val file = try {
                json.decodeFromString(ReleaseNotesJson.serializer(), text)
            } catch (e: IllegalArgumentException) {
                // malformed JSON, a missing field or a wrong type: kotlinx's
                // SerializationException is an IllegalArgumentException
                return null
            }
            val notes = LinkedHashMap<String, ReleaseNote>()
            for ((code, note) in file.locales) {
                if (note.title.isBlank()) {
                    continue
                }
                notes[code] = ReleaseNote(
                    locale = code,
                    title = note.title,
                    intro = note.intro?.takeIf { it.isNotBlank() },
                    sections = note.sections.mapNotNull { section ->
                        val heading = section.heading?.takeIf { it.isNotBlank() }
                        val items = section.items.filter { it.isNotBlank() }
                        if (heading == null && items.isEmpty()) null else ReleaseNoteSection(heading, items)
                    },
                )
            }
            if (notes.isEmpty()) {
                return null
            }
            val date = try {
                LocalDate.parse(file.date)
            } catch (e: DateTimeParseException) {
                null
            }
            return ReleaseNotes(version = file.version, date = date, notes = notes)
        }
    }
}

/** The note in one locale. */
data class ReleaseNote(
    /** The store's BCP 47 code of the note's language ("pt-BR"), which also sets the note's direction. */
    val locale: String,
    val title: String,
    /** A paragraph under the title, if any. */
    val intro: String?,
    /** In order; there may be none. */
    val sections: List<ReleaseNoteSection>,
)

/** A run of bullets, under a heading when it has one. */
data class ReleaseNoteSection(
    val heading: String?,
    val items: List<String>,
)

/** A locale as the note's languages are matched: language, script and region, without extensions. */
private class LanguageKey(val language: String, val script: String, val region: String) {

    /** The same language in the same script; a script only one side names matches either. */
    fun sameLanguage(other: LanguageKey): Boolean =
        language == other.language && (script.isEmpty() || other.script.isEmpty() || script == other.script)

    companion object {
        // Chinese without a script is Traditional in these regions and
        // Simplified (the store's zh) elsewhere
        private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

        fun of(locale: Locale): LanguageKey {
            // java.util.Locale may name Hebrew, Indonesian and Yiddish by their retired codes
            val language = when (val code = locale.language.lowercase(Locale.ROOT)) {
                "iw" -> "he"
                "in" -> "id"
                "ji" -> "yi"
                else -> code
            }
            val region = locale.country.uppercase(Locale.ROOT)
            val script = locale.script.lowercase(Locale.ROOT).ifEmpty {
                when {
                    language != "zh" -> ""
                    region in TRADITIONAL_CHINESE_REGIONS -> "hant"
                    else -> "hans"
                }
            }
            return LanguageKey(language, script, region)
        }
    }
}

// the file's shape (localizations gen/release-notes.mjs)

@Serializable
private class ReleaseNotesJson(
    val version: String,
    val date: String,
    val locales: Map<String, NoteJson>,
)

@Serializable
private class NoteJson(
    val title: String,
    val intro: String? = null,
    val sections: List<SectionJson> = emptyList(),
)

@Serializable
private class SectionJson(
    val heading: String? = null,
    val items: List<String> = emptyList(),
)
