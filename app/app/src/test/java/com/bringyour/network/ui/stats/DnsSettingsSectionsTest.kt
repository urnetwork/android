package com.bringyour.network.ui.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsSettingsSectionsTest {

    @Test
    fun connectionRegionShowsBeforeTheTrafficDnsReport() {
        // the region comes from the network space, not the dns report
        val sections = dnsSettingsSections(null)
        assertTrue(sections.connectionRegion)
        assertFalse(sections.trafficDns)
    }

    @Test
    fun bothShowOnceTheTrafficDnsReportArrives() {
        val sections = dnsSettingsSections(DnsSettingsUi())
        assertTrue(sections.connectionRegion)
        assertTrue(sections.trafficDns)
    }
}
