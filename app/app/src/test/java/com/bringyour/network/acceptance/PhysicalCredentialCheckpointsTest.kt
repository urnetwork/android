package com.bringyour.network.acceptance

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalCredentialCheckpointsTest {
    @Test
    fun `runner diagnostics require exact opt in and otherwise do not observe anything`() {
        for (argument in listOf(null, "", "false", "TRUE", " true ", "1")) {
            assertFalse(physicalCredentialDiagnosticsEnabled(argument))
            val expected = Any()
            var calls = 0
            val result = withPhysicalCredentialRunnerCheckpoints(
                enabled = physicalCredentialDiagnosticsEnabled(argument),
                checkpoint = { error("disabled diagnostics must not access context files or logs") },
                createRunner = { calls++; expected },
            )
            assertSame(expected, result)
            assertEquals(1, calls)
        }
        assertTrue(physicalCredentialDiagnosticsEnabled("true"))
    }

    @Test
    fun `runner observes entry before base initialization and successful return afterward`() {
        val order = mutableListOf<String>()
        val result = withPhysicalCredentialRunnerCheckpoints(
            enabled = true,
            checkpoint = { order += it.wireValue },
            createRunner = { order += "base-on-create"; 7 },
        )
        assertEquals(7, result)
        assertEquals(listOf("runner-on-create-entry", "base-on-create", "runner-on-create-return"), order)
    }

    @Test
    fun `diagnostic failure cannot replace base runner behavior or conceal its exception`() {
        var calls = 0
        val expected = Any()
        assertSame(expected, withPhysicalCredentialRunnerCheckpoints(
            enabled = true,
            checkpoint = { error("synthetic-private-file-error") },
            createRunner = { calls++; expected },
        ))
        assertEquals(1, calls)
        val failure = IllegalStateException("base-runner-failure")
        val stages = mutableListOf<PhysicalCredentialStage>()
        val actual = assertThrows(IllegalStateException::class.java) {
            withPhysicalCredentialRunnerCheckpoints(
                enabled = true,
                checkpoint = { stages += it },
                createRunner = { throw failure },
            )
        }
        assertSame(failure, actual)
        assertEquals(listOf(PhysicalCredentialStage.RUNNER_ON_CREATE_ENTRY), stages)
    }

    @Test
    fun `runner checkpoints distinguish already missing from removal during initialization without restoring`() {
        for (initiallyPresent in listOf(false, true)) inDirectory { directory ->
            val credential = directory.resolve("credentials")
            if (initiallyPresent) credential.writeText("synthetic-one\nsynthetic-two")
            val observed = mutableListOf<Pair<String, Boolean>>()
            withPhysicalCredentialRunnerCheckpoints(
                enabled = true,
                checkpoint = { observed += it.wireValue to credential.exists() },
                createRunner = { if (initiallyPresent) check(credential.delete()) },
            )
            observed += PhysicalCredentialStage.TEST_METHOD_ENTRY.wireValue to credential.exists()
            assertEquals(listOf(
                "runner-on-create-entry" to initiallyPresent,
                "runner-on-create-return" to false,
                "test-method-entry" to false,
            ), observed)
            assertFalse(credential.exists())
        }
    }

    @Test
    fun `every runner and test stage uses the same redacted primitive record`() {
        for (stage in PhysicalCredentialStage.entries) {
            val record = physicalCredentialCheckpointJson(stage, 1, 2) {
                throw IllegalStateException("synthetic-private-path credential-value digest")
            }
            assertTrue(record.contains("\"stage\":\"${stage.wireValue}\""))
            assertTrue(record.contains("\"exists\":null,\"fileType\":\"unavailable\""))
            for (forbidden in listOf("synthetic-private", "credential-value", "digest", "exception")) {
                assertFalse(record.contains(forbidden))
            }
        }
    }

    @Test
    fun `checkpoint append remains bounded without overflow`() {
        assertTrue(physicalCredentialCheckpointAppendAllowed(0, 300))
        assertTrue(physicalCredentialCheckpointAppendAllowed(16_084, 300))
        assertFalse(physicalCredentialCheckpointAppendAllowed(16_085, 300))
        assertFalse(physicalCredentialCheckpointAppendAllowed(Long.MAX_VALUE, 300))
        assertFalse(physicalCredentialCheckpointAppendAllowed(-1, 300))
        assertFalse(physicalCredentialCheckpointAppendAllowed(0, -1))
        assertFalse(physicalCredentialCheckpointAppendAllowed(0, 16_385))
    }

    @Test
    fun `checkpoint contains only fixed schema and primitive metadata`() {
        val record = physicalCredentialCheckpointJson(
            PhysicalCredentialStage.BEFORE_CREDENTIAL_READ,
            elapsedRealtimeMs = 123,
            timeUnixMs = 456,
        ) {
            PhysicalCredentialMetadata(PhysicalCredentialFileType.REGULAR, true, 0x180, 44)
        }
        assertEquals(
            "{\"type\":\"physical-credential-checkpoint\",\"schemaVersion\":1," +
                "\"stage\":\"before-credential-read\",\"elapsedRealtimeMs\":123," +
                "\"timeUnixMs\":456,\"exists\":true,\"fileType\":\"regular\"," +
                "\"ownerMatchesApp\":true,\"mode\":\"600\",\"byteCount\":44}",
            record,
        )
    }

    @Test
    fun `missing unavailable and invalid metadata never leak source diagnostics`() {
        val missing = physicalCredentialCheckpointJson(PhysicalCredentialStage.BEFORE_CREDENTIAL_READ, 1, 2) {
            PhysicalCredentialMetadata(PhysicalCredentialFileType.MISSING)
        }
        assertTrue(missing.contains("\"exists\":false"))
        assertTrue(missing.contains("\"ownerMatchesApp\":null,\"mode\":null,\"byteCount\":null"))
        for (reader in listOf<() -> PhysicalCredentialMetadata>(
            { error("private-credential-value /private-credential-path/credentials") },
            { PhysicalCredentialMetadata(PhysicalCredentialFileType.REGULAR, true, -1, 1) },
            { PhysicalCredentialMetadata(PhysicalCredentialFileType.REGULAR, true, 0x180, -1) },
        )) {
            val record = physicalCredentialCheckpointJson(PhysicalCredentialStage.BEFORE_CREDENTIAL_READ, 1, 2, reader)
            assertTrue(record.contains("\"exists\":null,\"fileType\":\"unavailable\""))
            assertFalse(record.contains("private-credential"))
        }
    }

    @Test
    fun `existing staged credentials are read after unchanged observed launch ordering`() {
        inDirectory { directory ->
            val credentials = directory.resolve("credentials")
            credentials.writeText("fixture@example.invalid\nsynthetic-password")
            val order = mutableListOf<String>()
            val result = withPhysicalCredentialCheckpoints(
                checkpoint = { stage ->
                    order += stage.wireValue
                    assertTrue(credentials.isFile)
                },
                launchLoggedOut = { order += "launch" },
                login = { order += "read"; credentials.readLines() },
            )
            assertEquals(2, result.size)
            assertTrue(result.all(String::isNotBlank))
            assertEquals(
                listOf("before-logged-out-launch", "launch", "after-logged-out-launch", "before-credential-read", "read"),
                order,
            )
        }
    }

    @Test
    fun `removal during launch is attributed without concealing or repairing ENOENT`() {
        inDirectory { directory ->
            val credentials = directory.resolve("credentials")
            credentials.writeText("fixture@example.invalid\nsynthetic-password")
            val observed = mutableListOf<Pair<PhysicalCredentialStage, Boolean>>()
            assertThrows(FileNotFoundException::class.java) {
                withPhysicalCredentialCheckpoints(
                    checkpoint = { stage -> observed += stage to credentials.isFile },
                    launchLoggedOut = { check(credentials.delete()) },
                    login = { credentials.readLines() },
                )
            }
            assertEquals(
                listOf(
                    PhysicalCredentialStage.BEFORE_LOGGED_OUT_LAUNCH to true,
                    PhysicalCredentialStage.AFTER_LOGGED_OUT_LAUNCH to false,
                    PhysicalCredentialStage.BEFORE_CREDENTIAL_READ to false,
                ),
                observed,
            )
            assertFalse(credentials.exists())
        }
    }

    private fun inDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("physical-credential-checkpoint").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
