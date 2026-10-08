package com.bringyour.network.acceptance

import com.bringyour.network.ui.PostLoginUiAction
import com.bringyour.network.ui.postLoginMainNavigationReady
import com.bringyour.network.ui.login.signupFormErrorIsTerminal
import java.util.concurrent.CancellationException
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
                mainNavigationReady = false,
            ),
        )
    }

    @Test
    fun signupErrorCannotBeCalledSuccessfulOrDismissedAsAnOverlay() {
        for (action in listOf(null) + PostLoginUiAction.entries) {
            for (mainPresent in listOf(false, true)) {
                assertEquals(
                    MainUiWaitOutcome.SIGNUP_FORM_ERROR,
                    mainUiWaitOutcome(action, mainPresent, signupFormErrorPresent = true, mainNavigationReady = true),
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
                assertEquals(expected, mainUiWaitOutcome(action, mainPresent, false, true))
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
    fun visibleNavigationWaitsForTheDelayedIntroDecisionAndDismissal() {
        val screen = FakeMainUi().apply {
            mainPresent = true
            mainReady = postLoginMainNavigationReady(false, false, false, true, false)
            // The balance resolves after navigation is already visible. The
            // decision must be applied before the same navigation is trusted.
            after(1_000) { mainReady = postLoginMainNavigationReady(false, false, true, true, false) }
            after(1_250) {
                mainPresent = false
                mainReady = postLoginMainNavigationReady(false, false, true, false, true)
                action = PostLoginUiAction.IntroClose
            }
            after(1_500) {
                mainPresent = true
                mainReady = postLoginMainNavigationReady(false, false, true, false, false)
            }
        }
        var submits = 0
        submitPasswordSignupAndWait({ submits += 1 }) { waitForMainUi(screen, 90_000) }

        assertEquals(1_500L, screen.elapsedMillis)
        assertEquals(listOf(PostLoginUiAction.IntroClose), screen.dismissed)
        assertEquals(1, submits)
        assertEquals(0, screen.timeouts)
    }

    @Test
    fun visibleNavigationWithAnUnresolvedIntroKeepsTheOriginalDeadline() {
        val screen = FakeMainUi().apply { mainPresent = true; mainReady = false }
        val error = assertThrows(AssertionError::class.java) { waitForMainUi(screen, 90_000) }
        assertSame(screen.timeoutError, error)
        assertEquals(90_000L, screen.elapsedMillis)
        assertEquals(1, screen.timeouts)
        assertTrue(screen.dismissed.isEmpty())
    }

    @Test
    fun readySignalDoesNotReplaceTheRequiredNavigationNode() {
        val screen = FakeMainUi().apply {
            mainReady = true
            after(250) { mainPresent = true }
        }
        waitForMainUi(screen, 90_000)
        assertEquals(250L, screen.elapsedMillis)
    }

    @Test
    fun signupErrorRemainsTerminalWhileTheIntroDecisionIsPending() {
        val screen = FakeMainUi(errorPresent = true).apply { mainPresent = true; mainReady = false }
        assertThrows(SignupFormError::class.java) { waitForMainUi(screen, 90_000) }
        assertEquals(0L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
        assertTrue(screen.dismissed.isEmpty())
    }

    @Test
    fun cancellationDuringTheBoundedWaitPropagatesUnchanged() {
        val cancelled = CancellationException("synthetic cancellation")
        val screen = FakeMainUi().apply { after(125) { mainPresent = true; throw cancelled } }
        val error = assertThrows(CancellationException::class.java) { waitForMainUi(screen, 90_000) }
        assertSame(cancelled, error)
        assertEquals(125L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
        assertTrue(screen.dismissed.isEmpty())
    }

    @Test
    fun observationFailureDuringTheBoundedWaitPropagatesUnchanged() {
        val observationFailure = AssertionError("synthetic observation failure")
        val screen = FakeMainUi().apply { after(125) { mainPresent = true; throw observationFailure } }
        val error = assertThrows(AssertionError::class.java) { waitForMainUi(screen, 90_000) }
        assertSame(observationFailure, error)
        assertEquals(125L, screen.elapsedMillis)
        assertEquals(0, screen.timeouts)
        assertTrue(screen.dismissed.isEmpty())
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
        var mainReady = true
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
            mainReady,
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
                    return
                }
            }
        }

        override fun timeout(): Nothing {
            timeouts += 1
            throw timeoutError
        }
    }
}
