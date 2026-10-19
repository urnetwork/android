package com.bringyour.network.ui.connect

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationsRefreshTickerTest {

    private class Fixture(scope: TestScope) {
        var fetches = 0
        var fetching = false
        val ticker = LocationsRefreshTicker(
            scope = scope.backgroundScope,
            intervalMillis = 60_000,
            isFetching = { fetching },
            refresh = { fetches += 1 },
        )
    }

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun refreshesAtTheIntervalWhileVisible() = runTest {
        val f = Fixture(this)
        f.ticker.setVisible(true)
        advance(59_999)
        assertEquals(0, f.fetches)
        advance(1)
        assertEquals(1, f.fetches)
        advance(120_000)
        assertEquals(3, f.fetches)
    }

    @Test
    fun stopsWhenHiddenAndResumesWhenVisible() = runTest {
        val f = Fixture(this)
        f.ticker.setVisible(true)
        advance(60_000)
        assertEquals(1, f.fetches)

        f.ticker.setVisible(false)
        advance(600_000)
        assertEquals(1, f.fetches)

        f.ticker.setVisible(true)
        advance(60_000)
        assertEquals(2, f.fetches)
    }

    @Test
    fun visibleTwiceDoesNotDoubleTheRate() = runTest {
        val f = Fixture(this)
        f.ticker.setVisible(true)
        f.ticker.setVisible(true)
        advance(60_000)
        assertEquals(1, f.fetches)
    }

    @Test
    fun skipsATickWhileAFetchIsInFlight() = runTest {
        val f = Fixture(this)
        f.ticker.setVisible(true)
        f.fetching = true
        advance(120_000)
        assertEquals(0, f.fetches)

        f.fetching = false
        advance(60_000)
        assertEquals(1, f.fetches)
    }
}
