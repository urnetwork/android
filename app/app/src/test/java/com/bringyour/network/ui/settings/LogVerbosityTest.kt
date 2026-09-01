package com.bringyour.network.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verbosity control's decisions, as pure functions.
 *
 * What this pins is the part that can be wrong without anything failing: the
 * sdk's setter throws nothing, clamps silently and can be refused outright by
 * the device, so the screen's honesty rests entirely on how a level READ BACK
 * from the device is interpreted here.
 */
class LogVerbosityTest {

    @Test
    fun theLevelsAreTheThreeTheSdkDefines() {
        // Sdk.LogVerbosityDefault / Verbose / Trace. Not an arbitrary list:
        // `connect` gates its diagnostics at V(1) and V(2) only, so anything
        // above 2 is volume with nothing new to show, and the sdk clamps it
        assertEquals(listOf(0L, 1L, 2L), LOG_VERBOSITY_PRESETS)
        assertEquals(0L, LOG_VERBOSITY_DEFAULT)
        assertEquals(1L, LOG_VERBOSITY_VERBOSE)
        assertEquals(2L, LOG_VERBOSITY_TRACE)
    }

    @Test
    fun tappingCyclesUpAndWrapsBackToDefault() {
        assertEquals(LOG_VERBOSITY_VERBOSE, nextLogVerbosity(LOG_VERBOSITY_DEFAULT))
        assertEquals(LOG_VERBOSITY_TRACE, nextLogVerbosity(LOG_VERBOSITY_VERBOSE))
        // wrapping back to 0 is the only way off Trace, and Trace is the level
        // that fills the disk -- it must not be a dead end
        assertEquals(LOG_VERBOSITY_DEFAULT, nextLogVerbosity(LOG_VERBOSITY_TRACE))
    }

    @Test
    fun steppingFromAnUnknownLevelLandsOnDefault() {
        // GetLogVerbosity reports the -v flag itself, not a clamped shadow
        // copy, so a level outside the sdk's range is reachable when one was
        // set another way. Stepping from it has to go somewhere predictable
        // rather than throw out of a click handler.
        assertEquals(LOG_VERBOSITY_DEFAULT, nextLogVerbosity(7L))
        assertEquals(LOG_VERBOSITY_DEFAULT, nextLogVerbosity(-3L))
    }

    @Test
    fun aLevelWithNoDeviceIsUnknownRatherThanDefault() {
        // "there is no device to ask" and "the device is at level 0" are
        // different claims. Reporting the second for the first is exactly the
        // silent assumption this control exists to avoid: it would show
        // "Default" for a device that may be running at Trace.
        assertNull(logVerbosityLevel(null))
        assertEquals(LogVerbosityLevel.DEFAULT, logVerbosityLevel(0L))
    }

    @Test
    fun anOutOfRangeLevelIsNamedForWhatItActuallyLogs() {
        // at -v=5 every V(2) statement fires, so calling it anything but Trace
        // would understate what the logs now contain
        assertEquals(LogVerbosityLevel.TRACE, logVerbosityLevel(5L))
        // and a negative flag logs nothing V-gated at all
        assertEquals(LogVerbosityLevel.DEFAULT, logVerbosityLevel(-1L))
        assertEquals(LogVerbosityLevel.VERBOSE, logVerbosityLevel(1L))
        assertEquals(LogVerbosityLevel.TRACE, logVerbosityLevel(2L))
    }

    @Test
    fun theValueLabelReportsTheLevelTheDeviceGave() {
        // the name says what the row means; the number is what the sdk reports
        // and what a support thread compares against a bundle's manifest
        assertEquals("0 · Default", logVerbosityValueLabel(LOG_VERBOSITY_DEFAULT, "Default"))
        assertEquals("1 · Verbose", logVerbosityValueLabel(LOG_VERBOSITY_VERBOSE, "Verbose"))
        assertEquals("2 · Trace", logVerbosityValueLabel(LOG_VERBOSITY_TRACE, "Trace"))
        // a level set past the sdk's range is reported as the number it
        // actually is rather than quietly redrawn as 2 -- this row is the only
        // place the discrepancy could show
        assertEquals("7 · Trace", logVerbosityValueLabel(7L, "Trace"))
    }

    @Test
    fun theDestinationWarningIsShownAtEveryLevelThatRecordsThem() {
        // the difference between a bundle that is safe to attach to a support
        // thread and one carrying every site the user visited. V(1) is where
        // the per-packet block decisions -- with destination addresses and
        // ports -- start being written.
        assertFalse(logVerbosityRecordsDestinations(LOG_VERBOSITY_DEFAULT))
        assertTrue(logVerbosityRecordsDestinations(LOG_VERBOSITY_VERBOSE))
        assertTrue(logVerbosityRecordsDestinations(LOG_VERBOSITY_TRACE))
        // an out-of-range level logs strictly more, never less
        assertTrue(logVerbosityRecordsDestinations(9L))
    }

    @Test
    fun noWarningIsClaimedForALevelThatWasNeverReadBack() {
        // the warning is keyed off the device's answer, so with no device
        // there is no claim to make in either direction
        assertFalse(logVerbosityRecordsDestinations(null))
    }
}
