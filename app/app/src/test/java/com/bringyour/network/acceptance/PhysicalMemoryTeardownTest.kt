package com.bringyour.network.acceptance

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PhysicalMemoryTeardownTest {
    @Test
    fun `runtime threshold follows the explicit profile without changing exact boundary semantics`() {
        assertEquals(32L * 1024 * 1024, physicalMemoryRuntimeThresholdBytes("ios-memory-audit-v2"))
        assertEquals(64L * 1024 * 1024, physicalMemoryRuntimeThresholdBytes("android"))
        assertEquals(32L * 1024 * 1024, physicalMemoryRuntimeThresholdBytes("ios-memory-audit-v1"))
        for (profile in listOf("ios-memory-audit-v2", "android")) {
            val threshold = physicalMemoryRuntimeThresholdBytes(profile)
            val retainedValues = listOf(threshold - 1, threshold, threshold + 1, threshold - 1)
            assertEquals(1, retainedValues.count { it > threshold })
        }
        val betweenProfiles = 48L * 1024 * 1024
        assertEquals(true, betweenProfiles > physicalMemoryRuntimeThresholdBytes("ios-memory-audit-v2"))
        assertEquals(false, betweenProfiles > physicalMemoryRuntimeThresholdBytes("android"))
        assertThrows(IllegalStateException::class.java) { physicalMemoryRuntimeThresholdBytes("") }
        assertThrows(IllegalStateException::class.java) { physicalMemoryRuntimeThresholdBytes("android-override") }
    }

    @Test
    fun `every exporter boundary failure is sticky`() {
        for (stage in listOf("open", "append", "flush", "close")) {
            val failed = AtomicBoolean(false)
            retainPhysicalMemoryExporterFailure(failed) { throw java.io.IOException("synthetic $stage failure") }
            assertEquals(stage, true, failed.get())
        }
    }

    @Test
    fun `native receipt write failure attempts one incomplete fallback and remains failed`() {
        val events = mutableListOf<String>()
        writePhysicalMemoryEvidence(summary = { events.add("summary") },
            native = { events.add("native"); throw java.io.IOException("synthetic native close failure") },
            fallback = { events.add("fallback") }, failed = { events.add("failed:$it") })
        assertEquals(listOf("summary", "native", "failed:native-receipt-write", "fallback"), events)
    }

    @Test
    fun `export failure after taking a batch remains sticky after joined cleanup`() {
        val failed = AtomicBoolean(false)
        val taken = CountDownLatch(1)
        var takeCount = 0
        val worker = thread {
            retainPhysicalMemoryExporterFailure(failed) {
                takeCount++
                taken.countDown()
                throw java.io.IOException("synthetic writer failed after native take")
            }
        }
        taken.await()
        worker.join()
        assertEquals(1, takeCount)
        assertThrows(IllegalStateException::class.java) { requirePhysicalMemoryDrainerJoined(worker, failed) }
        retainPhysicalMemoryExporterFailure(failed) { /* later writes succeed */ }
        assertEquals(true, failed.get())
    }

    @Test
    fun `summary failure preserves raw native receipt and cannot claim flushed completion`() {
        val events = mutableListOf<String>()
        writePhysicalMemoryEvidence(
            summary = { events.add("summary"); throw java.io.IOException("synthetic summary failure") },
            native = { flushed -> events.add("native:$flushed") },
            fallback = { events.add("fallback") },
            failed = { stage -> events.add("failed:$stage") },
        )
        assertEquals(listOf("summary", "failed:summary-write", "native:false"), events)
    }

    @Test
    fun `normal external owner joins and drains before its holder scope returns`() {
        val events = mutableListOf<String>()
        closePhysicalMemoryOwner(
            stop = { events.add("stop") }, joinDrainer = { events.add("drainer-join") },
            logout = { events.add("ui-logout-request") }, joinDevice = { events.add("external-device-join") },
            drainDeviceRing = { events.add("final-ring-drain") }, failed = { stage -> events.add("failed:$stage") },
        )
        // Mirrors the production helper-return boundary: no holder callback
        // is carried into the outer native terminal and evidence writes.
        events.add("holder-scope-returned")
        events.add("native-terminal")
        writePhysicalMemoryEvidence(summary = { events.add("summary") }, native = { events.add("native-receipt") },
            fallback = { events.add("fallback") }, failed = { events.add("failed:$it") })
        assertEquals(listOf("stop", "drainer-join", "ui-logout-request", "external-device-join", "final-ring-drain",
            "holder-scope-returned", "native-terminal", "summary", "native-receipt"), events)
    }

    @Test
    fun `join failure still requests logout but skips final drain and takes failed terminal path`() {
        val failures = mutableListOf<String>()
        val events = mutableListOf<String>()
        closePhysicalMemoryOwner(stop = { events.add("stop") }, joinDrainer = { error("synthetic withheld join") },
            logout = { events.add("logout") }, joinDevice = { events.add("join-device") },
            drainDeviceRing = { events.add("must-not-drain") }, failed = { failures.add(it) })
        events.add(if (failures.isEmpty()) "finish" else "abort")
        assertEquals(listOf("drainer-join"), failures)
        assertEquals(listOf("stop", "logout", "join-device", "abort"), events)
    }

    @Test
    fun `profile rate must be an actually present integer zero`() {
        assertEquals(0L, physicalMemoryLong(0))
        assertEquals(0L, physicalMemoryLong(0L))
        for (invalid in listOf(null, "0", 0.0, false)) {
            assertThrows(IllegalStateException::class.java) { physicalMemoryLong(invalid) }
        }
    }

    @Test
    fun `return from join without worker exit cannot qualify the drainer`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread {
            entered.countDown()
            release.await()
        }
        entered.await()
        var joinCalls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                // Force the timeout-return state while the worker is held by
                // an explicit barrier; no short negative timing assertion.
                requirePhysicalMemoryDrainerJoined(worker) { joinCalls++ }
            }
            assertEquals(1, joinCalls)
        } finally {
            release.countDown()
            worker.join()
        }
        requirePhysicalMemoryDrainerJoined(worker)
    }
}
