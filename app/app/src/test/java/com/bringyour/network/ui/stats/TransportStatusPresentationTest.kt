package com.bringyour.network.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic status-presentation matrix of TRANSPORTSTATUS.md, over the
 * generic core (plain strings: the sdk-backed types are native and not
 * loadable on the JVM). The adapter over the view model types only forwards
 * fields.
 */
class TransportStatusPresentationTest {

    private val auto = listOf("h3", "h1", "dns", "dnspump")

    private fun compute(
        isAuto: Boolean = true,
        draftMatchesStatusPolicy: Boolean = true,
        autoTransports: List<String> = auto,
        statusKnown: Boolean = true,
        autoDegraded: Boolean = true,
        autoEligibleTransports: Set<String> = setOf("h1"),
        autoConstraint: String = TransportStatusPresentation.CONSTRAINT_MEMORY,
    ): TransportStatusPresentation<String> {
        return TransportStatusPresentation.compute(
            isAuto = isAuto,
            draftMatchesStatusPolicy = draftMatchesStatusPolicy,
            autoTransports = autoTransports,
            statusKnown = statusKnown,
            autoDegraded = autoDegraded,
            autoEligibleTransports = autoEligibleTransports,
            autoConstraint = autoConstraint,
        )
    }

    @Test
    fun healthyStatusShowsNoDecorations() {
        val presentation = compute(autoDegraded = false, autoEligibleTransports = auto.toSet())
        assertEquals(TransportStatusPresentation.hidden<String>(), presentation)
    }

    @Test
    fun degradedStatusMarksEnabledIneligibleTransports() {
        val presentation = compute()
        assertTrue(presentation.showBanner)
        assertTrue(presentation.memoryConstraint)
        assertEquals(setOf("h3", "dns", "dnspump"), presentation.constrainedTransports)
    }

    @Test
    fun autoDisabledTransportIsNeverConstrained() {
        // h3 disabled in the policy and absent from the eligible modes: no
        // indicator on h3, only on the enabled-but-ineligible carriers
        val presentation = compute(autoTransports = listOf("h1", "dns", "dnspump"))
        assertTrue(presentation.showBanner)
        assertEquals(setOf("dns", "dnspump"), presentation.constrainedTransports)
    }

    @Test
    fun explicitModeHidesAutoStatus() {
        val presentation = compute(isAuto = false)
        assertEquals(TransportStatusPresentation.hidden<String>(), presentation)
    }

    @Test
    fun unknownStatusShowsNoDecorations() {
        val presentation = compute(statusKnown = false)
        assertEquals(TransportStatusPresentation.hidden<String>(), presentation)
    }

    @Test
    fun dirtyDraftHidesStatusUntilItMatchesTheAppliedPolicy() {
        val edited = compute(draftMatchesStatusPolicy = false)
        assertEquals(TransportStatusPresentation.hidden<String>(), edited)
        val restored = compute(draftMatchesStatusPolicy = true)
        assertTrue(restored.showBanner)
    }

    @Test
    fun unknownConstraintUsesGenericCopy() {
        val presentation = compute(autoConstraint = "quantum")
        assertTrue(presentation.showBanner)
        assertFalse(presentation.memoryConstraint)
    }

    @Test
    fun unknownEligibleVocabularyKeepsTheBannerRenderable() {
        // the authoritative degraded flag renders the banner even when the
        // eligible list carries only vocabulary this app does not know
        val presentation = compute(autoEligibleTransports = setOf("warp9"))
        assertTrue(presentation.showBanner)
        assertEquals(auto.toSet(), presentation.constrainedTransports)
    }
}
