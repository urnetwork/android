package com.bringyour.network.ui.login

import com.bringyour.network.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkServiceUrlTest {

    @Test
    fun theBundledHostDerivesTheOperatorServiceUrls() {
        // what the change-network dialog shows as the current and placeholder
        // endpoints for the official network
        assertEquals(
            "https://api.bringyour.com",
            derivedServiceUrl(
                BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME,
                BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME,
                BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME,
                "https",
                "api",
            ),
        )
        assertEquals(
            "wss://connect.bringyour.com",
            derivedServiceUrl(
                BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME,
                BuildConfig.BRINGYOUR_BUNDLE_MIGRATION_HOST_NAME,
                BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME,
                "wss",
                "connect",
            ),
        )
    }

    @Test
    fun aBlankMigrationHostUsesTheHostItself() {
        assertEquals("https://api.bringyour.com", derivedServiceUrl("bringyour.com", "", "main", "https", "api"))
        assertEquals("https://api.bringyour.com", derivedServiceUrl("bringyour.com", "", "", "https", "api"))
        assertEquals("https://beta-api.bringyour.com", derivedServiceUrl("bringyour.com", "", "beta", "https", "api"))
    }

    @Test
    fun aMigrationHostStillRedirectsTheServices() {
        assertEquals(
            "https://api.legacy.example",
            derivedServiceUrl("vpn.example", "legacy.example", "main", "https", "api"),
        )
        assertEquals(
            "wss://beta-connect.legacy.example",
            derivedServiceUrl("vpn.example", "legacy.example", "beta", "wss", "connect"),
        )
    }
}
