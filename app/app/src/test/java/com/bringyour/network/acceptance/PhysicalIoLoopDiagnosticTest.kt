package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Barrier/queue order models the actual main-thread callback fence, not JNI. */
class PhysicalIoLoopDiagnosticTest {
    @Test
    fun defaultOffAndExplicitPrivateOptInAreDistinct() {
        assertFalse(physicalIoLoopDiagnosticRequested(null, false))
        assertFalse(physicalIoLoopDiagnosticRequested(null, true))
        assertTrue(physicalIoLoopDiagnosticRequested("1", true))
        assertThrows(IllegalStateException::class.java) { physicalIoLoopDiagnosticRequested("1", false) }
        for (argument in listOf("", "0", "true", "2")) {
            assertThrows(IllegalArgumentException::class.java) { physicalIoLoopDiagnosticRequested(argument, true) }
        }
    }

    @Test
    fun normalReplacementsAndRetainedDonePostsDrainBeforeRelease() {
        val first = Any()
        val replacement = Any()
        var current: Any? = first
        val mainPosts = ArrayDeque<() -> Unit>()
        var devicePresent = true
        var retries = 0
        val events = mutableListOf<String>()
        fun donePost(ended: Any) {
            mainPosts.add {
                if (current === ended) {
                    current = null
                    if (devicePresent) retries++
                }
                events.add("done-post")
            }
        }
        // Real source wiring is separately asserted: replaced owner callbacks
        // do not erase the current owner; logout detaches it before native join.
        current = replacement
        donePost(first)
        mainPosts.removeFirst().invoke()
        assertTrue(current === replacement)
        devicePresent = false
        current = null
        finishPhysicalIoLoopDiagnostic(
            mainFence = {
                while (mainPosts.isNotEmpty()) mainPosts.removeFirst().invoke()
                check(!devicePresent && current == null)
                events.add("main-fence")
            },
            snapshot = {
                donePost(replacement)
                events.add("native-run-and-fd-joined")
                "joined-synthetic-json"
            },
            write = { raw, fenced ->
                assertEquals("joined-synthetic-json", raw)
                assertTrue(fenced)
                assertTrue(mainPosts.isEmpty())
                events.add("write")
            },
            release = { events.add("release") },
            failed = { error("unexpected failure: $it") },
        )
        assertEquals(0, retries)
        assertEquals(listOf("done-post", "main-fence", "native-run-and-fd-joined", "done-post", "main-fence", "write", "release"), events)
    }

    @Test
    fun failedConstructionFenceStillJoinsWritesIncompleteAndReleases() {
        val events = mutableListOf<String>()
        var fences = 0
        finishPhysicalIoLoopDiagnostic(
            mainFence = { fences++; if (fences == 1) error("synthetic active owner") },
            snapshot = { events.add("join"); "incomplete-synthetic-json" },
            write = { _, fenced -> assertFalse(fenced); events.add("write-incomplete") },
            release = { events.add("release") },
            failed = { events.add(it) },
        )
        assertEquals(2, fences)
        assertEquals(listOf("ioloop-fence-before", "join", "write-incomplete", "release"), events)
    }

    @Test
    fun everyTeardownFailureStillAttemptsLaterFenceAndRelease() {
        for (stage in listOf("snapshot", "second-fence", "write", "release")) {
            val events = mutableListOf<String>()
            var fences = 0
            finishPhysicalIoLoopDiagnostic(
                mainFence = { fences++; events.add("fence"); if (stage == "second-fence" && fences == 2) error("synthetic fence") },
                snapshot = { events.add("join"); if (stage == "snapshot") error("synthetic join"); "synthetic-json" },
                write = { _, fenced ->
                    assertEquals(stage != "second-fence", fenced)
                    events.add("write")
                    if (stage == "write") error("synthetic writer")
                },
                release = { events.add("release"); if (stage == "release") error("synthetic release") },
                failed = { events.add("failed:$it") },
            )
            assertEquals(2, fences)
            assertEquals(1, events.count { it == "release" })
            assertEquals(1, events.count { it.startsWith("failed:") })
            assertEquals(if (stage == "snapshot") 0 else 1, events.count { it == "write" })
        }
    }
}
