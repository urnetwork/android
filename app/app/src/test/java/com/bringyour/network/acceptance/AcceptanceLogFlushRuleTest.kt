package com.bringyour.network.acceptance

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Real file buffers make terminal loss deterministic without a timer or process race. */
class AcceptanceLogFlushRuleTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun failureAndUiCleanupAreFlushedBeforeTerminalStopAndCopy() {
        val file = temporary.newFile()
        val original = AssertionError("synthetic discovery failure")
        val diagnostic = "[auth-discovery]callback category=network\n"
        val cleanup = "synthetic UI cleanup complete\n"
        FileOutputStream(file).use { raw ->
            val buffered = BufferedOutputStream(raw, 32 * 1024)
            val body = object : Statement() {
                override fun evaluate() {
                    buffered.write(diagnostic.toByteArray())
                    assertEquals("", file.readText())
                    throw original
                }
            }
            val uiCleanup = TestRule { base, _ ->
                object : Statement() {
                    override fun evaluate() {
                        try {
                            base.evaluate()
                        } finally {
                            buffered.write(cleanup.toByteArray())
                        }
                    }
                }
            }
            val invocation = RuleChain.outerRule(AcceptanceLogFlushRule(buffered::flush))
                .around(uiCleanup)
                .apply(body, Description.EMPTY)

            val observed = runCatching { invocation.evaluate() }.exceptionOrNull()
            // Abrupt termination closes the file descriptor, not its userspace buffer.
            raw.close()

            assertSame(original, observed)
            assertEquals(diagnostic + cleanup, file.readText())
        }
    }

    @Test
    fun missingFlushLosesTheDiagnosticWhenTerminalStopPrecedesCopy() {
        val file = temporary.newFile()
        val original = AssertionError("synthetic discovery failure")
        FileOutputStream(file).use { raw ->
            val buffered = BufferedOutputStream(raw, 32 * 1024)
            val body = object : Statement() {
                override fun evaluate() {
                    buffered.write("[auth-discovery]callback category=network\n".toByteArray())
                    throw original
                }
            }

            val observed = runCatching { body.evaluate() }.exceptionOrNull()
            raw.close()

            assertSame(original, observed)
            assertEquals("", file.readText())
        }
    }

    @Test
    fun successAlsoFlushesExactlyOnceBeforeReturningToInstrumentation() {
        val file = temporary.newFile()
        var flushCount = 0
        FileOutputStream(file).use { raw ->
            val buffered = BufferedOutputStream(raw, 32 * 1024)
            val body = object : Statement() {
                override fun evaluate() {
                    buffered.write("synthetic successful test\n".toByteArray())
                }
            }
            AcceptanceLogFlushRule {
                flushCount += 1
                buffered.flush()
            }.apply(body, Description.EMPTY).evaluate()
            raw.close()

            assertEquals(1, flushCount)
            assertEquals("synthetic successful test\n", file.readText())
        }
    }

    @Test
    fun flushFailureIsSuppressedWithoutReplacingTheOriginalFailure() {
        val original = AssertionError("synthetic test failure")
        val flushFailure = IOException("synthetic flush failure")
        val body = object : Statement() {
            override fun evaluate() = throw original
        }

        val observed = runCatching {
            AcceptanceLogFlushRule { throw flushFailure }.apply(body, Description.EMPTY).evaluate()
        }.exceptionOrNull()

        assertSame(original, observed)
        assertEquals(1, original.suppressed.size)
        assertSame(flushFailure, original.suppressed.single())
    }

    @Test
    fun flushFailureAfterSuccessRemainsATestFailure() {
        val flushFailure = IOException("synthetic flush failure")
        val body = object : Statement() {
            override fun evaluate() = Unit
        }

        val observed = runCatching {
            AcceptanceLogFlushRule { throw flushFailure }.apply(body, Description.EMPTY).evaluate()
        }.exceptionOrNull()

        assertSame(flushFailure, observed)
    }

    @Test
    fun theSameFlushFailureCannotReplaceItselfWithSelfSuppression() {
        val original = AssertionError("synthetic shared failure")
        val body = object : Statement() {
            override fun evaluate() = throw original
        }

        val observed = runCatching {
            AcceptanceLogFlushRule { throw original }.apply(body, Description.EMPTY).evaluate()
        }.exceptionOrNull()

        assertSame(original, observed)
        assertEquals(0, original.suppressed.size)
    }
}
