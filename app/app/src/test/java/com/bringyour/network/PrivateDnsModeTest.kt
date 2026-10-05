package com.bringyour.network

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The physical network's Private DNS mode from the LinkProperties signals
 * (P021), its log value, and when the strict mode notice shows.
 */
class PrivateDnsModeTest {
    @Test
    fun hostnameWhileActiveIsStrict() {
        assertEquals(
            PrivateDnsMode.Strict("dns.example"),
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = "dns.example"),
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
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = false, privateDnsServerName = "dns.example"),
        )
    }

    @Test
    fun strictHostIsTrimmed() {
        assertEquals(
            PrivateDnsMode.Strict("dns.example"),
            privateDnsModeOf(Build.VERSION_CODES.TIRAMISU, isPrivateDnsActive = true, privateDnsServerName = "  dns.example  "),
        )
    }

    @Test
    fun belowApi28IsAlwaysOff() {
        // isPrivateDnsActive / privateDnsServerName do not exist before P.
        assertEquals(
            PrivateDnsMode.Off,
            privateDnsModeOf(Build.VERSION_CODES.O_MR1, isPrivateDnsActive = true, privateDnsServerName = "dns.example"),
        )
    }

    @Test
    fun logValueTagsEachMode() {
        assertEquals("off", PrivateDnsMode.Off.logValue())
        assertEquals("opportunistic", PrivateDnsMode.Opportunistic.logValue())
        assertEquals("strict(dns.example)", PrivateDnsMode.Strict("dns.example").logValue())
    }

    @Test
    fun noticeHostOnlyWhenConnectedAndStrict() {
        assertEquals("dns.example", privateDnsStrictNoticeHost(PrivateDnsMode.Strict("dns.example"), connected = true))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Strict("dns.example"), connected = false))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Opportunistic, connected = true))
        assertNull(privateDnsStrictNoticeHost(PrivateDnsMode.Off, connected = true))
    }
}
