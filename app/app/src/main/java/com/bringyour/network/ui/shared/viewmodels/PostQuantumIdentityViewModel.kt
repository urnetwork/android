package com.bringyour.network.ui.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.ui.components.renderIdenticon
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.PostQuantumIdentityViewController
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The canonical identity key hash display rule, shared by every platform:
 * split the 52-char hash into 4-char groups and show the first 4 groups, an
 * ellipsis, then the last 2 groups. Copy always uses the full un-grouped
 * hash, never this display form.
 */
fun formatIdentityKeyHashForDisplay(hash: String): String {
    val groups = hash.chunked(4)
    if (groups.size <= 6) {
        return groups.joinToString(" ")
    }
    return (groups.take(4) + "…" + groups.takeLast(2)).joinToString(" ")
}

/**
 * The full grouped hash for the share dialog: every 4-char group, nothing
 * truncated — the share dialog exists for reading, screenshots, and
 * side-channel verification.
 */
fun formatIdentityKeyHashForShare(hash: String): String =
    hash.chunked(4).joinToString(" ")

/**
 * One identity row: a provider with an established, identity-verified e2e
 * session (the provider identities list and the panel deck), and also the
 * device's own identity on the panel's top row — same shape, same layout.
 */
@Immutable
class ProviderIdentityRowUi(
    val clientId: String,
    val publicKeyHash: String,
    // the raw public identity key, for share-time rendering of the
    // canonical identicon png
    val publicKey: ByteArray,
    // list-row size raster (panel size for the own-identity row)
    val identicon: ImageBitmap?,
    // panel-deck size raster
    val identiconSmall: ImageBitmap?,
    // badge-size raster, rendered next to the client id in provider
    // locations to mark a provider with a verified e2e session
    val identiconBadge: ImageBitmap? = null,
) {
    // the identicons derive from the public key, which the hash captures, and
    // are cached per (key hash, size) -- so value equality is the ids and the hash
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProviderIdentityRowUi) return false
        return clientId == other.clientId && publicKeyHash == other.publicKeyHash
    }

    override fun hashCode(): Int = 31 * clientId.hashCode() + publicKeyHash.hashCode()
}

/**
 * Publishes the device's own public identity key (identicon + canonical
 * hash) and the live providers with an identity-verified e2e session. The
 * state comes from the SDK PostQuantumIdentityViewController, shared by
 * every platform; this view model maps it onto UI types and re-reads on
 * every `providerIdentitiesChanged`.
 */
@HiltViewModel
class PostQuantumIdentityViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    companion object {
        // identicon display dp sizes; rasters render at 2x (the canonical
        // convention shared with every platform)
        // the provider-locations trailing badge next to the 11.sp client id:
        // large enough to read as the peer's identicon, small enough to sit
        // inline with a single text line
        const val BADGE_IDENTICON_SIZE = 16
        const val DECK_IDENTICON_SIZE = 28
        const val ROW_IDENTICON_SIZE = 40
        // the panel's own-identity identicon: 2x a list row
        const val PANEL_IDENTICON_SIZE = 80
        // the share dialog identicon: 4x the panel, sized for screenshots
        const val SHARE_IDENTICON_SIZE = 320
    }

    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var viewControllerDevice: DeviceLocal? = null
    private var postQuantumIdentityVc: PostQuantumIdentityViewController? = null
    private val subs = mutableListOf<Sub>()
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, PostQuantumIdentityViewController>(
            open = { openPostQuantumIdentityViewController(it) },
            close = { device, vc -> closePostQuantumIdentityViewController(device, vc) },
        )

    // identicon raster cache, keyed by (key hash, dp size)
    private val identiconCache = mutableMapOf<String, ImageBitmap>()

    // the device's own identity (key hash + identicon + client id), shaped
    // like a provider identities row so the panel renders it identically
    var ownIdentity by mutableStateOf<ProviderIdentityRowUi?>(null)
        private set

    // providers with an established, identity-verified e2e session
    var providerIdentities by mutableStateOf<List<ProviderIdentityRowUi>>(listOf())
        private set

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        // the device is (re)created asynchronously (login, network change) —
        // wire per device, every time, never once at init
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                setupDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun setupDevice(device: DeviceLocal?) {
        ownIdentity = null
        providerIdentities = listOf()
        identiconCache.clear()
        controllerOwner.setDevice(device)
    }

    private fun openPostQuantumIdentityViewController(
        device: DeviceLocal
    ): PostQuantumIdentityViewController {
        val vc = device.openPostQuantumIdentityViewController()
        viewControllerDevice = device
        postQuantumIdentityVc = vc
        subs.add(vc.addPostQuantumIdentityListener {
            // sdk thread -> main; re-read on every providerIdentitiesChanged
            viewModelScope.launch {
                update()
            }
        })
        // start attaches the device listener and seeds it with the current state
        vc.start()
        update()
        return vc
    }

    private fun closePostQuantumIdentityViewController(
        device: DeviceLocal,
        vc: PostQuantumIdentityViewController,
    ) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (postQuantumIdentityVc === vc) {
            postQuantumIdentityVc = null
            viewControllerDevice = null
        }
    }

    /**
     * the canonical identicon raster for `key`, cached per (key hash, dp size)
     */
    fun identicon(key: ByteArray, hash: String, sizeDp: Int): ImageBitmap? {
        val cacheKey = "$hash:$sizeDp"
        identiconCache[cacheKey]?.let { return it }
        val image = renderIdenticon(key, sizeDp) ?: return null
        identiconCache[cacheKey] = image
        return image
    }

    private fun update() {
        val vc = postQuantumIdentityVc ?: return

        val hash = vc.publicIdentityKeyHash
        val key = vc.publicIdentityKey
        ownIdentity = if (key != null && !hash.isNullOrEmpty()) {
            ProviderIdentityRowUi(
                clientId = viewControllerDevice?.clientId?.idStr ?: "",
                publicKeyHash = hash,
                publicKey = key,
                identicon = identicon(key, hash, PANEL_IDENTICON_SIZE),
                identiconSmall = null,
            )
        } else {
            null
        }

        val rows = mutableListOf<ProviderIdentityRowUi>()
        val list = vc.providerIdentities
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val identity = list.get(i) ?: continue
                val clientId = identity.clientId?.idStr ?: continue
                val identityKey = identity.publicKey ?: continue
                val keyHash = identity.publicKeyHash ?: continue
                rows.add(
                    ProviderIdentityRowUi(
                        clientId = clientId,
                        publicKeyHash = keyHash,
                        publicKey = identityKey,
                        identicon = identicon(identityKey, keyHash, ROW_IDENTICON_SIZE),
                        identiconSmall = identicon(identityKey, keyHash, DECK_IDENTICON_SIZE),
                        identiconBadge = identicon(identityKey, keyHash, BADGE_IDENTICON_SIZE),
                    )
                )
            }
        }
        providerIdentities = rows
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
