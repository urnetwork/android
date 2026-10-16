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
    fun aSignOutRefusesItsDevicesConnects() {
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = Any()
        commands.signOutStarted(device)

        var ran = false
        assertEquals(false, commands.run(device, connect = true) { ran = true })
        assertNull(commands.pending(device))
        queue.runAll()
        assertEquals(false, ran)
    }

    @Test
    fun aSignOutSkipsItsQueuedConnectsAndRunsItsDisconnects() {
        // a connect tapped while the queue was busy, then the sign out: the
        // connect never reaches the sdk, a disconnect after it still does
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val device = Any()
        val ran = mutableListOf<String>()
        commands.run(device, connect = true) { ran.add("connect") }
        commands.run(device, connect = false) { ran.add("disconnect") }
        commands.signOutStarted(device)
        assertEquals(true, commands.run(device, connect = false) { ran.add("sign out's disconnect") })

        queue.runAll()
        assertEquals(listOf("disconnect", "sign out's disconnect"), ran)
        assertNull(commands.pending(device))
    }

    @Test
    fun theFenceEndsWithItsDevice() {
        // the next sign-in's device connects at once
        val queue = HeldQueue()
        val commands = ConnectCommands(execute = queue::execute, postMain = { it.run() })
        val signedOut = Any()
        val next = Any()
        commands.signOutStarted(signedOut)

        val ran = mutableListOf<String>()
        assertEquals(true, commands.run(next, connect = true) { ran.add("next connect") })
        queue.runAll()
        assertEquals(listOf("next connect"), ran)
    }

    /** The watchdog's delayed posts, held until the test fires them. */
    private class HeldDelays {
        val posted = mutableListOf<Pair<Runnable, Long>>()

        fun post(runnable: Runnable, millis: Long) {
            posted.add(runnable to millis)
        }

        fun fireAll() {
            val fired = posted.toList()
            posted.clear()
            fired.forEach { it.first.run() }
        }
    }

    @Test
    fun aCommandPastItsBoundIsLoggedAndNoLongerShowsAsPending() {
        // the sdk holds a disconnect: from the bound on the surfaces show
        // what the sdk reports, and the command still runs, in order
        val queue = HeldQueue()
        val delays = HeldDelays()
        val stalls = mutableListOf<Pair<Boolean, Long>>()
        val commands = ConnectCommands(
            execute = queue::execute,
            postMain = { it.run() },
            postMainDelayed = delays::post,
            onStalled = { connect, millis -> stalls.add(connect to millis) },
        )
        val device = Any()
        val ran = mutableListOf<String>()
        var pendingWhileHeld: Boolean? = true
        commands.run(device, connect = false) {
            // the bound passes while the sdk still holds the disconnect
            delays.fireAll()
            pendingWhileHeld = commands.pending(device)
            ran.add("disconnect")
        }
        commands.run(device, connect = true) { ran.add("connect") }
        assertEquals(true, commands.pending(device))

        queue.runNext()
        assertEquals(listOf(false to CONNECT_COMMAND_STALL_MILLIS), stalls)
        assertNull(pendingWhileHeld)
        // it returned: the connect after it is pending again, then runs
        assertEquals(true, commands.pending(device))
        queue.runNext()
        assertEquals(listOf("disconnect", "connect"), ran)
        assertNull(commands.pending(device))
    }

    @Test
    fun aCommandWithinItsBoundIsNotLogged() {
        val queue = HeldQueue()
        val delays = HeldDelays()
        val stalls = mutableListOf<Pair<Boolean, Long>>()
        val commands = ConnectCommands(
            execute = queue::execute,
            postMain = { it.run() },
            postMainDelayed = delays::post,
            onStalled = { connect, millis -> stalls.add(connect to millis) },
        )
        val device = Any()
        commands.run(device, connect = true) {}
        queue.runAll()
        // the bound passes after it returned
        delays.fireAll()
        assertEquals(emptyList<Pair<Boolean, Long>>(), stalls)
        assertNull(commands.pending(device))
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
