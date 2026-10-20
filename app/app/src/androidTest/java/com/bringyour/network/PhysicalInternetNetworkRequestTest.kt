package com.bringyour.network

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.net.NetworkCapabilities
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PhysicalInternetNetworkRequestTest {
    private fun assumeRequestInspectionSupported() {
        assumeTrue(Build.VERSION.SDK_INT >= 28)
    }

    @Test
    fun physicalInternetRequestBuildsAcrossTheSupportedApiRange() {
        // In particular, API 26-29 must not invoke Builder.clearCapabilities(),
        // which does not exist until API 30.
        assertNotNull(physicalInternetNetworkRequestBuilder().build())
    }

    @Test
    fun physicalInternetRequestAllowsMeteredNetworks() {
        assumeRequestInspectionSupported()
        val request = physicalInternetNetworkRequestBuilder().build()

        assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    @Test
    @TargetApi(36)
    fun physicalInternetRequestAllowsBandwidthConstrainedNetworks() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        val request = physicalInternetNetworkRequestBuilder().build()

        assertFalse(
            request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_BANDWIDTH_CONSTRAINED)
        )
    }

    @Test
    fun physicalInternetRequestExcludesVpnNetworks() {
        assumeRequestInspectionSupported()
        val request = physicalInternetNetworkRequestBuilder().build()

        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
    }

    @Test
    fun physicalInternetRequestRequiresGeneralTrustedInternet() {
        assumeRequestInspectionSupported()
        val request = physicalInternetNetworkRequestBuilder().build()

        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED))
    }

    @Test
    fun physicalDefaultRequestBuildsAcrossTheSupportedApiRange() {
        assertNotNull(physicalDefaultNetworkRequestBuilder().build())
    }

    @Test
    @SuppressLint("WrongConstant")
    fun physicalDefaultRequestMirrorsTheSystemDefaultWithoutVpns() {
        // the best match must be the system default, never this app's tunnel,
        // and on API 31+ never a carrier VCN's underlying network
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        val request = physicalDefaultNetworkRequestBuilder().build()

        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))
        assertTrue(request.hasCapability(NET_CAPABILITY_NOT_VCN_MANAGED_COMPAT))
    }
}
