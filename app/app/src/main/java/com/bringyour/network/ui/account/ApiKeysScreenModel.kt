package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The sdk calls Account -> API keys makes (sdk api.go ListApiKeys,
 * CreateApiKey and DeleteApiKey) on the device's account api. Each call
 * answers once, on an sdk thread.
 */
internal interface ApiKeysClient {
    fun list(done: (Result<List<ApiKeyEntry>>) -> Unit)

    // the answer holds the new key, which the server shows only this once
    fun create(name: String, done: (Result<CreatedApiKey>) -> Unit)

    fun delete(apiKeyId: String, done: (Result<Unit>) -> Unit)
}

/** A create's answer. toString leaves the key out, so no log or crash report prints it. */
internal class CreatedApiKey(
    val apiKeyId: String,
    val name: String,
    val apiKey: String,
) {
    override fun toString() = "CreatedApiKey(apiKeyId=$apiKeyId, name=$name)"
}

/** The dialog over the screen; one at a time. */
sealed interface ApiKeysDialog {
    /**
     * The name form. While [creating] it cannot be dismissed: the answer holds
     * the new key, which a dismissed form would lose. [failed] after a create
     * failed; Create tries again.
     */
    data class Create(val creating: Boolean, val failed: Boolean) : ApiKeysDialog

    /**
     * The new key, shown once. The model holds the key only here and drops it
     * when the dialog closes; toString leaves it out.
     */
    class Created(val name: String, val apiKey: String) : ApiKeysDialog {
        override fun toString() = "Created(name=$name)"
    }

    /** The confirmation before a delete, with the row as it was when the user chose it. */
    data class ConfirmDelete(val row: ApiKeyRowUi) : ApiKeysDialog
}

/**
 * Account -> API keys: the network's keys, with create and delete. Owns the
 * screen's state and the sdk calls behind it. Main-thread confined;
 * ApiKeysViewModel binds it to the device and the screen.
 *
 * Each appearance of the screen loads the list again, since keys also change
 * on ur.io and the other apps, and so do a pull, Try again and every create or
 * delete. Only the latest load's answer applies.
 *
 * The calls answer on sdk threads. [post] hands each answer to the main
 * thread, where it applies only while the account it was asked for is still
 * the current one: another device (an account switch, a sign out) clears the
 * list, closes any dialog, which drops a new key not yet dismissed, and
 * nothing that account asked for applies after. So does the model's close.
 *
 * A create's key goes only into ApiKeysDialog.Created, never into the list's
 * state, and the dialog's close drops the model's one reference to it.
 */
