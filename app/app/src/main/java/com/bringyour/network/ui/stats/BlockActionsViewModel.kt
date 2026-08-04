package com.bringyour.network.ui.stats

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.utils.isIpAddressValue
import com.bringyour.network.utils.listToSdkStringList
import com.bringyour.network.utils.sdkStringListToList
import com.bringyour.sdk.BlockActionOverride
import com.bringyour.sdk.BlockActionOverrideList
import com.bringyour.sdk.BlockActionViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.RouteOverride
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// coalescing window for bursts of block-action updates
private const val BLOCK_ACTIONS_COALESCE_MILLIS = 100L

/**
 * A recent routing decision, aggregated per destination cluster
 */
@Immutable
data class BlockActionUi(
    val id: String,
    val timeMillis: Long,
    // cluster hosts/ips that did NOT match an override (disjoint from the matched sets)
    val hosts: List<String>,
    val ips: List<String>,
    // the exact hosts/ips that matched an override rule, shown as green chips at the
    // front (disjoint from hosts/ips)
    val matchedHosts: List<String>,
    val matchedIps: List<String>,
    // the unmatched hosts collapsed to base names (Sdk.collapseHostNames), white chips
    val hostBaseNames: List<String>,
    val block: Boolean,
    val local: Boolean,
    val hasBlockOverride: Boolean,
    val hasRouteOverride: Boolean,
    // the deciding route override id, when a rule determined the decision
    val overrideId: String?,
    val byteCount: Long,
    // short client ids of the exits CURRENTLY carrying flows to this
    // cluster's ips (live join against the flow table). One id is the normal
    // healthy shape; two ids on one row is a site split across egress IPs --
    // the exact event the affinity work exists to prevent
    val exitShortIds: List<String> = listOf(),
) {
    /** every host name (matched + unmatched) and every ip (matched + unmatched) */
    val allHostNames: List<String>
        get() = matchedHosts + hosts
    val allIps: List<String>
        get() = matchedIps + ips

    /**
     * all host values that can be added to a split rule, host names first
     */
    val hostValues: List<String>
        get() = allHostNames + allIps

    /** count of unmatched ips, rendered as a single "X IPs" pill */
    val ipCount: Int
        get() = ips.size
}

/**
 * A host split rule (a block action override with host values)
 */
@Immutable
data class SplitRuleUi(
    val id: String,
    // the raw host values (host names and ips mixed), for the editor
    val hosts: List<String>,
    // the rule's host names collapsed to base names, and its exact ip values — both
    // rendered as green chips in the row
    val hostBaseNames: List<String>,
    val ipValues: List<String>,
)

/**
 * What an app split rule does with the app's traffic.
 *
 * EXCLUDED and INCLUDED are tunnel MEMBERSHIP (enforced by the VpnService
 * builder's disallow/allow lists). PINNED is not membership at all: the app
 * uses the tunnel like any other, but all of its flows are held to one exit,
 * so its API session and its CDNs present a single egress IP -- the fix for
 * apps whose images fail to load behind a multi-exit VPN. A pinned app must
 * never reach the builder's allow list, or the VPN would flip to
 * allowlist mode and route ONLY pinned apps.
 */
enum class AppRuleMode {
    EXCLUDED,
    INCLUDED,
    PINNED;

    fun toRouteOverride(): RouteOverride {
        val route = RouteOverride()
        when (this) {
            EXCLUDED -> route.local = true
            INCLUDED -> route.local = false
            PINNED -> {
                route.local = false
                route.pin = true
            }
        }
        return route
    }

    companion object {
        fun of(local: Boolean, pin: Boolean): AppRuleMode = when {
            local -> EXCLUDED
            pin -> PINNED
            else -> INCLUDED
        }
    }
}

/**
 * An app split rule (a block action override with app ids); see [AppRuleMode]
 */
data class AppSplitRuleUi(
    val id: String,
    val appId: String,
    val mode: AppRuleMode,
) {
    val includedInTunnel: Boolean
        get() = mode == AppRuleMode.INCLUDED
}

/**
 * Publishes the live block action window, block stats, and the block
 * action overrides, split into host rules ("split rules") and app rules
 */
