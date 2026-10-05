package com.bringyour.network.ui.stats

import com.bringyour.network.ProvidePauseReason
import com.bringyour.network.R
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.Sdk

/**
 * Why an enabled provider may get no traffic, from what this device knows
 * locally. The provider card shows it as one muted line under the provide
 * mode row, so an idle provider is told why instead of seeing an empty chart.
 */
enum class ProviderIdleReason {
    NONE,
    // Auto provides to everyone only while this device's VPN is connected;
    // disconnected it provides only to the user's own devices
    AUTO_NOT_CONNECTED,
    NETWORK_ONLY,
    // the provide power mode pauses in Battery Saver, and it is on
    PAUSED_BATTERY_SAVER,
    // the provide power mode pauses on battery, and the device is unplugged
    PAUSED_NOT_CHARGING,
    PAUSED_WIFI_ONLY,
    PAUSED_NO_NETWORK,
    NO_TRAFFIC_YET;

    /**
     * the line's text, null for none
     */
    val messageResourceId: Int?
        get() = when (this) {
            NONE -> null
            AUTO_NOT_CONNECTED -> R.string.provider_idle_auto_not_connected
            NETWORK_ONLY -> R.string.provider_idle_network_only
            PAUSED_BATTERY_SAVER -> R.string.provider_idle_paused_battery_saver
            PAUSED_NOT_CHARGING -> R.string.provider_idle_paused_not_charging
            PAUSED_WIFI_ONLY -> R.string.provider_idle_paused_wifi_only
            PAUSED_NO_NETWORK -> R.string.provider_idle_paused_no_network
            NO_TRAFFIC_YET -> R.string.provider_idle_no_traffic_yet
        }

    /**
     * a reason from this device's own state, which is immediate; the server's
     * reason is cached for minutes, so these win over it
     */
    val local: Boolean
        get() = when (this) {
            AUTO_NOT_CONNECTED,
            NETWORK_ONLY,
            PAUSED_BATTERY_SAVER,
            PAUSED_NOT_CHARGING,
            PAUSED_WIFI_ONLY,
            PAUSED_NO_NETWORK -> true
            NONE,
            NO_TRAFFIC_YET -> false
        }
}

/**
 * The first matching reason wins. A null control mode is one this app does
 * not know (such as the sdk's unexposed manual mode); like Never it has no
 * reason, and the providing-disabled text covers Never.
 *
 * @param liveProvideMode the device's effective provide mode (`Sdk.ProvideMode*`)
 * @param recentProviderBytes the bytes relayed for clients over the stats window
 * @param providePauseReason why the app paused providing (`providePauseDecision`)
 */
fun providerIdleReason(
    controlMode: ProvideControlMode?,
    liveProvideMode: Long,
    providePaused: Boolean,
    provideNetworkMode: ProvideNetworkMode,
    recentProviderBytes: Long,
    providePauseReason: ProvidePauseReason = ProvidePauseReason.NONE,
): ProviderIdleReason {
    return when {
        controlMode == null || controlMode == ProvideControlMode.NEVER -> ProviderIdleReason.NONE
        controlMode == ProvideControlMode.NETWORK -> ProviderIdleReason.NETWORK_ONLY
        // ProvideMode is a bit set: compare per-case, never with ranges
        controlMode == ProvideControlMode.AUTO && liveProvideMode != Sdk.ProvideModePublic ->
            ProviderIdleReason.AUTO_NOT_CONNECTED
        providePaused && providePauseReason == ProvidePauseReason.BATTERY_SAVER ->
            ProviderIdleReason.PAUSED_BATTERY_SAVER
        providePaused && providePauseReason == ProvidePauseReason.NOT_CHARGING ->
            ProviderIdleReason.PAUSED_NOT_CHARGING
        providePaused && provideNetworkMode == ProvideNetworkMode.WIFI -> ProviderIdleReason.PAUSED_WIFI_ONLY
        providePaused -> ProviderIdleReason.PAUSED_NO_NETWORK
        liveProvideMode == Sdk.ProvideModePublic && recentProviderBytes == 0L -> ProviderIdleReason.NO_TRAFFIC_YET
        else -> ProviderIdleReason.NONE
    }
}
