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

internal enum class VpnServiceLaunchDecision {
    ALREADY_RUNNING,
    REQUEST_VPN_PERMISSION,
    START_FOREGROUND,
}

internal const val VPN_SERVICE_START_ADOPTION_TIMEOUT_MILLIS = 10_000L
internal const val VPN_SERVICE_CLASS_NAME = "com.bringyour.network.MainService"

private val VPN_SERVICE_START_RETRY_DELAYS_MILLIS = longArrayOf(
    1_000L,
    2_000L,
    5_000L,
    10_000L,
    30_000L,
)

/**
 * Whether this cold process was brought up specifically for the VPN service.
 * Application startup can then defer SDK/device restoration until immediately
 * after MainService has met Android's foreground-promotion deadline.
 */
internal fun vpnServiceOwnsColdProcessStart(
    startIntentClassName: String?,
    runningServiceClassNames: Set<String>,
    vpnServiceClassName: String,
): Boolean =
    startIntentClassName == vpnServiceClassName ||
        vpnServiceClassName in runningServiceClassNames

/**
 * A successful startForegroundService() call is only a request to Android. If
 * no service lifecycle callback follows, optimistic app state must eventually
 * be released so a later foreground/network event can try again.
 */
internal fun vpnServiceStartAttemptTimedOut(
    expectedGeneration: Long,
    currentGeneration: Long,
    serviceActive: Boolean,
    serviceAdopted: Boolean,
): Boolean =
    expectedGeneration == currentGeneration && serviceActive && !serviceAdopted

/**
 * Retry only while a started Activity can supply Android's user-visible FGS
 * start exemption. Background retries would just repeat
 * ForegroundServiceStartNotAllowedException and can become an OEM-specific
 * crash loop.
 */
internal fun vpnServiceStartRetryEligible(
    retryRequested: Boolean,
    vpnRequired: Boolean,
    serviceActive: Boolean,
    startPending: Boolean,
    foregroundActivityAvailable: Boolean,
): Boolean =
    retryRequested &&
        vpnRequired &&
        !serviceActive &&
        !startPending &&
        foregroundActivityAvailable

/** Bounded exponential-ish backoff for repeated foreground-service failures. */
internal fun vpnServiceStartRetryDelayMillis(failureCount: Int): Long {
    val index = failureCount.coerceIn(0, VPN_SERVICE_START_RETRY_DELAYS_MILLIS.lastIndex)
    return VPN_SERVICE_START_RETRY_DELAYS_MILLIS[index]
}

/** Notification permission is intentionally absent: Android does not require it to run an FGS. */
internal fun decideVpnServiceLaunch(
    serviceActive: Boolean,
    startPending: Boolean,
    vpnPermissionRequired: Boolean,
): VpnServiceLaunchDecision = when {
    serviceActive || startPending -> VpnServiceLaunchDecision.ALREADY_RUNNING
    vpnPermissionRequired -> VpnServiceLaunchDecision.REQUEST_VPN_PERMISSION
    else -> VpnServiceLaunchDecision.START_FOREGROUND
}

private val TUNNEL_RETRY_DELAYS_MILLIS = longArrayOf(
    250L,
    1_000L,
    2_000L,
    5_000L,
    10_000L,
    30_000L,
)

/** Infinite retry with a bounded delay: a transient TUN failure must not permanently stop VPN. */
internal fun tunnelRetryDelayMillis(failureCount: Int): Long {
    val index = failureCount.coerceIn(0, TUNNEL_RETRY_DELAYS_MILLIS.lastIndex)
    return TUNNEL_RETRY_DELAYS_MILLIS[index]
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
