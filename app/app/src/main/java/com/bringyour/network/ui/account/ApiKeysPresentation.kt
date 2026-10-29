package com.bringyour.network.ui.account

import androidx.annotation.StringRes
import com.bringyour.network.R
import com.bringyour.network.ui.indexedLazyListKey

/*
 * Account -> API keys, as data: the network's keys copied off the sdk's
 * gomobile objects (sdk api.go ListApiKeys), and what the screen shows for
 * them. Everything here is plain data and functions, so the JVM tests cover it
 * without the sdk's native library; ApiKeysViewModel copies the gomobile
 * results in and ApiKeysScreen renders the result.
 *
 * A key's secret is never part of this data. The server shows it once, in the
 * answer to its create, and ApiKeysScreenModel keeps it only in the dialog
 * that shows it (ApiKeysDialog.Created) until that dialog closes.
 *
 * Errors are carried only as flags: the screen never shows the server's
 * message.
 */

/** The api reference the intro links to. */
const val API_KEYS_DOCS_URL = "https://ur.io/docs/api"

// account_api_key.name is varchar(128) on the server, which counts characters
// (code points); a create with a longer name is refused
internal const val API_KEY_NAME_MAX_LENGTH = 128

/**
 * The name field's text after an edit. A name is one line, so control
 * characters (a pasted line break, or a NUL, which the server refuses) are
 * dropped, and it stops at the server's limit, so no create is refused for its
 * length.
 */
internal fun apiKeyNameInput(text: String): String {
    val printable = text.filterNot { Character.isISOControl(it) }
    if (printable.codePointCount(0, printable.length) <= API_KEY_NAME_MAX_LENGTH) {
        return printable
    }
    return printable.substring(0, printable.offsetByCodePoints(0, API_KEY_NAME_MAX_LENGTH))
}

/**
 * The name a create sends: the field's text without its outer spaces. Null
 * when that leaves nothing: the app always names a key, though one created
 * through the api may have no name.
 */
internal fun apiKeyCreateName(text: String): String? = apiKeyNameInput(text).trim().ifEmpty { null }

/** The sdk's PublicAccountApiKey: what identifies a key, never the key. */
data class ApiKeyEntry(
    val apiKeyId: String,
    // empty for a key created through the api without one
    val name: String,
    // CreateTime in Unix millis, null when the server sent none
    val createTimeMillis: Long?,
)

/** The list as ApiKeysScreenModel keeps it. */
data class ApiKeysState(
    // in the server's order, which is none; the screen sorts them
    val keys: List<ApiKeyEntry>,
    // a list arrived since the screen opened on this account
    val loaded: Boolean,
    val loading: Boolean,
    // the last load failed
    val loadFailed: Boolean,
    // the keys whose delete runs
    val deletingIds: Set<String>,
    // the keys whose last delete failed
    val deleteFailedIds: Set<String>,
) {
    companion object {
        // nothing loaded yet: the screen just opened, or the account changed
        val Initial = ApiKeysState(
            keys = listOf(),
            loaded = false,
            loading = false,
            loadFailed = false,
            deletingIds = setOf(),
            deleteFailedIds = setOf(),
        )
    }
}

/** What the screen shows in place of, or around, the rows, with the message it shows in their place. */
enum class ApiKeysBody(@get:StringRes val messageRes: Int?) {
    // never loaded
    Progress(null),
    // the first load failed: the message and Try again
    LoadFailed(R.string.api_keys_load_failed),
    // loaded, and no keys
    Empty(R.string.api_keys_empty),
    Rows(null),
}

/** One key's row, as shown. */
data class ApiKeyRowUi(
    // unique per row, for the lazy list
    val key: String,
    // the full id, which the row shows and the delete sends
    val apiKeyId: String,
    // empty when the key has none: the row says Unnamed key
    val name: String,
    val createTimeMillis: Long?,
    // its delete runs: progress, and no control
    val deleting: Boolean,
    // its last delete failed; its controls stay for a retry
    val deleteFailed: Boolean,
)

data class ApiKeysUi(
    val body: ApiKeysBody,
    // newest first
    val rows: List<ApiKeyRowUi>,
    // a load over the shown list (an appearance, a pull, a change): the pull indicator
    val refreshing: Boolean,
    // a load failed and the rows are the last list
    val refreshFailed: Boolean,
) {
    companion object {
        val Initial = apiKeysUi(ApiKeysState.Initial)
    }
}

