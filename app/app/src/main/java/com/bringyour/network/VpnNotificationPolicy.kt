package com.bringyour.network

internal enum class VpnNotificationStatus {
    ACTIVE,
    CONNECTING,
    CONNECTED,
}

internal data class VpnNotificationPresentation(
    val status: VpnNotificationStatus,
    val locationLabel: String?,
    val providerCount: Int,
    val showDisconnect: Boolean,
)

/** Pure projection of SDK state into the foreground notification. */
internal fun vpnNotificationPresentation(
    connectRequested: Boolean,
    systemAlwaysOn: Boolean,
    providerCount: Long,
    locationName: String?,
    city: String?,
    region: String?,
    country: String?,
    bestAvailableLabel: String,
): VpnNotificationPresentation {
    val safeProviderCount = providerCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val locationLabel = if (connectRequested) {
        sequenceOf(locationName, city, region, country)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
            ?: bestAvailableLabel
    } else {
        null
    }
    val status = when {
        !connectRequested -> VpnNotificationStatus.ACTIVE
        safeProviderCount > 0 -> VpnNotificationStatus.CONNECTED
        else -> VpnNotificationStatus.CONNECTING
    }
    return VpnNotificationPresentation(
        status = status,
        locationLabel = locationLabel,
        providerCount = safeProviderCount,
        // Android Always-on policy would immediately reconnect, so presenting
        // a button that cannot stick would be misleading.
        showDisconnect = connectRequested && !systemAlwaysOn,
    )
}
