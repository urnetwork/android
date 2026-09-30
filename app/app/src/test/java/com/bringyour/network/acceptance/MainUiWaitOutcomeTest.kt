package com.bringyour.network.acceptance

import com.bringyour.network.ui.PostLoginUiAction
import com.bringyour.network.ui.login.signupFormErrorIsTerminal
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiWaitOutcomeTest {
    @Test
    fun signupFormErrorIsTerminalInsteadOfWaitingForPostLoginTimeout() {
        assertEquals(
            MainUiWaitOutcome.SIGNUP_FORM_ERROR,
            mainUiWaitOutcome(
                action = null,
                mainNavigationPresent = false,
                signupFormErrorPresent = true,
            ),
        )
    }

    @Test
    fun signupErrorCannotBeCalledSuccessfulOrDismissedAsAnOverlay() {
        for (action in listOf(null) + PostLoginUiAction.entries) {
            for (mainPresent in listOf(false, true)) {
                assertEquals(
                    MainUiWaitOutcome.SIGNUP_FORM_ERROR,
                    mainUiWaitOutcome(action, mainPresent, signupFormErrorPresent = true),
                )
            }
        }
    }

    @Test
    fun waitsWithoutSignupErrorPreserveExistingNavigationAndOverlayRules() {
        for (action in listOf(null) + PostLoginUiAction.entries) {
            for (mainPresent in listOf(false, true)) {
                val expected = if (action == null && mainPresent) {
                    MainUiWaitOutcome.READY
                } else {
                    MainUiWaitOutcome.PENDING
                }
                assertEquals(expected, mainUiWaitOutcome(action, mainPresent, false))
            }
        }
    }

    @Test
    fun errorReportHasOnlyFixedMetadataAndNoRawCauseOrSuppressedErrors() {
        val error = SignupFormError()
        assertEquals("Password signup failed: signup-form-error", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
    }

    @Test
    fun visibleApiRefusalEndsImmediatelyWithOneSubmitAndNoTimeout() {
        val screen = FakeMainUi(errorPresent = true)
        var submits = 0

        assertThrows(SignupFormError::class.java) {
            submitPasswordSignupAndWait({ submits += 1 }) { waitForMainUi(screen, 90_000) }
        }

        assertEquals(1, submits)
        assertEquals(0L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
        assertTrue(screen.dismissed.isEmpty())
    }

    @Test
    fun delayedErrorWakesTheWaitWithoutASubmissionRetry() {
        val screen = FakeMainUi(inProgress = true).apply {
            after(125) { inProgress = false; errorPresent = true }
        }
        var submits = 0
        assertThrows(SignupFormError::class.java) {
            submitPasswordSignupAndWait({ submits += 1 }) { waitForMainUi(screen, 90_000) }
        }
        assertEquals(1, submits)
        assertEquals(125L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
    }

    @Test
    fun startupErrorOnSignupFormIsNotReportedAsAnHttpOrApiRejection() {
        val screen = FakeMainUi(errorPresent = true)
        val error = assertThrows(SignupFormError::class.java) { waitForMainUi(screen, 90_000) }
        assertEquals("Password signup failed: signup-form-error", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        assertEquals(0, screen.timeouts)
    }

    @Test
    fun delayedSuccessAdvancesSequentialOverlaysAndSubmitsOnce() {
        val screen = FakeMainUi(inProgress = true).apply {
            after(2_000) { inProgress = false; action = PostLoginUiAction.WelcomeEnter }
            after(3_500) { action = PostLoginUiAction.IntroClose }
            after(4_250) { mainPresent = true }
        }
        var submits = 0
        submitPasswordSignupAndWait({ submits += 1 }) { waitForMainUi(screen, 90_000) }

        assertEquals(1, submits)
        assertEquals(4_250L, screen.elapsedMillis)
        assertEquals(listOf(PostLoginUiAction.WelcomeEnter, PostLoginUiAction.IntroClose), screen.dismissed)
        assertEquals(0, screen.timeouts)
    }

    @Test
    fun pendingSignupRetainsExactNinetySecondDeadlineAndOriginalTimeout() {
        val screen = FakeMainUi(inProgress = true)
        var submits = 0
        val error = assertThrows(AssertionError::class.java) {
            submitPasswordSignupAndWait({ submits += 1 }) { waitForMainUi(screen, 90_000) }
        }
        assertSame(screen.timeoutError, error)
        assertEquals(90_000L, screen.elapsedMillis)
        assertEquals(1, screen.timeouts)
        assertEquals(1, submits)
    }

    @Test
    fun transientPreviousErrorDuringLoadingDoesNotRejectSuccessfulSubmission() {
        val screen = FakeMainUi(errorPresent = true, inProgress = true).apply {
            after(250) { errorPresent = false }
            after(1_500) { inProgress = false; mainPresent = true }
        }
        waitForMainUi(screen, 90_000)
        assertEquals(1_500L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
    }

    @Test
    fun terminalErrorTagRequiresPresentErrorAndIdleSubmission() {
        for (errorPresent in listOf(false, true)) {
            for (inProgress in listOf(false, true)) {
                assertEquals(
                    errorPresent && !inProgress,
                    signupFormErrorIsTerminal(errorPresent, inProgress),
                )
            }
        }
    }

    @Test
    fun rejectedSubmitNeverBeginsWaitingOrRetries() {
        val original = AssertionError("action refused")
        var submits = 0
        var waits = 0
        val error = assertThrows(AssertionError::class.java) {
            submitPasswordSignupAndWait(
                submit = { submits += 1; throw original },
                waitForMain = { waits += 1 },
            )
        }
        assertSame(original, error)
        assertEquals(1, submits)
        assertEquals(0, waits)
    }

    private class FakeMainUi(
        var errorPresent: Boolean = false,
        var inProgress: Boolean = false,
    ) : MainUiWaitDriver {
        var elapsedMillis = 0L
        var action: PostLoginUiAction? = null
        var mainPresent = false
        var timeouts = 0
        val timeoutError = AssertionError("original post-login timeout")
        val dismissed = mutableListOf<PostLoginUiAction>()
        private val events = ArrayDeque<Pair<Long, FakeMainUi.() -> Unit>>()

        fun after(millis: Long, change: FakeMainUi.() -> Unit) {
            events.addLast(millis to change)
        }

        override fun nowNanos(): Long = TimeUnit.MILLISECONDS.toNanos(elapsedMillis)
        override fun observe(): MainUiWaitEvidence = MainUiWaitEvidence(
            action,
            mainPresent,
            signupFormErrorIsTerminal(errorPresent, inProgress),
        )

        override fun dismiss(action: PostLoginUiAction) {
            dismissed += action
            this.action = null
        }

        override fun waitForIdle() = Unit

        override fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
            val deadline = elapsedMillis + timeoutMillis
            while (!condition()) {
                val event = events.firstOrNull()
                if (event != null && event.first <= deadline) {
                    elapsedMillis = event.first
                    events.removeFirst().second.invoke(this)
                } else {
                    elapsedMillis = deadline
                    if (!condition()) throw AssertionError("poll timeout")
                }
            }
        }

        override fun timeout(): Nothing {
            timeouts += 1
            throw timeoutError
        }
    }
}
