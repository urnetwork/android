package com.bringyour.network.ui.account

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.CreateApiKeyArgs
import com.bringyour.sdk.DeleteApiKeyArgs
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ApiKeysViewModel"

/**
 * Account -> API keys: the network's API keys from the sdk's api calls
 * (ListApiKeys, CreateApiKey, DeleteApiKey), made on the device's account api.
 *
 * Binds [ApiKeysScreenModel] to the app: the device (a replaced device drops
 * the former account's list, dialogs and answers) and the screen's visibility
 * (LifecycleStartEffect in ApiKeysScreen). The answers arrive on sdk threads
 * and are posted to the main thread.
 *
 * A new key lives only in the model's dialog, in this view model's memory: it
 * survives a rotation, never the process, and is never saved, logged or put in
 * the saved state.
 */
@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    deviceManager: DeviceManager,
) : ViewModel() {

    private val model = ApiKeysScreenModel<DeviceLocal>(
        openClient = { device -> SdkApiKeysClient(device) },
        post = { block -> viewModelScope.launch { block() } },
    )
    private var removeDeviceChangeListener: (() -> Unit)? = null

    val ui: ApiKeysUi get() = model.ui
    val dialog: ApiKeysDialog? get() = model.dialog

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                model.setDevice(device)
            }
        }
    }

    fun setVisible(visible: Boolean) = model.setVisible(visible)

    fun refresh() = model.refresh()

    fun requestCreate() = model.requestCreate()

    fun create(nameText: String) = model.create(nameText)

    fun requestDelete(apiKeyId: String) = model.requestDelete(apiKeyId)

    fun confirmDelete() = model.confirmDelete()

    fun dismissDialog() = model.dismissDialog()

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        model.close()
        super.onCleared()
    }
}

/** A call the server answered with its error, whose message the screen never shows. */
private class ApiKeysException(message: String) : Exception(message)

/**
 * ApiKeysClient over the device's account api. Each answer is copied off its
 * gomobile objects on the sdk thread it arrives on, inside runCatching: an
 * exception thrown out of a gomobile callback would abort the process.
 *
 * Only errors are logged, by their message. An answer never is: a create's
 * answer holds the new key, and its gomobile toString prints every field.
 */
private class SdkApiKeysClient(
    private val device: DeviceLocal,
) : ApiKeysClient {

    override fun list(done: (Result<List<ApiKeyEntry>>) -> Unit) {
        val api = device.api ?: return done(Result.failure(IllegalStateException("no api")))
        api.listApiKeys { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    checkNotNull(result) { "no answer" }
                    result.error?.let { throw ApiKeysException(it.message.orEmpty()) }
                    // the server sends no list for a network without keys
                    result.apiKeys?.let { list ->
                        (0 until list.len()).mapNotNull { i ->
                            list.get(i)?.let { key ->
                                ApiKeyEntry(
                                    apiKeyId = key.id?.idStr.orEmpty(),
                                    name = key.name.orEmpty(),
                                    // a zero time is no time
                                    createTimeMillis = key.createTime?.unixMilli()?.takeIf { 0 < it },
                                )
                            }
                        }
                    } ?: listOf()
                }.onFailure { Log.i(TAG, "list api keys failed: ${it.message}") }
            )
        }
    }

    override fun create(name: String, done: (Result<CreatedApiKey>) -> Unit) {
        val api = device.api ?: return done(Result.failure(IllegalStateException("no api")))
        val args = CreateApiKeyArgs()
        args.name = name
        api.createApiKey(args) { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    checkNotNull(result) { "no answer" }
                    result.error?.let { throw ApiKeysException(it.message.orEmpty()) }
                    CreatedApiKey(
                        apiKeyId = result.id?.idStr.orEmpty(),
                        name = result.name.orEmpty(),
                        apiKey = result.apiKey.orEmpty(),
                    )
                }.onFailure { Log.i(TAG, "create api key failed: ${it.message}") }
            )
        }
    }

    override fun delete(apiKeyId: String, done: (Result<Unit>) -> Unit) {
        val api = device.api ?: return done(Result.failure(IllegalStateException("no api")))
        val id = try {
            Sdk.parseId(apiKeyId)
        } catch (e: Exception) {
            Log.w(TAG, "not an api key id", e)
            return done(Result.failure(e))
        }
        val args = DeleteApiKeyArgs()
        args.id = id
        api.deleteApiKey(args) { result, err ->
            done(
                runCatching {
                    if (err != null) {
                        throw err
                    }
                    result?.error?.let { throw ApiKeysException(it.message.orEmpty()) }
                    Unit
                }.onFailure { Log.i(TAG, "delete api key failed: ${it.message}") }
            )
        }
    }
}
