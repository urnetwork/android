package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class IpFamilyTest {

    @Test
    fun theValuesAreTheOnesTheSdkDefines() {
        // Sdk.IpFamilyPolicyAuto / Force4 / Force6 (sdk/sdk.go). Every other
        // assertion here is expressed in terms of these constants, so without
        // this one the whole file still passes with FORCE_4 and FORCE_6
        // swapped -- the row would force the wrong family, and Automatic
        // would be unreachable, with a green test suite. The literals are
        // written out so a reviewer can diff them against the Go source.
        //
        // Literals rather than Sdk.IpFamilyPolicy*: this is a JVM unit test
        // and touching the gomobile class would try to load gojni.
        assertEquals(0L, IP_FAMILY_AUTO)
        assertEquals(1L, IP_FAMILY_FORCE_4)
        assertEquals(2L, IP_FAMILY_FORCE_6)
    }

    @Test
    fun clampsOutOfRangeToAuto() {
        assertEquals(IP_FAMILY_AUTO, clampIpFamilyPolicy(-1L))
        assertEquals(IP_FAMILY_AUTO, clampIpFamilyPolicy(7L))
        assertEquals(IP_FAMILY_FORCE_4, clampIpFamilyPolicy(IP_FAMILY_FORCE_4))
        assertEquals(IP_FAMILY_FORCE_6, clampIpFamilyPolicy(IP_FAMILY_FORCE_6))
    }

    @Test
    fun cyclesAutoForce4Force6AndBack() {
        assertEquals(IP_FAMILY_FORCE_4, nextIpFamilyPolicy(IP_FAMILY_AUTO))
        assertEquals(IP_FAMILY_FORCE_6, nextIpFamilyPolicy(IP_FAMILY_FORCE_4))
        assertEquals(IP_FAMILY_AUTO, nextIpFamilyPolicy(IP_FAMILY_FORCE_6))
    }

    // Parity with ios IpFamilyTests.autoDetailReportsALearnedDemotion: the
    // detail must distinguish auto-with-nothing-learned from
    // auto-with-a-demotion, or the row looks the same either way.
    @Test
    fun autoDetailResourceDiffersWhenSomethingIsDemoted() {
        assertNotEquals(
            ipFamilyDetailResource(IP_FAMILY_AUTO, status = ""),
            ipFamilyDetailResource(IP_FAMILY_AUTO, status = "IPv6 demoted for 4m (2 strikes)"),
        )
    }
}
