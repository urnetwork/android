package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedProviderCountTest {

    @Test
    fun fetchedCountReplacesTheCountCopiedAtSelection() {
        // the location was selected with 12 providers; a later fetch has 40
        val count = selectedProviderCount(
            selectedId = "country:FR",
            selectedCount = 12,
            fetched = sequenceOf("country:DE" to 7, "country:FR" to 40),
        )
        assertEquals(40, count)
    }

    @Test
    fun selectionCountWhenTheLocationIsNotInTheFetchedList() {
        val count = selectedProviderCount(
            selectedId = "country:FR",
            selectedCount = 12,
            fetched = sequenceOf("country:DE" to 7),
        )
        assertEquals(12, count)
    }

    @Test
    fun selectionCountWhenNothingIsSelected() {
        assertEquals(0, selectedProviderCount(null, 0, sequenceOf("country:DE" to 7)))
    }
}
