package com.bringyour.network.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LazyListKeysTest {
    @Test
    fun emptyBackendIdentifiersRemainUnique() {
        val keys = List(100) { index ->
            indexedLazyListKey("leaderboard", index, "")
        }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun duplicateBackendIdentifiersRemainUnique() {
        val first = indexedLazyListKey("peers", 0, "duplicate")
        val second = indexedLazyListKey("peers", 1, "duplicate")

        assertNotEquals(first, second)
    }

    @Test
    fun nullBackendIdentifiersRemainUnique() {
        val first = indexedLazyListKey("contracts", 0, null)
        val second = indexedLazyListKey("contracts", 1, null)

        assertNotEquals(first, second)
    }

    @Test
    fun sectionNamespacesPreventCrossSectionCollisions() {
        val country = indexedLazyListKey("country", 0, "")
        val city = indexedLazyListKey("city", 0, "")

        assertNotEquals(country, city)
    }

    @Test
    fun theSameInputsProduceTheSameKey() {
        val first = indexedLazyListKey("wallet", 7, "id")
        val second = indexedLazyListKey("wallet", 7, "id")

        assertEquals(first, second)
        assertTrue(first.endsWith(":id"))
    }
}
