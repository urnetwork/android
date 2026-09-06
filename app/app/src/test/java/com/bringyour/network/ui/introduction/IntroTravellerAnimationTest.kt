package com.bringyour.network.ui.introduction

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class IntroTravellerAnimationTest {
    @Test
    fun everyFrameUsesTheInfiniteAnimationPolicy() {
        val policy = StopAfterThirdInfiniteOperation()
        val clock = FixedFrameClock(0L, 6_500_000_000L)
        val updates = mutableListOf<IntroTravellerFrame>()

        val stopped = try {
            runBlocking(clock + policy) {
                runIntroTravellerAnimation(updates::add)
            }
            null
        } catch (error: ExpectedAnimationStop) {
            error
        }

        assertNotNull("the test policy did not intercept the third frame operation", stopped)
        assertEquals(3, policy.operationCount)
        assertEquals(listOf(IntroTravellerFrame(trip = 0f, tripCount = 1)), updates)
        assertEquals(2, clock.frameCount)
    }

    private class FixedFrameClock(vararg frameTimesNanos: Long) : MonotonicFrameClock {
        private val times = ArrayDeque(frameTimesNanos.toList())
        var frameCount = 0
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameCount += 1
            return onFrame(times.removeFirst())
        }
    }

    private class StopAfterThirdInfiniteOperation : InfiniteAnimationPolicy {
        var operationCount = 0
            private set

        override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
            operationCount += 1
            if (operationCount == 3) throw ExpectedAnimationStop()
            return block()
        }
    }

    private class ExpectedAnimationStop : RuntimeException()
}
