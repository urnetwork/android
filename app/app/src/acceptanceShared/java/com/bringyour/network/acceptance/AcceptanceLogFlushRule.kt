package com.bringyour.network.acceptance

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Flush after test and UI cleanup, before instrumentation can terminate the app. */
class AcceptanceLogFlushRule(
    private val flushLogs: () -> Unit,
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var failure: Throwable? = null
                try {
                    base.evaluate()
                } catch (error: Throwable) {
                    failure = error
                    throw error
                } finally {
                    try {
                        flushLogs()
                    } catch (error: Throwable) {
                        val original = failure
                        if (original == null) throw error
                        if (error !== original) original.addSuppressed(error)
                    }
                }
            }
        }
}
