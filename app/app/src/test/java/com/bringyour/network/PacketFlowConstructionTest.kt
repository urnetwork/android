package com.bringyour.network

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the actual PacketFlow constructor, not a copied lifecycle model.
 * The factory forces the ordering permitted by Go's worker-before-return
 * constructor. It does not emulate packet I/O or claim a device-pause cause.
 */
class PacketFlowConstructionTest {
    private class NativeHandle {
        lateinit var done: () -> Unit
        var cancelCalls = 0

        fun create(done: () -> Unit): () -> Unit {
            this.done = done
            return { cancelCalls++ }
        }
    }

    @Test
    fun nativeDoneBeforeFactoryReturnDoesNotTouchUnpublishedHandle() {
        val ended = mutableListOf<PacketFlow>()
        var factoryReturned = false
        var cancelCalls = 0
        val flow = PacketFlow(
            createIoLoop = { done ->
                done()
                assertEquals(1, ended.size)
                assertFalse(factoryReturned)
                factoryReturned = true
                val close: () -> Unit = { cancelCalls++ }
                close
            },
            endCallback = { endedFlow ->
                assertFalse(endedFlow.isActive())
                ended.add(endedFlow)
            },
        )

        assertTrue(factoryReturned)
        assertSame(flow, ended.single())
        assertFalse(flow.isActive())
        assertEquals(0, cancelCalls)
        flow.close()
        assertEquals(1, cancelCalls)
    }

    @Test
    fun nativeDoneAfterPublicationDoesNotRecancelTheWorker() {
        val native = NativeHandle()
        val ended = mutableListOf<PacketFlow>()
        val flow = PacketFlow(native::create) { ended.add(it) }
        assertTrue(flow.isActive())

        native.done()

        assertFalse(flow.isActive())
        assertSame(flow, ended.single())
        assertEquals(0, native.cancelCalls)
    }

    @Test
    fun explicitCloseStillCancelsPublishedHandleBeforeAndAfterDone() {
        val native = NativeHandle()
        val ended = mutableListOf<PacketFlow>()
        val flow = PacketFlow(native::create) { ended.add(it) }

        flow.close()
        assertFalse(flow.isActive())
        assertEquals(1, native.cancelCalls)
        assertTrue(ended.isEmpty())
        native.done()
        assertSame(flow, ended.single())
        assertEquals(1, native.cancelCalls)
        // Preserve existing repeated external-close semantics; Go cancel is idempotent.
        flow.close()
        assertEquals(2, native.cancelCalls)
    }

    @Test(timeout = 10_000)
    fun concurrentCloseAndNativeDoneDoNotHoldStateLockAcrossCancellation() {
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val nativeDone = AtomicReference<() -> Unit>()
        val cancelCalls = AtomicInteger()
        val endCalls = AtomicInteger()
        val workerFailure = AtomicReference<Throwable?>()
        val flow = PacketFlow(
            createIoLoop = { done ->
                nativeDone.set(done)
                val close: () -> Unit = {
                    cancelCalls.incrementAndGet()
                    closeEntered.countDown()
                    releaseClose.await()
                }
                close
            },
            endCallback = { ended ->
                assertFalse(ended.isActive())
                endCalls.incrementAndGet()
            },
        )
        val closer = Thread {
            try {
                flow.close()
            } catch (error: Throwable) {
                workerFailure.set(error)
            }
        }
        closer.start()
        try {
            assertTrue("external cancellation did not enter", closeEntered.await(5, TimeUnit.SECONDS))
            // The close action is held. Completion and its active-state read must
            // return without waiting for that action or taking another owner.
            nativeDone.get().invoke()
            assertEquals(1, endCalls.get())
            assertEquals(1, cancelCalls.get())
            assertFalse(flow.isActive())
        } finally {
            releaseClose.countDown()
            closer.join(5_000)
            if (closer.isAlive) {
                closer.interrupt()
                closer.join(5_000)
            }
            assertFalse("test cancellation owner did not join", closer.isAlive)
        }
        workerFailure.get()?.let { throw AssertionError("external cancellation failed", it) }
    }

    @Test
    fun retainedOldDonePostDoesNotRetireReplacement() {
        val posts = ArrayDeque<() -> Unit>()
        var current: PacketFlow? = null
        val end: (PacketFlow) -> Unit = { ended ->
            posts.add { if (current === ended) current = null }
        }
        val oldNative = NativeHandle()
        val old = PacketFlow(oldNative::create, end)
        current = old
        val replacementNative = NativeHandle()
        val replacement = PacketFlow(replacementNative::create, end)
        current = replacement

        old.close()
        oldNative.done()
        assertEquals(1, posts.size)
        posts.removeFirst().invoke()
        assertSame(replacement, current)
        assertTrue(replacement.isActive())
        assertEquals(1, oldNative.cancelCalls)
        assertEquals(0, replacementNative.cancelCalls)

        replacement.close()
        replacementNative.done()
        posts.removeFirst().invoke()
        assertEquals(null, current)
        assertEquals(1, replacementNative.cancelCalls)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun earlyReplacementDonePostRetainsItsOwnConstructionIdentity() {
        val posts = ArrayDeque<() -> Unit>()
        var current: PacketFlow? = null
        val end: (PacketFlow) -> Unit = { ended ->
            posts.add { if (current === ended) current = null }
        }
        val oldNative = NativeHandle()
        val old = PacketFlow(oldNative::create, end)
        current = old
        var replacementCancelCalls = 0
        val replacement = PacketFlow(
            createIoLoop = { done ->
                done()
                assertSame(old, current)
                val close: () -> Unit = { replacementCancelCalls++ }
                close
            },
            endCallback = end,
        )
        current = replacement
        old.close()
        oldNative.done()

        assertFalse(replacement.isActive())
        assertEquals(2, posts.size)
        posts.removeFirst().invoke()
        assertEquals(null, current)
        posts.removeFirst().invoke()
        assertEquals(null, current)
        assertEquals(1, oldNative.cancelCalls)
        assertEquals(0, replacementCancelCalls)
        replacement.close()
        assertEquals(1, replacementCancelCalls)
    }

    @Test
    fun failedConstructionPreservesPrimaryFailureAndExistingOwner() {
        for (completeBeforeFailure in listOf(false, true)) {
            val posts = ArrayDeque<() -> Unit>()
            var current: PacketFlow? = null
            val end: (PacketFlow) -> Unit = { ended ->
                posts.add { if (current === ended) current = null }
            }
            val oldNative = NativeHandle()
            val old = PacketFlow(oldNative::create, end)
            current = old
            val primaryFailure = IllegalStateException("synthetic native construction failure")

            val failure = assertThrows(IllegalStateException::class.java) {
                val replacement = PacketFlow(
                    createIoLoop = { done ->
                        if (completeBeforeFailure) done()
                        throw primaryFailure
                    },
                    endCallback = end,
                )
                current = replacement
                old.close()
            }

            assertSame(primaryFailure, failure)
            assertSame(old, current)
            assertTrue(old.isActive())
            assertEquals(0, oldNative.cancelCalls)
            assertEquals(if (completeBeforeFailure) 1 else 0, posts.size)
            while (posts.isNotEmpty()) posts.removeFirst().invoke()
            assertSame(old, current)
            old.close()
            oldNative.done()
            posts.removeFirst().invoke()
            assertEquals(null, current)
            assertEquals(1, oldNative.cancelCalls)
        }
    }
}
