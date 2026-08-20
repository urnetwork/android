package com.bringyour.network.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Blue600
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.Yellow400
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.StringList
import com.bringyour.sdk.Sub
import com.bringyour.sdk.TransportSettings
import com.bringyour.sdk.TransportStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The stable transport vocabulary shared with the sdk. The raw values are the
 * sdk `TransportType` strings, which double as the `TransportMode` strings for
 * the selectable carriers. p2p and unknown are observable carriers only: p2p
 * is negotiated per peer outside the transport policy, and unknown holds
 * traffic admitted for sending that has not yet been written to a physical
 * carrier (it is attributed to its carrier on the first route write).
 *
 * Only presentation lives here (names, colors, descriptions). Every rule --
 * the stable order, the selectable modes and their default preference order,
 * which carriers a policy enables, and the auto editing constraints -- is the
 * sdk's, so it is shared and tested once for every platform.
 */
enum class TransportTypeUi(
    val rawValue: String,
    /**
     * the display name for the carriers whose name is a product name (not
     * localized); null when the name is a localized word (see `labelRes`)
     */
    private val literalName: String?,
    /**
     * the localized display name, for the plain-word buckets
     */
    private val labelRes: Int?,
    /**
     * a one line description for the settings editor
     */
    val detailRes: Int?,
    /**
     * the brand color of the carrier, used for the transport bar segments and
     * the legend dots. The queued bucket is neutral. Coral is deliberately not
     * used so the bar cannot be confused with the blocked chart next to it.
     */
    val color: Color,
) {
    H3(Sdk.TransportTypeH3, "H3", null, R.string.transport_h3_description, Green),
    H1(Sdk.TransportTypeH1, "H1", null, R.string.transport_h1_description, BlueLight),
    /** tunneled over dns -- the "whodis" transport */
    DNS(Sdk.TransportTypeDns, "whodis", null, R.string.transport_dns_description, Pink),
    /** tunneled over dns with a constant-rate reply pump -- "whodis pump" */
    DNS_PUMP(Sdk.TransportTypeDnsPump, "whodis pump", null, R.string.transport_dnspump_description, Yellow400),
    P2P(Sdk.TransportTypeP2p, "P2P", null, null, Blue600),
    /**
     * admitted but not yet attributed to a physical carrier (queued); a dark
     * neutral -- muted gray read too close to the pale H1 blue in the bar
     */
    UNKNOWN(Sdk.TransportTypeUnknown, null, R.string.transport_queued, null, TextFaint);

    /**
     * The display name. The dns carriers carry their product names, which are
     * not localized; the queued bucket is a plain word and is.
     */
    @Composable
    fun label(): String {
        return literalName ?: stringResource(id = labelRes ?: R.string.transport_queued)
    }

    val isSelectable: Boolean
        get() = selectable.contains(this)

    companion object {
        /**
         * maps an sdk transport type / mode string, null for vocabulary this
         * app does not know (a newer sdk)
         */
        fun fromRawValue(rawValue: String?): TransportTypeUi? {
            if (rawValue == null) {
                return null
            }
            return entries.firstOrNull { it.rawValue == rawValue }
        }

        /**
         * maps an sdk string list of transport types / modes, dropping unknown
         * vocabulary
         */
        fun fromSdk(list: StringList?): List<TransportTypeUi> {
            return sdkStringListToList(list).mapNotNull { fromRawValue(it) }
        }

        /**
         * The carriers a transport mode can select, in the sdk's default
         * preference order (h1, h3, dns, then dns pump). This is the order
         * every transport list in the app shows them in.
         */
        val selectable: List<TransportTypeUi>
            get() = fromSdk(Sdk.selectableTransportModes())
    }
}

/**
 * A render snapshot of an sdk transport policy: one carrier, or auto over the
 * enabled carriers. Derived entirely through the sdk helpers so the app never
 * re-implements the policy rules; edits go through the sdk object (`sdk`),
 * then a fresh snapshot is taken.
 *
 * Equality is over the snapshot fields; the sdk reference is the source.
 */
