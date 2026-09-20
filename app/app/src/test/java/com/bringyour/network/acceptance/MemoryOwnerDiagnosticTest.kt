package com.bringyour.network.acceptance

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MemoryOwnerDiagnosticTest {
    class Writer {
        var calls = 0
        fun writeMemoryOwnerCensus(path: String) {
            calls++
            File(path).writeText("{\"schema\":1}")
        }
    }

    class EmptyWriter {
        fun writeMemoryOwnerCensus(path: String) = Unit
    }

    class FailingWriter {
        fun writeMemoryOwnerCensus(path: String) {
            throw IllegalStateException("private path must not become successful evidence")
        }
    }

    @Test
    fun `exact SDK binding writes once and refuses existing evidence`() {
        inDirectory { directory ->
            val writer = Writer()
            val output = writeMemoryOwnerDiagnostic(writer, directory, "idle-before-gc")
            assertEquals("physical-owners-idle-before-gc.json", output.name)
            assertEquals("{\"schema\":1}", output.readText())
            assertThrows(IllegalStateException::class.java) {
                writeMemoryOwnerDiagnostic(writer, directory, "idle-before-gc")
            }
            assertEquals(1, writer.calls)
        }
    }

    @Test
    fun `old binding empty evidence and native error fail closed`() {
        inDirectory { directory ->
            for (writer in listOf(Any(), EmptyWriter(), FailingWriter())) {
                assertThrows(IllegalStateException::class.java) {
                    writeMemoryOwnerDiagnostic(writer, directory, "preflight")
                }
            }
        }
    }

    @Test
    fun `labels cannot escape or silently omit the artifact`() {
        inDirectory { directory ->
            val writer = Writer()
            for (label in listOf("", "../private", "/absolute", ".", "a".repeat(65))) {
                assertThrows(IllegalArgumentException::class.java) {
                    writeMemoryOwnerDiagnostic(writer, directory, label)
                }
            }
            assertEquals(0, writer.calls)
        }
    }

    private fun inDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("memory-owner-diagnostic").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
