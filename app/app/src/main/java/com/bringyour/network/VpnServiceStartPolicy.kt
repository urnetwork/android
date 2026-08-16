package com.bringyour.network

/** Pure start/restart decision shared by MainApplication and MainService. */
internal fun vpnServiceRequired(
    provideEnabled: Boolean,
    connectEnabled: Boolean,
    routeLocal: Boolean,
): Boolean = provideEnabled || connectEnabled || !routeLocal

internal data class VpnServiceStartFacts(
    val stopRequested: Boolean,
    val startRequested: Boolean,
    val appMarkedActive: Boolean,
    val deviceRequiresVpn: Boolean,
    val redelivered: Boolean,
    val systemStart: Boolean,
    val commandCompatible: Boolean = true,
)

internal enum class VpnServiceStartDecision {
    RUN,
    STOP,
}

/**
 * A new process starts with MainApplication.serviceActive=false even when
 * Android is redelivering the start intent for a VPN it killed. In that case
 * the restored SDK device state, plus Android's redelivery/system-start flag,
 * is the authority. An ordinary stale app intent still cannot resurrect a VPN.
 */
internal fun decideVpnServiceStart(facts: VpnServiceStartFacts): VpnServiceStartDecision {
    if (!facts.commandCompatible || facts.stopRequested || !facts.startRequested) {
        return VpnServiceStartDecision.STOP
    }
    // Android's Settings > Always-on VPN selection is an explicit system
    // policy. It remains authoritative even when the app's restored
    // connect/provide/kill-switch state would not ordinarily need a TUN.
    if (facts.systemStart) {
        return VpnServiceStartDecision.RUN
    }
    if (facts.appMarkedActive) {
        return VpnServiceStartDecision.RUN
    }
    return if (facts.redelivered && facts.deviceRequiresVpn) {
        VpnServiceStartDecision.RUN
    } else {
        VpnServiceStartDecision.STOP
    }
}

internal const val VPN_SERVICE_COMMAND_VERSION = 1

/**
 * Explicit app commands are versioned because Android can redeliver an Intent
 * across an APK replacement (or a downgrade). Legacy v0 commands remain
 * accepted; a future command is rejected instead of being misinterpreted.
 * Framework Always-on starts do not use the private app protocol.
 */
internal fun vpnServiceCommandCompatible(source: String?, version: Int): Boolean {
    return source != "app" || version in 0..VPN_SERVICE_COMMAND_VERSION
}

/**
 * Android's documented always-on detection contract is app-owned commands are
 * explicitly tagged; an untagged start is system-owned. isAlwaysOn() is an
 * additional level check on Android 10+, not the only signal, so Android 7-9
 * cold-boot starts follow the same path.
 */
internal fun vpnServiceSystemStart(source: String?, frameworkAlwaysOn: Boolean): Boolean {
    return source != "app" || frameworkAlwaysOn
}

/** Hold a fail-closed interface until an always-on service has a live provider. */
internal fun vpnAlwaysOnGuardRequired(
    systemAlwaysOn: Boolean,
    deviceAvailable: Boolean,
    providerConnected: Boolean,
): Boolean = systemAlwaysOn && (!deviceAvailable || !providerConnected)