class TransportSettingsUi(
    /**
     * the sdk policy this snapshot was taken from. Clone it to edit.
     */
    val sdk: TransportSettings,
) {
    /**
     * the selected carrier, or null for auto
     */
    val singleTransport: TransportTypeUi?

    /**
     * the carriers enabled under auto in the sdk's preference order. Retained
     * while a single carrier is selected, so switching back to auto restores
     * the same policy
     */
    val autoTransports: List<TransportTypeUi>

    /**
     * the carriers the policy enables, in preference order: the single
     * carrier, or the auto carriers
     */
    val enabledTransports: List<TransportTypeUi>

    init {
        val mode = sdk.mode
        singleTransport = if (mode == Sdk.TransportModeAuto) {
            null
        } else {
            // an unrecognized mode normalizes to auto in the sdk
            TransportTypeUi.fromRawValue(mode)?.takeIf { it.isSelectable }
        }
        autoTransports = TransportTypeUi.fromSdk(sdk.autoModes())
        enabledTransports = TransportTypeUi.fromSdk(sdk.enabledTransportTypes())
    }

    val isAuto: Boolean
        get() = singleTransport == null

    fun isAutoEnabled(transport: TransportTypeUi): Boolean {
        return autoTransports.contains(transport)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransportSettingsUi) return false
        return singleTransport == other.singleTransport &&
            autoTransports == other.autoTransports &&
            enabledTransports == other.enabledTransports
    }

    override fun hashCode(): Int {
        var result = singleTransport.hashCode()
        result = 31 * result + autoTransports.hashCode()
        result = 31 * result + enabledTransports.hashCode()
        return result
    }

    companion object {
        /**
         * the sdk default client policy: auto over every carrier
         */
        fun defaultClient(): TransportSettingsUi {
            return TransportSettingsUi(Sdk.defaultTransportSettings() ?: TransportSettings())
        }

        /**
         * the sdk default provider policy
         */
        fun defaultProvider(): TransportSettingsUi {
            return TransportSettingsUi(Sdk.defaultProviderTransportSettings() ?: TransportSettings())
        }
    }
}

/** Runtime Auto capability reported by the process that owns the transport budget. */
data class TransportRuntimeStatusUi(
    val autoDegraded: Boolean,
    val autoEligibleTransports: Set<TransportTypeUi>,
    val autoConstraint: String,
) {
    fun isAutoEligible(transport: TransportTypeUi): Boolean {
        return autoEligibleTransports.contains(transport)
    }

    companion object {
        fun fromSdk(status: TransportStatus): TransportRuntimeStatusUi {
            return TransportRuntimeStatusUi(
                autoDegraded = status.autoDegraded,
                autoEligibleTransports = TransportTypeUi.fromSdk(status.autoEligibleModes).toSet(),
                autoConstraint = status.autoConstraint,
            )
        }
    }
}

/**
 * Which device policy a transport settings surface edits: the client policy
 * (the carrier this device uses to reach providers) or the provider policy
 * (the carrier it uses when relaying for remote clients)
 */
enum class TransportSettingsKind {
    CLIENT,
    PROVIDER;

    fun defaultSettings(): TransportSettingsUi {
        return when (this) {
            CLIENT -> TransportSettingsUi.defaultClient()
            PROVIDER -> TransportSettingsUi.defaultProvider()
        }
    }
}

/**
 * Compares two sdk policies with the sdk's normalized equality (the by-value
 * helper, so the call can never fall back to the gomobile proxy identity
 * `equals(Object)`)
 */
fun transportSettingsEqual(a: TransportSettings, b: TransportSettings): Boolean {
    return Sdk.transportSettingsEqual(a, b)
}

/**
 * Publishes the device transport settings (client and provider) and applies
 * edits.
 *
 * The device's change listeners deliver every change (the same pattern as the
 * dns resolver settings), so the published value is the policy in force. One
 * initial read covers the time before the first event.
 *
 * Persistence: the device runs in this process (`DeviceLocal`) and persists
 * the policy in the shared local state on every change, restoring it when it
 * is created, so unlike the apple app no app-side mirror is needed.
 */
