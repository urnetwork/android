package com.bringyour.network.ui.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.utils.listToSdkStringList
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.NetworkSpace
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The bootstrap DNS-over-HTTPS servers of the network space: servers the
 * space resolves its own names (api, connect, extender) through ahead of the
 * default DoH servers, for networks that block those. Account > Extenders
 * edits them for the signed-in device's space, and the login screen's network
 * settings for the active space before there is a device -- a fresh install
 * on such a network cannot sign in without them.
 *
 * The sdk stores the list in the space's values and applies a save in place:
 * the client strategy's DoH settings are swapped while the space, a device
 * bound to it and this screen stay valid. The VPN service runs in the app's
 * process on the device bound to this same space, so the client windows take
 * a save at once and there is no "next time it connects" to tell the user.
 * The swap closes the old DoH cache, which waits for its requests in flight,
 * so the write runs off the main thread.
 *
 * The field logic is ControlDohLogic.kt.
 */
@HiltViewModel
class ControlDohSettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider,
) : ViewModel() {

    /** The field's text, one url per line. */
    var text by mutableStateOf("")
        private set

    /** Whether there is a space to edit. False until the servers load. */
    var editable by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set

    /** The first line's problem (`Sdk.validateControlDohUrl`), empty when every line validates. */
    var validationErrorId by mutableStateOf("")
        private set

    /** What the last save answered, until the text changes. */
    var saveErrorId by mutableStateOf("")
        private set

    private var loadJob: Job? = null

    init {
        load()
    }

    /**
     * Fills the field with the space's servers, empty when it names none,
     * which is the default servers alone. The screens call this each time they
     * show the field, since an import or the other screen may have changed the
     * list meanwhile.
     */
    fun load() {
        val space = networkSpace()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val urls = withContext(Dispatchers.IO) {
                space?.let { sdkStringListToList(it.controlDohUrls) }
            }
            editable = urls != null
            saveErrorId = ""
            replaceText(controlDohText(urls ?: listOf()))
        }
    }

    /** Edits the field. The last save's answer no longer applies to it. */
    fun update(text: String) {
        saveErrorId = ""
        replaceText(text)
    }

    /**
     * "Use China resolvers": the preset replaces the field, one server per
     * line. Nothing is saved until the user reviews it and taps Save.
     */
    fun useChinaPreset() {
        update(controlDohText(sdkStringListToList(Sdk.regionalControlDohUrls(CONTROL_DOH_PRESET_CHINA))))
    }

    /**
     * Saves the field's servers. Every line must validate, otherwise nothing is
     * saved and the error id shows under the field, which keeps the text as
     * typed. On success the field shows the list the space read back, which is
     * normalized, without repeats, v4 first.
     */
    fun save(onSaved: () -> Unit) {
        saveUrls(text, controlDohLinesFromText(text), onSaved)
    }

    /** "Use built-in servers only": clears the field and saves the empty list at once. */
    fun reset(onSaved: () -> Unit) {
        if (saving) {
            return
        }
        update("")
        saveUrls("", listOf(), onSaved)
    }

    private fun saveUrls(savedText: String, urls: List<String>, onSaved: () -> Unit) {
        if (saving) {
            return
        }
        val space = networkSpace() ?: return
        saving = true
        viewModelScope.launch {
            try {
                val (errorId, stored) = withContext(Dispatchers.IO) {
                    val errorId = space.setControlDohUrls(listToSdkStringList(urls))
                    val stored = if (errorId.isEmpty()) {
                        sdkStringListToList(space.controlDohUrls)
                    } else {
                        null
                    }
                    errorId to stored
                }
                // an edit made meanwhile is kept, unsaved, and the answer for
                // the text before it is not shown over it
                if (text != savedText) {
                    return@launch
                }
                saveErrorId = errorId
                if (errorId.isEmpty()) {
                    stored?.let { replaceText(controlDohText(it)) }
                    onSaved()
                }
            } finally {
                saving = false
            }
        }
    }

    private fun replaceText(next: String) {
        text = next
        validationErrorId = controlDohValidationErrorId(controlDohLinesFromText(next)) { line ->
            Sdk.validateControlDohUrl(line)
        }
    }

    // the space a signed-in device is bound to, else the active space (the
    // login screen, before there is a device)
    private fun networkSpace(): NetworkSpace? =
        deviceManager.device?.networkSpace ?: networkSpaceManagerProvider.getNetworkSpace()
}
