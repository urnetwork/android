package com.bringyour.network

import android.annotation.SuppressLint
import android.net.ConnectivityManager
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

// Stable platform capability id since API 31, hidden from the public SDK.
// NetworkRequest.Builder adds it to app requests by itself unless
// clearCapabilities() ran, which the membership request does on purpose.
internal const val NET_CAPABILITY_NOT_VCN_MANAGED_COMPAT = 28

/**
 * The best match for this request is the physical network this app's unbound
 * sockets leave on: the system default. It adds NOT_VCN_MANAGED on API 31+,
 * which the system default request carries; without it a carrier VCN's
 * underlying network could rank best while traffic leaves through the VCN.
 * The membership request above keeps those networks eligible on purpose.
 */
@SuppressLint("WrongConstant")
internal fun physicalDefaultNetworkRequestBuilder(): NetworkRequest.Builder {
    val builder = physicalInternetNetworkRequestBuilder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED_COMPAT)
    }
    return builder
}

/**
 * Whether a network the membership request matches is connected right now.
 * Answers true when the platform cannot be asked, so a registration keeps
 * treating its first delivery as the current state, as it did before.
 */
@Suppress("DEPRECATION")
internal fun hasPhysicalInternetNetwork(connectivityManager: ConnectivityManager): Boolean {
    return runCatching {
        connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            } == true
        }
    }.getOrDefault(true)
}
