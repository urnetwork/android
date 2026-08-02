package com.bringyour.network

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequencedValueListenersTest {
    @Test
    fun listenerImmediatelyReceivesCurrentValue() {
        val values = mutableListOf<String>()
        val listeners = SequencedValueListeners("current")

        listeners.add { values.add(it) }

        assertEquals(listOf("current"), values)
    }

    @Test
    fun delayedOlderDispatchCannotOverwriteNewerValue() {
        val values = mutableListOf<String>()
        val listeners = SequencedValueListeners("initial")
        listeners.add { values.add(it) }
        val older = listeners.prepareUpdate("older")
        val newer = listeners.prepareUpdate("newer")

        newer()
        older()

        assertEquals(listOf("initial", "newer"), values)
    }

    @Test
    fun removedListenerRejectsAlreadyPreparedDispatch() {
        val values = mutableListOf<String>()
        val listeners = SequencedValueListeners("initial")
        val remove = listeners.add { values.add(it) }
        val pending = listeners.prepareUpdate("pending")

        remove()
        pending()

        assertEquals(listOf("initial"), values)
    }

    @Test
    fun callbackDispatchIsSerializedPerListener() {
        val values = Collections.synchronizedList(mutableListOf<String>())
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val listeners = SequencedValueListeners("initial")
        listeners.add {
            values.add(it)
            if (it == "first") {
                firstEntered.countDown()
                releaseFirst.await(5, TimeUnit.SECONDS)
            } else if (it == "second") {
                secondFinished.countDown()
            }
        }
        val first = listeners.prepareUpdate("first")
        val second = listeners.prepareUpdate("second")

        val firstThread = thread(start = true, block = first)
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS))
        val secondThread = thread(start = true, block = second)
        assertFalse(secondFinished.await(100, TimeUnit.MILLISECONDS))
        releaseFirst.countDown()

        firstThread.join(5_000)
        secondThread.join(5_000)
        assertFalse(firstThread.isAlive)
        assertFalse(secondThread.isAlive)
        assertEquals(listOf("initial", "first", "second"), values)
    }
}
