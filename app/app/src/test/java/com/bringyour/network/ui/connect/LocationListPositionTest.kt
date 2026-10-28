package com.bringyour.network.ui.connect

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationListPositionTest {

    @Test
    fun theScrollPositionIsSavedWithTheScreen() {
        // the location list keeps its place when a fold recreates the screen,
        // or turns the picker's screen into the dialog beside the panes
        val scope = SaverScope { true }
        val saved = with(LocationListPosition.Saver) { scope.save(LocationListPosition(index = 12, offset = 34)) }
        val restored = LocationListPosition.Saver.restore(saved!!)!!
        assertEquals(12, restored.index)
        assertEquals(34, restored.offset)
    }

    @Test
    fun aShortSaveRestoresTheTop() {
        val restored = LocationListPosition.Saver.restore(listOf())!!
        assertEquals(0, restored.index)
        assertEquals(0, restored.offset)
    }
}
