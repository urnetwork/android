package com.bringyour.network.ui.settings

import com.bringyour.network.ui.components.LoginMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudProxyTest {
    @Test
    fun urlUsesTheLinkHost() {
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl("ur.io"))
    }

    @Test
    fun blankLinkHostFallsBackToDefault() {
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl(""))
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl(null))
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl("   "))
    }

    @Test
    fun selfHostedLinkHostIsUsed() {
        assertEquals("https://example.org/app/proxies", CloudProxy.proxiesUrl("example.org"))
    }

    @Test
    fun oneTimeAuthCodeIsAppended() {
        assertEquals(
            "https://ur.io/app/proxies?auth_code=abc123",
            CloudProxy.proxiesUrl("ur.io", "abc123"),
        )
    }

    @Test
    fun blankAuthCodeIsOmitted() {
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl("ur.io", ""))
        assertEquals("https://ur.io/app/proxies", CloudProxy.proxiesUrl("ur.io", "   "))
    }

    @Test
    fun entryShownOnlyWhenAuthenticated() {
        assertTrue(CloudProxy.entryVisible(LoginMode.Authenticated))
        assertFalse(CloudProxy.entryVisible(LoginMode.Guest))
    }
}
