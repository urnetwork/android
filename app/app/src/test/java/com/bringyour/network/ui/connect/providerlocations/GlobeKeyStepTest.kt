package com.bringyour.network.ui.connect.providerlocations

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Test

/** The arrows on the focused provider globe step its wheel as a drag does. */
class GlobeKeyStepTest {

    @Test
    fun rightStepsEastAndLeftWest() {
        // positive east, as a drag to the right
        assertEquals(1, globeKeyStep(Key.DirectionRight))
        assertEquals(-1, globeKeyStep(Key.DirectionLeft))
    }

    @Test
    fun otherKeysStepNothing() {
        // Up and Down go on to the rows below the globe
        assertEquals(0, globeKeyStep(Key.DirectionUp))
        assertEquals(0, globeKeyStep(Key.DirectionDown))
        assertEquals(0, globeKeyStep(Key.Enter))
        assertEquals(0, globeKeyStep(Key.Tab))
    }
}
