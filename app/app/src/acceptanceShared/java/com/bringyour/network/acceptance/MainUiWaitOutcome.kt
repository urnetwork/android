package com.bringyour.network.acceptance

import com.bringyour.network.ui.PostLoginUiAction
import java.util.concurrent.TimeUnit

internal enum class MainUiWaitOutcome {
    PENDING,
    READY,
    SIGNUP_FORM_ERROR,
}

/** Presence-only evidence: never read form contents or raw API error text. */
internal fun mainUiWaitOutcome(
    action: PostLoginUiAction?,
    mainNavigationPresent: Boolean,
    signupFormErrorPresent: Boolean,
): MainUiWaitOutcome = when {
    signupFormErrorPresent -> MainUiWaitOutcome.SIGNUP_FORM_ERROR
    action == null && mainNavigationPresent -> MainUiWaitOutcome.READY
    else -> MainUiWaitOutcome.PENDING
}

internal class SignupFormError : AssertionError("Password signup failed: signup-form-error")

internal data class MainUiWaitEvidence(
    val action: PostLoginUiAction?,
    val mainNavigationPresent: Boolean,
    val signupFormErrorPresent: Boolean,
)

/** Test-only boundary for the existing UI wait; it has no submit or API operation. */
internal interface MainUiWaitDriver {
    fun nowNanos(): Long
    fun observe(): MainUiWaitEvidence
    fun dismiss(action: PostLoginUiAction)
    fun waitForIdle()
    fun waitUntil(timeoutMillis: Long, condition: () -> Boolean)
    fun timeout(): Nothing
}

internal fun waitForMainUi(driver: MainUiWaitDriver, timeoutMillis: Long) {
    val deadlineNanos = driver.nowNanos() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    fun outcome(evidence: MainUiWaitEvidence): MainUiWaitOutcome = mainUiWaitOutcome(
        evidence.action,
        evidence.mainNavigationPresent,
        evidence.signupFormErrorPresent,
    )
    while (true) {
        val evidence = driver.observe()
        when (outcome(evidence)) {
            MainUiWaitOutcome.READY -> return
            MainUiWaitOutcome.SIGNUP_FORM_ERROR -> throw SignupFormError()
            MainUiWaitOutcome.PENDING -> Unit
        }
        if (driver.nowNanos() >= deadlineNanos) driver.timeout()

        evidence.action?.let(driver::dismiss)
        driver.waitForIdle()

        val remainingMillis = TimeUnit.NANOSECONDS
            .toMillis(deadlineNanos - driver.nowNanos())
            .coerceIn(1, 1_000)
        runCatching {
            driver.waitUntil(remainingMillis) {
                val current = driver.observe()
                outcome(current) != MainUiWaitOutcome.PENDING || current.action != evidence.action
            }
        }
    }
}

/** One submission, followed by observation only; a refusal never triggers a retry. */
internal fun submitPasswordSignupAndWait(submit: () -> Unit, waitForMain: () -> Unit) {
    submit()
    waitForMain()
}
