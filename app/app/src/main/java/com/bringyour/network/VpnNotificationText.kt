package com.bringyour.network

/**
 * What the VPN notification (MainService's foreground notification) says for
 * its presentation (vpnNotificationPresentation): pure, so its words are unit
 * tested (VpnNotificationTextTest).
 *
 * Short, for small screens (apple/DUO.md item 16). A Samsung Flip's cover
 * screen, the lock screen and a narrow shade show the title and the text in
 * narrow rows and cut off what does not fit, so the title says the status
 * first: a narrow screen loses the end of the place, never the status.
 * - Connected: "Protected · Germany", then the provider count.
 * - Connecting, couldn't connect and the tunnel down: the connect screen's
 *   status line, as the Quick Settings tile's subtitle says it, then the
 *   place the connection is for.
 * - The VPN up with no connection asked for (providing, or the kill switch):
 *   the app's name and "On".
 *
 * A slow connection swaps the connected content in place, as it did before
 * this was pulled out of MainService (apple/DELAY.md §7.2): "Slow internet",
 * or the fallback title, over "URnetwork is still connected", with the delay
 * lines when expanded; then "Internet back to normal" for the few seconds
 * before the normal content returns.
 */
internal data class VpnNotificationText(
    val title: String,
    val text: String,
    /** The expanded text: the slow content's delay lines, else [text]. */
    val bigText: String,
)

/** The words the notification is made of: the app's resources in MainService, plain English in tests. */
internal interface VpnNotificationStrings {
    /** "URnetwork" */
    val appName: String

    /** "On" */
    val on: String

    /** "Protected · Germany" (vpn_notification_protected) */
    fun protectedAt(location: String): String

    /** "3 providers" (the provider_count plural) */
    fun providerCount(count: Int): String

    /** "Connecting to providers" */
    val connecting: String

    /** "Couldn't connect" */
    val connectFailed: String

    /** "VPN tunnel disconnected 😓" */
    val tunnelDisconnected: String

    /** "Slow internet" */
    val slowInternet: String

    /** "Using a slower fallback connection" */
    val slowInternetFallback: String

    /** "URnetwork is still connected" */
    val slowInternetStillConnected: String

    /** "Upload delay" */
    val uploadDelay: String

    /** "Download delay" */
    val downloadDelay: String

    /** "Internet back to normal" */
    val recovered: String

    /** "Delays cleared" */
    val recoveredDetail: String
}

internal fun vpnNotificationText(
    presentation: VpnNotificationPresentation,
    strings: VpnNotificationStrings,
): VpnNotificationText {
    when (val slowInternet = presentation.slowInternet) {
        is SlowInternetPresentation.Slow -> {
            val text = strings.slowInternetStillConnected
            return VpnNotificationText(
                title = if (slowInternet.fallback) strings.slowInternetFallback else strings.slowInternet,
                text = text,
                bigText = transportDelayLines(
                    slowInternet.delay,
                    uploadLabel = strings.uploadDelay,
                    downloadLabel = strings.downloadDelay,
                ).joinToString("\n").ifEmpty { text },
            )
        }
        SlowInternetPresentation.Recovered -> return VpnNotificationText(
            title = strings.recovered,
            text = strings.recoveredDetail,
            bigText = strings.recoveredDetail,
        )
        null -> Unit
    }
    // a requested connection always has a place, else "Best available
    // provider"; only the VPN up with no connection asked for has none
    val location = presentation.locationLabel
    val (title, text) = when (presentation.status) {
        VpnNotificationStatus.CONNECTED ->
            (location?.let(strings::protectedAt) ?: strings.appName) to
                strings.providerCount(presentation.providerCount)
        VpnNotificationStatus.CONNECTING -> strings.connecting to (location ?: strings.appName)
        // the connect screen's words; the connection keeps trying
        VpnNotificationStatus.CONNECT_FAILED -> strings.connectFailed to (location ?: strings.appName)
        VpnNotificationStatus.TUNNEL_DISCONNECTED -> strings.tunnelDisconnected to (location ?: strings.appName)
        VpnNotificationStatus.ACTIVE -> strings.appName to strings.on
    }
    return VpnNotificationText(title = title, text = text, bigText = text)
}
