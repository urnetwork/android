package com.bringyour.network.ui.wallet

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * What the sign screen needs of the device: its client id (uuid form), its
 * public client key (`GetSnClientKey()`, "" when not available) and the
 * sdk's `SignSnFleetBinding`, which signs the §11.4 binding digest only and
 * throws on a refusal. No seed or private key passes through here.
 */
interface Top200SignDevice {
    val clientId: String?
    val clientKey: String
    fun signFleetBinding(bindingJson: String): String
}

/** The sign's outcome. */
sealed class Top200SignResult {
    data class Signed(val payload: String) : Top200SignResult()

    /** top200_sign_error_failed, with the sdk's error text */
    data class Failed(val detail: String) : Top200SignResult()
}

data class Top200SignState(
    val clientId: String? = null,
    val clientKey: String = "",
    val text: String = "",
    val check: Top200BindingCheck = Top200BindingCheck.Empty,
    val signing: Boolean = false,
    val result: Top200SignResult? = null,
) {
    /** the copyable manifest member entry, null without a client id or key */
    val memberEntry: String? get() = Top200Bindings.memberEntry(clientId, clientKey)

    /** only this device's binding, reviewed on screen, and not already signing */
    val canSign: Boolean get() = check is Top200BindingCheck.Valid && !signing
}

/**
 * The sign screen's state. Every field edit (a paste, typing, an app link)
 * runs [Top200Bindings.check] and clears the last payload. [sign] signs only
 * the text it judged [Top200BindingCheck.Valid] for this device, judged again
 * at the tap, off the calling thread.
 */
class Top200SignFlow(
    private val signDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var device: Top200SignDevice? = null

    private val _state = MutableStateFlow(Top200SignState())
    val state: StateFlow<Top200SignState> = _state.asStateFlow()

    /** A new device (or none): its id and key, and the field judged again for it. */
    fun setDevice(device: Top200SignDevice?) {
        this.device = device
        val clientId = device?.clientId
        val clientKey = device?.clientKey ?: ""
        _state.update {
            it.copy(
                clientId = clientId,
                clientKey = clientKey,
                check = Top200Bindings.check(it.text, clientId, clientKey),
                result = null,
            )
        }
    }

    /** The field changed. */
    fun setText(text: String) {
        _state.update {
            it.copy(
                text = text,
                check = Top200Bindings.check(text, it.clientId, it.clientKey),
                result = null,
            )
        }
    }

    /**
     * An app link opened the screen: its decoded binding fills the field (and
     * is judged as a paste); a bad link empties the field and shows the
     * invalid error. Never signs.
     */
    fun applyLink(link: Top200SignLink) {
        val text = link.bindingText
        if (text == null) {
            _state.update {
                it.copy(text = "", check = Top200BindingCheck.Invalid, result = null)
            }
        } else {
            setText(text)
        }
    }

    /** Sign with this device, after the review the screen shows for a valid check. */
    suspend fun sign() {
        val d = device ?: return
        val current = _state.value
        if (current.signing) {
            return
        }
        // judged again against the device as it is now; only a valid binding is signed
        val check = Top200Bindings.check(current.text, d.clientId, d.clientKey)
        if (check !is Top200BindingCheck.Valid) {
            _state.update { it.copy(check = check) }
            return
        }
        _state.update { it.copy(signing = true, result = null) }
        val result = withContext(signDispatcher) {
            try {
                val signature = d.signFleetBinding(check.text)
                if (signature.isEmpty()) {
                    Top200SignResult.Failed("empty signature")
                } else {
                    Top200SignResult.Signed(Top200Bindings.payload(check.text, signature))
                }
            } catch (e: Exception) {
                Top200SignResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        _state.update {
            // an edit while signing judged other text: that payload is not for it
            if (it.text == current.text && device === d) {
                it.copy(signing = false, result = result)
            } else {
                it.copy(signing = false)
            }
        }
    }
}

// ---- the app link

/** An app link for the sign screen: the binding it carries, null when the link was bad. */
data class Top200SignLink(val bindingText: String?)

/**
 * Hands an app link from the LoginActivity (where `ur://` arrives) to the
 * main activity's sign screen, which takes it once. Held for the process,
 * like [SnConsentReturns].
 */
object Top200SignLinks {
    private val _pending = MutableStateFlow<Top200SignLink?>(null)
    val pending: StateFlow<Top200SignLink?> = _pending.asStateFlow()

    // the nav host opens the sign screen once per delivered link
    private val _navigation = MutableStateFlow(false)
    val navigation: StateFlow<Boolean> = _navigation.asStateFlow()

    fun deliver(link: Top200SignLink) {
        _pending.value = link
        _navigation.value = true
    }

    /** The waiting link, taken once. */
    fun take(): Top200SignLink? {
        val link = _pending.value
        _pending.value = null
        return link
    }

    fun consumeNavigation() {
        _navigation.value = false
    }
}

/**
 * Hands a signed-in device's app link over and brings the main activity up.
 * The `binding` parameter is decoded here, so a bad link reaches the screen
 * as one. A link that arrives while signed out is dropped by the caller.
 */
fun forwardTop200SignLink(activity: Activity, bindingParameter: String?, mainActivity: Class<*>) {
    Top200SignLinks.deliver(Top200SignLink(Top200Bindings.decodeLinkBinding(bindingParameter)))
    val intent = Intent(activity, mainActivity)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    activity.startActivity(intent)
    activity.finish()
}
