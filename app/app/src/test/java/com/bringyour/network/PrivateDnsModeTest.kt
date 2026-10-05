package com.bringyour.network

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateDnsModeTest {
    @Test
    fun hostnameWhileActiveIsStrict() {
        assertEquals(
            PrivateDnsMode.Strict("dns.quad9.net"),
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = "dns.quad9.net"),
        )
    }

    @Test
    fun activeWithNoHostnameIsOpportunistic() {
        assertEquals(
            PrivateDnsMode.Opportunistic,
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = null),
        )
    }

    @Test
    fun blankHostnameWhileActiveIsOpportunistic() {
        // A whitespace-only server name is not a configured host.
        assertEquals(
            PrivateDnsMode.Opportunistic,
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = "   "),
        )
    }

    @Test
    fun inactiveIsOff() {
        assertEquals(
            PrivateDnsMode.Off,
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = false, privateDnsServerName = "dns.quad9.net"),
        )
    }

    @Test
    fun strictHostIsTrimmed() {
        assertEquals(
            PrivateDnsMode.Strict("dns.quad9.net"),
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = "  dns.quad9.net  "),
        )
    }

    @Test
    fun belowApi28IsAlwaysOff() {
        // isPrivateDnsActive / privateDnsServerName do not exist before P.
        assertEquals(
            PrivateDnsMode.Off,
            privateDnsModeOf(Build.VERSION_CODES.O_MR1, isPrivateDnsActive = true, privateDnsServerName = "dns.quad9.net"),
        )
    }

    @Test
    fun logValueTagsEachMode() {
        assertEquals("off", PrivateDnsMode.Off.logValue())
        assertEquals("opportunistic", PrivateDnsMode.Opportunistic.logValue())
        assertEquals("strict(dns.quad9.net)", PrivateDnsMode.Strict("dns.quad9.net").logValue())
    }

    @Test
    fun noticeHostOnlyWhenConnectedAndStrict() {
        assertEquals("dns.quad9.net", privateDnsStrictNoticeHost(PrivateDnsMode.Strict("dns.quad9.net"), connected = true))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Strict("dns.quad9.net"), connected = false))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Opportunistic, connected = true))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Off, connected = true))
    }
}
