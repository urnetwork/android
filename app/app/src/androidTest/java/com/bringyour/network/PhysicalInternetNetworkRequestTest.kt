package com.bringyour.network

import android.annotation.TargetApi
import android.net.NetworkCapabilities
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

@TargetApi(28)
class PhysicalInternetNetworkRequestTest {
    private fun assumeRequestInspectionSupported() {
        assumeTrue(Build.VERSION.SDK_INT >= 28)
    }

    @Test
    fun physicalInternetRequestAllowsMeteredNetworks() {
        assumeRequestInspectionSupported()
        val request = physicalInternetNetworkRequestBuilder().build()

        assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
    }

    @Test
    fun physicalInternetRequestAllowsBandwidthConstrainedNetworks() {
        assumeRequestInspectionSupported()
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
}
