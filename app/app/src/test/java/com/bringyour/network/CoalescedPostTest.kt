package com.bringyour.network

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quick Settings tile's status check is coalesced (review of the Apple
 * parity branch, 2026-10-16): the window reports each provider's evaluation,
 * and the tile posted one main-thread check per report. The post is held
 * until the test runs it, as the main looper runs it later; nothing waits on
 * time.
 */
class CoalescedPostTest {

    private class HeldPosts {
        private val posted = ArrayDeque<Runnable>()

        val size: Int get() = posted.size

        fun post(runnable: Runnable) {
            posted.addLast(runnable)
        }

        fun runNext() {
            posted.removeFirst().run()
        }
    }

    @Test
    fun aBurstOfRequestsPostsOneRun() {
        val posts = HeldPosts()
        var runs = 0
        val check = CoalescedPost(post = posts::post) { runs += 1 }

        repeat(5) { check.request() }
        assertEquals(1, posts.size)

        posts.runNext()
        assertEquals(1, runs)
        assertEquals(0, posts.size)
    }

    @Test
    fun aRequestAfterTheRunStartsPostsAnother() {
        // the run reads the state as it starts: a later change needs a run
        val posts = HeldPosts()
        var runs = 0
        val check = CoalescedPost(post = posts::post) { runs += 1 }
        check.request()
        posts.runNext()

        check.request()
        assertEquals(1, posts.size)
        posts.runNext()
        assertEquals(2, runs)
    }

    @Test
    fun aRequestDuringTheRunIsNotLost() {
        // a report arriving while the check reads: it is read by another run
        val posts = HeldPosts()
        var runs = 0
        lateinit var check: CoalescedPost
        check = CoalescedPost(post = posts::post) {
            runs += 1
            if (runs == 1) {
                check.request()
            }
        }
        check.request()
        posts.runNext()
        assertEquals(1, posts.size)
        posts.runNext()
        assertEquals(2, runs)
        assertEquals(0, posts.size)
    }

    @Test
    fun theTilesStatusListenersRequestTheCoalescedCheck() {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + "src/main/java/com/bringyour/network/QuickConnectTileService.kt") }
            .firstOrNull { it.isFile }
        assertNotNull(file)
        val source = file!!.readText()
        val from = source.indexOf("    override fun onStartListening() {")
        assertTrue(0 <= from)
        val listening = source.substring(from, source.indexOf("\n    }\n", from))
        assertTrue(listening, listening.contains("addWindowStatusChangeListener { statusCheck.request() }"))
        assertTrue(listening, listening.contains("addTunnelChangeListener { statusCheck.request() }"))
        // the connect change still renders at once; nothing else posts per report
        assertEquals(listening, 1, Regex(Regex.escape("mainHandler.post")).findAll(listening).count())
        assertFalse(listening, listening.contains("renderIfStatusChanged"))
    }
}