@HiltViewModel
class TransportSettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val subs = mutableListOf<Sub>()
    private var subscribedDevice: DeviceLocal? = null
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val subscriptionOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, Unit>(
            open = { openDeviceSubscription(it) },
            close = { device, _ -> closeDeviceSubscription(device) },
        )

    var clientSettings by mutableStateOf<TransportSettingsUi?>(null)
        private set

    var providerSettings by mutableStateOf<TransportSettingsUi?>(null)
        private set

    var clientStatus by mutableStateOf<TransportRuntimeStatusUi?>(null)
        private set

    var providerStatus by mutableStateOf<TransportRuntimeStatusUi?>(null)
        private set

    // the applied policy each status was computed for. The device fires the
    // paired settings event before its status event (and refresh() reads
    // settings first), so the published settings at status arrival are the
    // paired snapshot. The editor renders status decorations only while its
    // draft equals this policy.
    var clientStatusPolicy by mutableStateOf<TransportSettingsUi?>(null)
        private set

    var providerStatusPolicy by mutableStateOf<TransportSettingsUi?>(null)
        private set

    init {
        processLifecycle.addObserver(this)
        subscriptionOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                clientSettings = null
                providerSettings = null
                clientStatus = null
                providerStatus = null
                clientStatusPolicy = null
                providerStatusPolicy = null
                subscriptionOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        subscriptionOwner.setForeground(false)
    }

    fun settings(kind: TransportSettingsKind): TransportSettingsUi? {
        return when (kind) {
            TransportSettingsKind.CLIENT -> clientSettings
            TransportSettingsKind.PROVIDER -> providerSettings
        }
    }

    fun status(kind: TransportSettingsKind): TransportRuntimeStatusUi? {
        return when (kind) {
            TransportSettingsKind.CLIENT -> clientStatus
            TransportSettingsKind.PROVIDER -> providerStatus
        }
    }

    fun statusPolicy(kind: TransportSettingsKind): TransportSettingsUi? {
        return when (kind) {
            TransportSettingsKind.CLIENT -> clientStatusPolicy
            TransportSettingsKind.PROVIDER -> providerStatusPolicy
        }
    }

    private fun openDeviceSubscription(device: DeviceLocal) {
        subscribedDevice = device
        subs.add(device.addTransportSettingsChangeListener { sdkSettings ->
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    publish(sdkSettings, TransportSettingsKind.CLIENT)
                }
            }
        })
        subs.add(device.addProviderTransportSettingsChangeListener { sdkSettings ->
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    publish(sdkSettings, TransportSettingsKind.PROVIDER)
                }
            }
        })
        subs.add(device.addTransportStatusChangeListener { sdkStatus ->
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    publish(sdkStatus, TransportSettingsKind.CLIENT)
                }
            }
        })
        subs.add(device.addProviderTransportStatusChangeListener { sdkStatus ->
            viewModelScope.launch {
                if (subscribedDevice === device) {
                    publish(sdkStatus, TransportSettingsKind.PROVIDER)
                }
            }
        })
        refresh(device)
    }

    private fun closeDeviceSubscription(device: DeviceLocal) {
        subs.forEach { it.close() }
        subs.clear()
        if (subscribedDevice === device) {
            subscribedDevice = null
        }
    }

    /**
     * re-reads both policies from the device
     */
    private fun refresh(device: DeviceLocal) {
        publish(device.transportSettings, TransportSettingsKind.CLIENT)
        publish(device.providerTransportSettings, TransportSettingsKind.PROVIDER)
        publish(device.transportStatus, TransportSettingsKind.CLIENT)
        publish(device.providerTransportStatus, TransportSettingsKind.PROVIDER)
    }

    /**
     * Publishes a policy delivered by the device. null (a device with no policy
     * at all) keeps the published value stable rather than flashing to null
     */
    private fun publish(sdkSettings: TransportSettings?, kind: TransportSettingsKind) {
        if (sdkSettings == null) {
            return
        }
        val settings = TransportSettingsUi(sdkSettings)
        when (kind) {
            TransportSettingsKind.CLIENT -> {
                if (settings != clientSettings) {
                    clientSettings = settings
                }
            }
            TransportSettingsKind.PROVIDER -> {
                if (settings != providerSettings) {
                    providerSettings = settings
                }
            }
        }
    }

    private fun publish(sdkStatus: TransportStatus?, kind: TransportSettingsKind) {
        if (sdkStatus == null) {
            return
        }
        val status = TransportRuntimeStatusUi.fromSdk(sdkStatus)
        // pair the status with the settings current at its arrival, also when
        // the status value itself is unchanged (the policy it decorates moved)
        when (kind) {
            TransportSettingsKind.CLIENT -> {
                if (status != clientStatus) {
                    clientStatus = status
                }
                if (clientStatusPolicy != clientSettings) {
                    clientStatusPolicy = clientSettings
                }
            }
            TransportSettingsKind.PROVIDER -> {
                if (status != providerStatus) {
                    providerStatus = status
                }
                if (providerStatusPolicy != providerSettings) {
                    providerStatusPolicy = providerSettings
                }
            }
        }
    }

    /**
     * Applies a policy to the device. The device persists it and the applied
     * (normalized) policy comes back through the change listener.
     */
    fun apply(sdkSettings: TransportSettings, kind: TransportSettingsKind) {
        val device = deviceManager.device ?: return
        when (kind) {
            TransportSettingsKind.CLIENT -> device.transportSettings = sdkSettings
            TransportSettingsKind.PROVIDER -> device.providerTransportSettings = sdkSettings
        }
        refresh(device)
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        subscriptionOwner.close()
        super.onCleared()
    }
}
