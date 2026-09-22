package com.bringyour.network.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitRuleHostInputTest {

    @Test
    fun plainNamesAreAccepted() {
        assertEquals("example.com", SplitRuleHostInput.validate("example.com").normalized)
        assertEquals("a1366.dscapi6.akamai.net", SplitRuleHostInput.validate("a1366.dscapi6.akamai.net").normalized)
        assertEquals("my-host.example.co.uk", SplitRuleHostInput.validate("my-host.example.co.uk").normalized)
    }

    @Test
    fun inputIsTrimmedAndLowercased() {
        assertEquals("example.com", SplitRuleHostInput.validate("  Example.COM  ").normalized)
    }

    @Test
    fun unicodeNamesAreRefused() {
        assertEquals(SplitRuleHostError.NotAscii, SplitRuleHostInput.validate("münchen.de").error)
    }

    @Test
    fun singleLabelNamesAreRefused() {
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("localhost").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("router").error)
    }

    @Test
    fun malformedNamesAreRefused() {
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("example..com").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("-example.com").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("example-.com").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("example.com.").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("exa mple.com").error)
    }

    @Test
    fun wildcardsAreAccepted() {
        assertEquals("*.example.com", SplitRuleHostInput.validate("*.example.com").normalized)
        assertEquals("**.example.com", SplitRuleHostInput.validate("**.example.com").normalized)
    }

    @Test
    fun wildcardsNeedAName() {
        assertEquals(SplitRuleHostError.BadWildcard, SplitRuleHostInput.validate("*.").error)
        assertEquals(SplitRuleHostError.BadWildcard, SplitRuleHostInput.validate("**.").error)
        assertEquals(SplitRuleHostError.BadWildcard, SplitRuleHostInput.validate("*.com").error)
    }

    @Test
    fun addressesAreAccepted() {
        assertEquals("1.2.3.4", SplitRuleHostInput.validate("1.2.3.4").normalized)
        assertEquals("2001:db8::1", SplitRuleHostInput.validate("2001:db8::1").normalized)
    }

    @Test
    fun mappedAddressesAreUnmapped() {
        assertEquals("1.2.3.4", SplitRuleHostInput.validate("::ffff:1.2.3.4").normalized)
    }

    @Test
    fun rangesAreMaskedToTheirNetwork() {
        val validation = SplitRuleHostInput.validate("192.168.1.42/24")
        assertEquals("192.168.1.0/24", validation.normalized)
        assertEquals("192.168.1.0/24", validation.note)
    }

    @Test
    fun rangesThatEndInsideAByteAreMasked() {
        assertEquals("10.0.0.0/12", SplitRuleHostInput.validate("10.1.2.3/12").normalized)
        assertEquals("192.168.1.128/25", SplitRuleHostInput.validate("192.168.1.130/25").normalized)
        assertEquals("192.0.0.0/2", SplitRuleHostInput.validate("192.168.1.42/2").normalized)
        assertEquals("2001:db8::/36", SplitRuleHostInput.validate("2001:db8::1/36").normalized)
    }

    @Test
    fun everyPrefixLengthIsSurvivable() {
        for (bits in 0..32) {
            SplitRuleHostInput.validate("192.168.1.42/$bits")
        }
        for (bits in 0..128) {
            SplitRuleHostInput.validate("2001:db8::1/$bits")
        }
    }

    @Test
    fun alreadyMaskedRangesCarryNoNote() {
        val validation = SplitRuleHostInput.validate("10.0.0.0/8")
        assertEquals("10.0.0.0/8", validation.normalized)
        assertNull(validation.note)
    }

    @Test
    fun malformedRangesAreRefused() {
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("10.0.0.0/").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("10.0.0.0/33").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("example.com/24").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("2001:db8::/129").error)
    }

    @Test
    fun anEmptyFieldIsNeitherAcceptedNorAnError() {
        val validation = SplitRuleHostInput.validate("   ")
        assertNull(validation.normalized)
        assertNull(validation.error)
        assertFalse(validation.isAccepted)
    }

    @Test
    fun duplicatesAreRefused() {
        assertEquals(
            SplitRuleHostError.Duplicate,
            SplitRuleHostInput.validate("example.com", listOf("example.com")).error
        )
        assertEquals(
            SplitRuleHostError.Duplicate,
            SplitRuleHostInput.validate("EXAMPLE.com", listOf("example.com")).error
        )
    }

    @Test
    fun valuesAlreadyCoveredByAWildcardAreRefused() {
        assertEquals(
            SplitRuleHostError.Covered("*.example.com"),
            SplitRuleHostInput.validate("a.example.com", listOf("*.example.com")).error
        )
        assertEquals(
            SplitRuleHostError.Covered("**.example.com"),
            SplitRuleHostInput.validate("example.com", listOf("**.example.com")).error
        )
        assertTrue(SplitRuleHostInput.validate("example.com", listOf("*.example.com")).isAccepted)
        assertTrue(SplitRuleHostInput.validate("example.org", listOf("*.example.com")).isAccepted)
    }

    @Test
    fun everyRejectionHasSomethingToSay() {
        val errors: List<SplitRuleHostError> = listOf(
            SplitRuleHostError.NotAscii,
            SplitRuleHostError.BadName,
            SplitRuleHostError.BadWildcard,
            SplitRuleHostError.BadRange,
            SplitRuleHostError.Duplicate,
            SplitRuleHostError.Covered("*.example.com"),
        )
        for (error in errors) {
            assertTrue(SplitRuleHostInput.message(error).isNotEmpty())
        }
    }

    @Test
    fun signedOrPaddedNumbersAreRefused() {
        // read as numbers, these would be "normalized" into an address or
        // range other than the one typed
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("+1.2.3.4").error)
        assertEquals(SplitRuleHostError.BadName, SplitRuleHostInput.validate("-1::").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("10.0.0.0/+8").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("10.0.0.0/08").error)
        assertEquals(SplitRuleHostError.BadRange, SplitRuleHostInput.validate("+1::/64").error)
    }

    @Test
    fun embeddedIpv4AfterDoubleColonIsAccepted() {
        assertEquals("::102:304", SplitRuleHostInput.validate("::1.2.3.4").normalized)
        assertEquals("1::102:304", SplitRuleHostInput.validate("1::1.2.3.4").normalized)
        assertEquals("1.2.3.4", SplitRuleHostInput.validate("::ffff:1.2.3.4").normalized)
    }
}