/**
 * Newest first: by creation time, then by id, which orders by creation too
 * (a ULID); keys without a time come last.
 */
internal fun apiKeysInDisplayOrder(keys: List<ApiKeyEntry>): List<ApiKeyEntry> = keys.sortedWith(
    compareBy<ApiKeyEntry> { it.createTimeMillis == null }
        .thenByDescending { it.createTimeMillis }
        .thenByDescending { it.apiKeyId }
)

/**
 * Lazy list keys: the key's id when it names one row, so a row's swipe state
 * stays with its key when another row goes; otherwise unique by index.
 */
internal fun apiKeyRowKeys(apiKeyIds: List<String>): List<String> {
    val counts = apiKeyIds.groupingBy { it }.eachCount()
    return apiKeyIds.mapIndexed { index, apiKeyId ->
        if (apiKeyId.isNotEmpty() && counts[apiKeyId] == 1) {
            "api-key:$apiKeyId"
        } else {
            indexedLazyListKey("api-key", index, apiKeyId)
        }
    }
}

/** The screen for the model's state. */
fun apiKeysUi(state: ApiKeysState): ApiKeysUi {
    val keys = apiKeysInDisplayOrder(state.keys)
    val rowKeys = apiKeyRowKeys(keys.map { it.apiKeyId })
    val rows = keys.mapIndexed { index, entry ->
        val deleting = entry.apiKeyId in state.deletingIds
        ApiKeyRowUi(
            key = rowKeys[index],
            apiKeyId = entry.apiKeyId,
            name = entry.name,
            createTimeMillis = entry.createTimeMillis,
            deleting = deleting,
            deleteFailed = !deleting && entry.apiKeyId in state.deleteFailedIds,
        )
    }
    val body = when {
        // a retry of a failed first load shows progress again
        !state.loaded && state.loading -> ApiKeysBody.Progress
        !state.loaded && state.loadFailed -> ApiKeysBody.LoadFailed
        !state.loaded -> ApiKeysBody.Progress
        rows.isEmpty() -> ApiKeysBody.Empty
        else -> ApiKeysBody.Rows
    }
    val listed = body == ApiKeysBody.Rows || body == ApiKeysBody.Empty
    return ApiKeysUi(
        body = body,
        rows = if (body == ApiKeysBody.Rows) rows else listOf(),
        // the progress body is the first load's own indicator
        refreshing = listed && state.loading,
        refreshFailed = listed && state.loadFailed,
    )
}

/**
 * The localized pieces of the screen's text: catalog strings, and the
 * platform's formats of a time in the user's zone (ApiKeysScreen's are
 * Context.getString and DateUtils).
 */
interface ApiKeysText {
    fun string(@StringRes id: Int, vararg args: Any): String

    // a creation time: the date and time, with the year when it is not this year
    fun created(timeMillis: Long): String

    // the full date and time, which assistive tech reads in place of the above
    fun dateTime(timeMillis: Long): String
}

/**
 * A row's lines, and what assistive tech reads for the created time, which
 * has the full date and time.
 */
data class ApiKeyRowText(
    // the name, or Unnamed key
    val name: String,
    // null when the server sent no time
    val created: String?,
    val spokenCreated: String?,
    // the full id
    val id: String,
)

/** A key's name as shown: a key created through the api may have none. */
fun apiKeyDisplayName(name: String, text: ApiKeysText): String =
    name.ifBlank { text.string(R.string.api_keys_unnamed) }

fun apiKeyRowText(row: ApiKeyRowUi, text: ApiKeysText): ApiKeyRowText = ApiKeyRowText(
    name = apiKeyDisplayName(row.name, text),
    created = row.createTimeMillis?.let { text.string(R.string.api_keys_created_at, text.created(it)) },
    spokenCreated = row.createTimeMillis?.let { text.string(R.string.api_keys_created_at, text.dateTime(it)) },
    id = text.string(R.string.api_keys_id, row.apiKeyId),
)

/** The body of the confirmation before a key's delete, which names the key. */
fun apiKeyDeleteConfirmBody(row: ApiKeyRowUi, text: ApiKeysText): String =
    text.string(R.string.api_keys_delete_confirm_body, apiKeyDisplayName(row.name, text))
