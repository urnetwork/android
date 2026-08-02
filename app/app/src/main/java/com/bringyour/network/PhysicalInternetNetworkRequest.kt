package com.bringyour.network

import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Matches usable physical internet paths without inheriting platform defaults.
 *
 * Android has added restrictive default capabilities over time (notably
 * NOT_METERED on Android 15). Starting empty keeps cellular, constrained, and
 * future valid paths eligible while explicitly excluding this VPN itself.
 */
internal fun physicalInternetNetworkRequestBuilder(): NetworkRequest.Builder {
    return NetworkRequest.Builder()
        .clearCapabilities()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
}
