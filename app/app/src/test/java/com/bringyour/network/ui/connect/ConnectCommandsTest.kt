package com.bringyour.network.ui.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The connect command queue: commands run off the thread that made them, one
 * at a time, in order, and each device shows the newest of its commands as
 * pending until the last one has run (owner report 2026-10-16). The held
 * queue runs only when the test runs it; the one test with a real thread
 * waits on a latch the commands count down, not on time.
 */
class ConnectCommandsTest {

    private class HeldQueue {
        private val queued = ArrayDeque<Runnable>()

        fun execute(runnable: Runnable) {
            queued.addLast(runnable)
        }

        fun runNext() {
            queued.removeFirst().run()
        }

        fun runAll() {
            while (queued.isNotEmpty()) {
                runNext()
            }
        }
    }

    @Test
    fun commandsRunOffTheCallingThreadInOrder() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val commands = ConnectCommands(execute = { executor.execute(it) }, postMain = { it.run() })
            val device = Any()
            val ran = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Thread>>())
            val done = CountDownLatch(3)
            for (name in listOf("connect", "disconnect", "connect again")) {
                commands.run(device, connect = name != "disconnect") {
                    ran.add(name to Thread.currentThread())
                    done.countDown()
                }
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(listOf("connect", "disconnect", "connect again"), ran.map { it.first })
            ran.forEach { (_, thread) -> assertNotSame(Thread.currentThread(), thread) }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun pendingIsTheNewestCommandUntilTheLastHasRun() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = Any()
        var changes = 0
        commands.addListener { changes += 1 }
        assertNull(commands.pending(device))

        commands.run(device, connect = true) {}
        assertEquals(true, commands.pending(device))
        commands.run(device, connect = false) {}
        assertEquals(false, commands.pending(device))
        assertEquals(2, changes)

        // the connect has run, the disconnect has not: it is still the newest
        queue.runNext()
        assertEquals(false, commands.pending(device))
        queue.runNext()
        assertNull(commands.pending(device))
        assertEquals(4, changes)
    }

    @Test
    fun pendingIsPerDevice() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val previous = Any()
        val next = Any()
        commands.run(previous, connect = true) {}
        assertEquals(true, commands.pending(previous))
        assertNull(commands.pending(next))
        assertNull(commands.pending(null))
    }

    @Test
    fun afterCommandsRunsAfterTheCommandsMadeSoFar() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val ran = mutableListOf<String>()
        commands.run(Any(), connect = false) { ran.add("disconnect") }
        commands.afterCommands { ran.add("close") }
        assertEquals(emptyList<String>(), ran)
        queue.runAll()
        assertEquals(listOf("disconnect", "close"), ran)
    }

    @Test
    fun aFailingCommandStillFinishes() {
        val queue = HeldQueue()
        val failures = mutableListOf<Throwable>()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() }, onFailure = { failures.add(it) })
        val device = Any()
        commands.run(device, connect = true) { throw IllegalStateException("sdk") }
        queue.runAll()
        assertEquals(1, failures.size)
        assertNull(commands.pending(device))
    }
}