internal class ApiKeysScreenModel<D : Any>(
    private val openClient: (D) -> ApiKeysClient,
    private val post: (() -> Unit) -> Unit,
) {
    var ui by mutableStateOf(ApiKeysUi.Initial)
        private set

    var dialog by mutableStateOf<ApiKeysDialog?>(null)
        private set

    private var state = ApiKeysState.Initial

    private var device: D? = null
    private var client: ApiKeysClient? = null

    // changes with the device and at the close: an answer asked for before is dropped
    private var generation = 0L

    // the latest load; an earlier load's answer is dropped
    private var loadSequence = 0L

    private var visible = false
    private var closed = false

    /** The device whose account api the calls go to. */
    fun setDevice(nextDevice: D?) {
        if (closed || device === nextDevice) {
            return
        }
        device = nextDevice
        client = nextDevice?.let(openClient)
        generation++
        // the former account's keys, its confirmation and its new key go with it
        dialog = null
        apply(ApiKeysState.Initial)
        if (visible) {
            load()
        }
    }

    /** Whether the screen shows; each appearance loads the list. */
    fun setVisible(nextVisible: Boolean) {
        if (closed) {
            return
        }
        val appeared = nextVisible && !visible
        visible = nextVisible
        if (appeared) {
            load()
        }
    }

    /** Pull to refresh, and Try again. */
    fun refresh() {
        if (closed) {
            return
        }
        load()
    }

    /** Create API key: opens the name form. */
    fun requestCreate() {
        if (client == null || dialog != null) {
            return
        }
        dialog = ApiKeysDialog.Create(creating = false, failed = false)
    }

    /**
     * The form's Create, with the name field's text. A blank name sends
     * nothing, and neither does a create that already runs.
     */
    fun create(nameText: String) {
        val opened = client ?: return
        val form = dialog as? ApiKeysDialog.Create ?: return
        if (form.creating) {
            return
        }
        val name = apiKeyCreateName(nameText) ?: return
        val creatingForm = ApiKeysDialog.Create(creating = true, failed = false)
        dialog = creatingForm
        val requestGeneration = generation
        opened.create(name) { result ->
            post {
                if (generation != requestGeneration) {
                    return@post
                }
                // the form cannot be dismissed while it waits, so it is still open
                if (dialog === creatingForm) {
                    val created = result.getOrNull()?.takeIf { it.apiKey.isNotEmpty() }
                    dialog = if (created != null) {
                        ApiKeysDialog.Created(name = created.name.ifEmpty { name }, apiKey = created.apiKey)
                    } else {
                        ApiKeysDialog.Create(creating = false, failed = true)
                    }
                }
                // the new key's row; after a failure too, in case the server made it
                load()
            }
        }
    }

    /**
     * Cancel, Done, back or a tap outside. The new key's dialog drops the key
     * as it closes. A form whose create runs stays: its answer holds the key.
     */
    fun dismissDialog() {
        val current = dialog ?: return
        if (current is ApiKeysDialog.Create && current.creating) {
            return
        }
        dialog = null
    }

    /** A row's Delete: asks first, naming the key. */
    fun requestDelete(apiKeyId: String) {
        if (client == null || dialog != null) {
            return
        }
        val row = ui.rows.firstOrNull { it.apiKeyId == apiKeyId } ?: return
        if (row.deleting) {
            return
        }
        dialog = ApiKeysDialog.ConfirmDelete(row)
    }

    /** The confirmation's Delete. A delete that already runs is not sent again. */
    fun confirmDelete() {
        val target = dialog as? ApiKeysDialog.ConfirmDelete ?: return
        dialog = null
        val opened = client ?: return
        val apiKeyId = target.row.apiKeyId
        if (apiKeyId in state.deletingIds) {
            return
        }
        apply(
            state.copy(
                deletingIds = state.deletingIds + apiKeyId,
                deleteFailedIds = state.deleteFailedIds - apiKeyId,
            )
        )
        val requestGeneration = generation
        opened.delete(apiKeyId) { result ->
            post {
                if (generation != requestGeneration) {
                    return@post
                }
                if (result.isSuccess) {
                    apply(
                        state.copy(
                            keys = state.keys.filter { it.apiKeyId != apiKeyId },
                            deletingIds = state.deletingIds - apiKeyId,
                        )
                    )
                    // a load that started before the delete may still list the key
                    load()
                } else {
                    apply(
                        state.copy(
                            deletingIds = state.deletingIds - apiKeyId,
                            deleteFailedIds = state.deleteFailedIds + apiKeyId,
                        )
                    )
                }
            }
        }
    }

    /** The screen is gone: nothing updates after, and any new key is dropped. */
    fun close() {
        if (closed) {
            return
        }
        closed = true
        generation++
        visible = false
        device = null
        client = null
        dialog = null
        apply(ApiKeysState.Initial)
    }

    private fun load() {
        val opened = client ?: return
        val requestGeneration = generation
        val sequence = ++loadSequence
        apply(state.copy(loading = true, loadFailed = false))
        opened.list { result ->
            post {
                if (generation != requestGeneration || loadSequence != sequence) {
                    return@post
                }
                val keys = result.getOrNull()
                if (keys != null) {
                    val apiKeyIds = keys.map { it.apiKeyId }.toSet()
                    apply(
                        state.copy(
                            keys = keys,
                            loaded = true,
                            loading = false,
                            loadFailed = false,
                            // a failed delete of a key that is gone has nothing to show
                            deleteFailedIds = state.deleteFailedIds intersect apiKeyIds,
                        )
                    )
                } else {
                    apply(state.copy(loading = false, loadFailed = true))
                }
            }
        }
    }

    private fun apply(next: ApiKeysState) {
        state = next
        ui = apiKeysUi(next)
    }
}
