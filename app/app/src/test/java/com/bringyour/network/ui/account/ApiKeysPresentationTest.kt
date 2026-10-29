package com.bringyour.network.ui.account

import com.bringyour.network.R
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Account -> API keys as data: the body states, the order of the rows, a
 * row's lines in English, the unnamed key, the delete confirmation, and the
 * rules of the name field. The platform's date formats are replaced by tagged
 * stand-ins so the tests show which time feeds which text.
 */
class ApiKeysPresentationTest {

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

        // the english catalog, by resource name
        private val english: Map<String, String> by lazy {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(moduleFile("src/main/res/values/strings.xml")).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }
                .associate { it.getAttribute("name") to it.textContent.replace("\\'", "'").replace("\\\"", "\"") }
        }

        private val stringNames: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        private fun name(id: Int) = stringNames[id] ?: error("no string resource $id")
    }

    /** The english strings, with the dates and times as tagged stand-ins. */
    private object EnglishText : ApiKeysText {
        override fun string(id: Int, vararg args: Any): String =
            String.format(Locale.ROOT, english[name(id)] ?: error("no english ${name(id)}"), *args)

        override fun created(timeMillis: Long) = "<date $timeMillis>"

        override fun dateTime(timeMillis: Long) = "<date and time $timeMillis>"
    }

    private fun key(
        index: Int,
        name: String = "home server",
        createTimeMillis: Long? = 1_799_000_000_000L + index * 1000L,
    ) = ApiKeyEntry(
        apiKeyId = "01a1f3c2-0000-4000-8000-00000000000$index",
        name = name,
        createTimeMillis = createTimeMillis,
    )

    private fun loaded(
        vararg keys: ApiKeyEntry,
        deletingIds: Set<String> = setOf(),
        deleteFailedIds: Set<String> = setOf(),
    ) = ApiKeysState.Initial.copy(
        keys = keys.toList(),
        loaded = true,
        deletingIds = deletingIds,
        deleteFailedIds = deleteFailedIds,
    )

    private fun row(state: ApiKeysState, index: Int = 0) = apiKeysUi(state).rows[index]

    @Test
    fun theBodyFollowsTheState() {
        // never loaded, and the first load
        assertEquals(ApiKeysBody.Progress, apiKeysUi(ApiKeysState.Initial).body)
        val loading = ApiKeysState.Initial.copy(loading = true)
        assertEquals(ApiKeysBody.Progress, apiKeysUi(loading).body)
        // the progress body is the first load's own indicator
        assertFalse(apiKeysUi(loading).refreshing)

        // a failed first load, and its retry
        val failed = ApiKeysState.Initial.copy(loadFailed = true)
        assertEquals(ApiKeysBody.LoadFailed, apiKeysUi(failed).body)
        assertFalse(apiKeysUi(failed).refreshFailed)
        assertEquals(ApiKeysBody.Progress, apiKeysUi(failed.copy(loading = true, loadFailed = false)).body)

        // loaded
        assertEquals(ApiKeysBody.Empty, apiKeysUi(loaded()).body)
        val rows = apiKeysUi(loaded(key(0), key(1)))
        assertEquals(ApiKeysBody.Rows, rows.body)
        assertEquals(2, rows.rows.size)
        assertEquals(ApiKeysUi.Initial, apiKeysUi(ApiKeysState.Initial))
    }

    @Test
    fun aReloadKeepsTheListAndAFailedOneSaysSo() {
        val state = loaded(key(0), key(1))
        val reloading = apiKeysUi(state.copy(loading = true))
        assertTrue(reloading.refreshing)
        assertEquals(2, reloading.rows.size)
        assertFalse(reloading.refreshFailed)

        val failed = apiKeysUi(state.copy(loadFailed = true))
        assertEquals(ApiKeysBody.Rows, failed.body)
        assertEquals(2, failed.rows.size)
        assertTrue(failed.refreshFailed)
        assertFalse(failed.refreshing)

        // over the empty list too
        assertTrue(apiKeysUi(loaded().copy(loadFailed = true)).refreshFailed)
        assertTrue(apiKeysUi(loaded().copy(loading = true)).refreshing)
    }

    @Test
    fun everyBodyShowsItsOwnMessage() {
        val expected = mapOf(
            ApiKeysBody.Progress to null,
            ApiKeysBody.LoadFailed to "Couldn't load your API keys. Try again.",
            ApiKeysBody.Empty to "You don't have any API keys.",
            ApiKeysBody.Rows to null,
        )
        assertEquals(ApiKeysBody.entries.toSet(), expected.keys)
        for ((body, message) in expected) {
            assertEquals("$body", message, body.messageRes?.let { EnglishText.string(it) })
        }
    }

    @Test
    fun theNewestKeyComesFirst() {
        val ui = apiKeysUi(loaded(key(1), key(3), key(2), key(0)))
        assertEquals(listOf(3, 2, 1, 0).map { key(it).apiKeyId }, ui.rows.map { it.apiKeyId })

        // the same time goes by id, which orders by creation too; no time comes last
        val sameTime = apiKeysUi(
            loaded(
                key(4, createTimeMillis = null),
                key(1, createTimeMillis = 1_799_000_000_000L),
                key(2, createTimeMillis = 1_799_000_000_000L),
            )
        )
        assertEquals(listOf(2, 1, 4).map { key(it).apiKeyId }, sameTime.rows.map { it.apiKeyId })
    }

    @Test
    fun rowKeysStayWithTheirKey() {
        val keys = apiKeyRowKeys(listOf("a", "b", "c"))
        assertEquals(listOf("api-key:a", "api-key:b", "api-key:c"), keys)
        // the key of b is the same once a is gone, so b keeps its swipe state
        assertEquals(listOf("api-key:b", "api-key:c"), apiKeyRowKeys(listOf("b", "c")))

        // duplicate and missing ids stay unique
        val odd = apiKeyRowKeys(listOf("a", "a", "", ""))
        assertEquals(odd.size, odd.toSet().size)
    }

    @Test
    fun theLinesOfARow() {
        val text = apiKeyRowText(row(loaded(key(0))), EnglishText)
        assertEquals("home server", text.name)
        assertEquals("Created <date 1799000000000>", text.created)
        // assistive tech hears the full date and time
        assertEquals("Created <date and time 1799000000000>", text.spokenCreated)
        // the full id, which the api's remove takes
        assertEquals("ID 01a1f3c2-0000-4000-8000-000000000000", text.id)
    }

    @Test
    fun aKeyWithoutATimeHasNoCreatedLine() {
        val text = apiKeyRowText(row(loaded(key(0, createTimeMillis = null))), EnglishText)
        assertNull(text.created)
        assertNull(text.spokenCreated)
        assertEquals("ID 01a1f3c2-0000-4000-8000-000000000000", text.id)
    }

    @Test
    fun aKeyWithoutANameIsTheUnnamedKey() {
        // keys created through the api can have none
        for (blank in listOf("", "   ")) {
            val unnamed = row(loaded(key(0, name = blank)))
            assertEquals("Unnamed key", apiKeyRowText(unnamed, EnglishText).name)
            assertEquals(
                "\"Unnamed key\" will stop working right away, and anything using it will lose access.",
                apiKeyDeleteConfirmBody(unnamed, EnglishText),
            )
        }
    }

    @Test
    fun theConfirmationNamesTheKey() {
        val named = row(loaded(key(0, name = "ci runner")))
        assertEquals(
            "\"ci runner\" will stop working right away, and anything using it will lose access.",
            apiKeyDeleteConfirmBody(named, EnglishText),
        )
        assertEquals("Delete this API key?", EnglishText.string(R.string.api_keys_delete_confirm_title))
        assertEquals("Delete", EnglishText.string(R.string.api_keys_delete))
    }

    @Test
    fun longNamesStayWhole() {
        // the row wraps; nothing is cut
        val long = "n".repeat(API_KEY_NAME_MAX_LENGTH)
        assertEquals(long, apiKeyRowText(row(loaded(key(0, name = long))), EnglishText).name)
    }

    @Test
    fun aRunningDeleteShowsProgressAndAFailedOneShowsItsError() {
        val keys = arrayOf(key(3), key(2), key(1))
        val ui = apiKeysUi(
            loaded(
                *keys,
                deletingIds = setOf(key(2).apiKeyId),
                // a failure of a delete that runs again is not shown
                deleteFailedIds = setOf(key(1).apiKeyId, key(2).apiKeyId),
            )
        )
        assertEquals(listOf(false, true, false), ui.rows.map { it.deleting })
        assertEquals(listOf(false, false, true), ui.rows.map { it.deleteFailed })
        assertEquals("Couldn't delete the API key. Try again.", EnglishText.string(R.string.api_keys_delete_failed))
    }

    @Test
    fun theNameFieldDropsControlCharactersAndStopsAtTheServersLimit() {
        // one line: a pasted line break and a NUL, which the server refuses
        assertEquals("home server", apiKeyNameInput("home\n server\u0000"))
        assertEquals("tabbed", apiKeyNameInput("tab\tbed"))

        val longest = "n".repeat(API_KEY_NAME_MAX_LENGTH)
        assertEquals(longest, apiKeyNameInput(longest))
        assertEquals(longest, apiKeyNameInput(longest + "nnn"))

        // the server counts characters, not utf-16 units: a character outside
        // the basic plane counts once and is never cut in half
        val emoji = "🔑"
        val emojis = emoji.repeat(API_KEY_NAME_MAX_LENGTH)
        assertEquals(emojis, apiKeyNameInput(emojis))
        assertEquals(emojis, apiKeyNameInput(emojis + emoji))
        val cut = apiKeyNameInput("n" + emojis)
        assertEquals(API_KEY_NAME_MAX_LENGTH, cut.codePointCount(0, cut.length))
        assertFalse(Character.isHighSurrogate(cut.last()))
        assertEquals(128, API_KEY_NAME_MAX_LENGTH)
    }

    @Test
    fun theNameSentIsTrimmedAndNeverBlank() {
        assertEquals("home server", apiKeyCreateName("  home server  "))
        assertEquals("a b", apiKeyCreateName("a b"))
        assertNull(apiKeyCreateName(""))
        assertNull(apiKeyCreateName("   "))
        assertNull(apiKeyCreateName("\n"))
    }

    @Test
    fun theIntroLinksToTheApiReference() {
        assertEquals("https://ur.io/docs/api", API_KEYS_DOCS_URL)
        assertEquals("How to use API keys", EnglishText.string(R.string.api_keys_docs_link))
    }

    @Test
    fun theNewKeysWarningSaysItIsShownOnceAndGivesAccess() {
        val warning = EnglishText.string(R.string.api_keys_created_warning)
        assertTrue(warning, warning.contains("won't be shown again"))
        assertTrue(warning, warning.contains("can use your account"))
    }
}