@HiltViewModel
class BlockActionsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private var blockActionVc: BlockActionViewController? = null
    private val subs = mutableListOf<Sub>()
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private var viewControllerDevice: DeviceLocal? = null
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, BlockActionViewController>(
            open = { openLiveUpdates(it) },
            close = { device, vc -> closeLiveUpdates(device, vc) },
        )

    // coalesces bursts of block-action updates (see the listener below)
    private var blockActionsUpdateJob: Job? = null
    // the exit-attribution re-poll (see openLiveUpdates); canceled with the
    // rest of the live updates
    private var exitAttributionJob: Job? = null

    /**
     * newest first
     */
    var blockActions by mutableStateOf<List<BlockActionUi>>(listOf())
        private set

    var splitRules by mutableStateOf<List<SplitRuleUi>>(listOf())
        private set

    var appRules by mutableStateOf<List<AppSplitRuleUi>>(listOf())
        private set

    var allowedCount by mutableIntStateOf(0)
        private set

    var blockedCount by mutableIntStateOf(0)
        private set

    /**
     * apps forced through the vpn. when non-empty, they take
     * precedence and the tunnel runs in allowlist mode
     */
    val tunnelIncludedAppIds: List<String>
        get() = appRules.filter { it.mode == AppRuleMode.INCLUDED }.map { it.appId }

    /**
     * apps that bypass the vpn. PINNED apps are deliberately absent from both
     * of these: pinning is exit placement inside the tunnel, not membership,
     * so a pinned app must neither flip the tunnel into allowlist mode nor
     * bypass it
     */
    val tunnelExcludedAppIds: List<String>
        get() = appRules.filter { it.mode == AppRuleMode.EXCLUDED }.map { it.appId }

    /** apps held to a single exit */
    val pinnedAppIds: List<String>
        get() = appRules.filter { it.mode == AppRuleMode.PINNED }.map { it.appId }

    init {
        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
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

    /**
     * The block policy stays installed in DeviceLocal while this controller is
     * closed. Only the UI's recent-action snapshots/listeners pause, avoiding
     * a full JNI/list/hostname rebuild for every burst generated by another
     * foreground app.
     */
    private fun setupDevice(device: DeviceLocal?) {
        blockActions = listOf()
        splitRules = listOf()
        appRules = listOf()
        allowedCount = 0
        blockedCount = 0
        controllerOwner.setDevice(device)
    }

    private fun openLiveUpdates(device: DeviceLocal): BlockActionViewController {
        val vc = device.openBlockActionViewController()
        viewControllerDevice = device
        blockActionVc = vc

        subs.add(vc.addBlockActionsListener {
            // Coalesce bursts into one rebuild: this fires per routing
            // decision (many/sec while browsing) and the full list rebuild
            // plus per-host JNI is otherwise wasted every time.
            blockActionsUpdateJob?.cancel()
            blockActionsUpdateJob = viewModelScope.launch {
                delay(BLOCK_ACTIONS_COALESCE_MILLIS)
                updateBlockActions()
            }
        })
        subs.add(vc.addBlockActionStatsListener {
            viewModelScope.launch {
                updateBlockStats()
            }
        })
        subs.add(device.addBlockActionOverridesChangeListener {
            viewModelScope.launch {
                updateOverrides()
            }
        })
        vc.start()
        updateBlockActions()
        updateBlockStats()
        updateOverrides()

        // keep the exit attribution live: flows re-race and rebind between
        // block-action events, so the join is refreshed on a slow tick as
        // well -- a row growing a second exit chip mid-session is exactly
        // the observation this feature exists for
        exitAttributionJob?.cancel()
        exitAttributionJob = viewModelScope.launch {
            while (true) {
                delay(5_000L)
                if (blockActions.isNotEmpty()) {
                    updateBlockActions()
                }
            }
        }
        return vc
    }

    private fun closeLiveUpdates(device: DeviceLocal, vc: BlockActionViewController) {
        blockActionsUpdateJob?.cancel()
        blockActionsUpdateJob = null
        exitAttributionJob?.cancel()
        exitAttributionJob = null
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (blockActionVc === vc) {
            blockActionVc = null
            viewControllerDevice = null
        }
    }

    private fun updateBlockActions() {
        val vc = blockActionVc ?: return

        // the live destination->exit attribution, joined onto each cluster's
        // ips below. Pull-model: this reflects the exit CURRENTLY carrying
        // each ip, after any re-race or rebind -- so a row growing a second
        // exit chip is a site split across egress IPs, live
        val exitsByIp = mutableMapOf<String, MutableSet<String>>()
        deviceManager.device?.destinationExits?.let { destinationExits ->
            val n = destinationExits.len()
            for (i in 0 until n) {
                val row = destinationExits.get(i) ?: continue
                val shortId = row.clientId?.idStr?.take(8) ?: continue
                exitsByIp.getOrPut(row.destinationIp) { mutableSetOf() }.add(shortId)
            }
        }

        val items = mutableListOf<BlockActionUi>()
        val list = vc.blockActions
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val action = list.get(i) ?: continue
                val unmatchedHosts = sdkStringListToList(action.hosts)
                val ips = sdkStringListToList(action.ips)
                val matchedIps = sdkStringListToList(action.matchedIps)
                items.add(
                    BlockActionUi(
                        id = action.blockActionId?.idStr ?: "$i-${action.time}",
                        timeMillis = action.time,
                        hosts = unmatchedHosts,
                        ips = ips,
                        matchedHosts = sdkStringListToList(action.matchedHosts),
                        matchedIps = matchedIps,
                        hostBaseNames = collapseHosts(unmatchedHosts),
                        block = action.block,
                        local = action.local,
                        hasBlockOverride = action.blockOverride != null,
                        hasRouteOverride = action.routeOverride != null,
                        overrideId = action.overrideId?.idStr,
                        byteCount = action.byteCount,
                        exitShortIds = (matchedIps + ips)
                            .flatMap { exitsByIp[it] ?: emptySet() }
                            .distinct()
                            .sorted(),
                    )
                )
            }
        }
        // newest first
        blockActions = items.reversed()
    }

    private fun updateBlockStats() {
        val vc = blockActionVc ?: return
        val stats = vc.blockStats
        allowedCount = (stats?.allowedCount ?: 0L).toInt()
        blockedCount = (stats?.blockedCount ?: 0L).toInt()
    }

    private fun updateOverrides() {
        val device = viewControllerDevice ?: return
        val hostRules = mutableListOf<SplitRuleUi>()
        val appSplitRules = mutableListOf<AppSplitRuleUi>()
        val list = device.blockActionOverrides
        if (list != null) {
            val n = list.len()
            for (i in 0 until n) {
                val override = list.get(i) ?: continue
                val overrideId = override.overrideId?.idStr ?: continue
                val appIds = sdkStringListToList(override.appIds)
                if (appIds.isNotEmpty()) {
                    // an app rule: excluded when the route override is local,
                    // pinned when it carries a pin, else included
                    val mode = AppRuleMode.of(
                        local = override.routeOverride?.local ?: false,
                        pin = override.routeOverride?.pin ?: false,
                    )
                    for (appId in appIds) {
                        appSplitRules.add(
                            AppSplitRuleUi(
                                id = overrideId,
                                appId = appId,
                                mode = mode,
                            )
                        )
                    }
                } else {
                    val ruleHosts = sdkStringListToList(override.hosts)
                    val ruleHostNames = ruleHosts.filter { !isIpAddressValue(it) }
                    val ruleIps = ruleHosts.filter { isIpAddressValue(it) }
                    hostRules.add(
                        SplitRuleUi(
                            id = overrideId,
                            hosts = ruleHosts,
                            hostBaseNames = collapseHosts(ruleHostNames),
                            ipValues = ruleIps,
                        )
                    )
                }
            }
        }
        splitRules = hostRules
        appRules = appSplitRules
    }

    /**
     * collapse host names to base names through the shared SDK logic
     * (Sdk.collapseHostNamesList), so every platform collapses identically
     */
    private fun collapseHosts(hosts: List<String>): List<String> =
        if (hosts.isEmpty()) {
            emptyList()
        } else {
            sdkStringListToList(Sdk.collapseHostNamesList(listToSdkStringList(hosts)))
        }

    /**
     * the split rule matching a block action's applied override, if it still exists
     */
    fun splitRule(overrideId: String?): SplitRuleUi? {
        if (overrideId == null) {
            return null
        }
        return splitRules.firstOrNull { it.id == overrideId }
    }

    /**
     * creates a split rule forcing the selected host values to route local
     */
    fun createLocalRule(hosts: List<String>) {
        val device = deviceManager.device ?: return
        if (hosts.isEmpty()) {
            return
        }
        val override = BlockActionOverride()
        override.overrideId = Sdk.newId()
        override.hosts = listToSdkStringList(hosts)
        val route = RouteOverride()
        route.local = true
        override.routeOverride = route
        device.addBlockActionOverride(override)
        updateOverrides()
    }

    /**
     * replaces the host values of an existing split rule
     */
    fun updateRule(id: String, hosts: List<String>) {
        if (hosts.isEmpty()) {
            removeRule(id)
            return
        }
        replaceOverrides { override ->
            if (override.overrideId?.idStr == id) {
                override.hosts = listToSdkStringList(hosts)
            }
            override
        }
    }

    fun removeRule(id: String) {
        removeOverride(id)
    }

    /**
     * creates an app split rule in one of the three modes; see [AppRuleMode]
     */
    fun createAppRule(appId: String, mode: AppRuleMode) {
        val device = deviceManager.device ?: return
        val override = BlockActionOverride()
        override.overrideId = Sdk.newId()
        val appIds = listToSdkStringList(listOf(appId))
        override.appIds = appIds
        override.routeOverride = mode.toRouteOverride()
        device.addBlockActionOverride(override)
        updateOverrides()
    }

    fun updateAppRule(id: String, mode: AppRuleMode) {
        replaceOverrides { override ->
            if (override.overrideId?.idStr == id) {
                override.routeOverride = mode.toRouteOverride()
            }
            override
        }
    }

    fun removeAppRule(id: String) {
        removeOverride(id)
    }

    private fun removeOverride(id: String) {
        val device = deviceManager.device ?: return
        val list = device.blockActionOverrides ?: return
        val n = list.len()
        for (i in 0 until n) {
            val override = list.get(i) ?: continue
            if (override.overrideId?.idStr == id) {
                device.removeBlockActionOverride(override.overrideId)
                break
            }
        }
        updateOverrides()
    }

    private fun replaceOverrides(transform: (BlockActionOverride) -> BlockActionOverride) {
        val device = deviceManager.device ?: return
        val list = device.blockActionOverrides ?: return
        val next = BlockActionOverrideList()
        val n = list.len()
        for (i in 0 until n) {
            val override = list.get(i) ?: continue
            next.add(transform(override))
        }
        device.setBlockActionOverrides(next)
        updateOverrides()
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }
}
