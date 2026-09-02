package com.bringyour.network

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

/**
 * Matches usable physical internet paths without inheriting newer restrictive
 * platform defaults where the API supports clearing them.
 *
 * clearCapabilities() was added in API 30, despite this app supporting API 26.
 * API 26-29 already default to the three explicit safety capabilities below,
 * so leaving those defaults in place is equivalent and avoids NoSuchMethodError.
 */
internal fun physicalInternetNetworkRequestBuilder(): NetworkRequest.Builder {
    val builder = NetworkRequest.Builder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.clearCapabilities()
    }
    return builder
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
}
