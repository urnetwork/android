package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordLoginAutomationTest {
    @Test
    fun discoveryClassificationRequiresThePasswordFormAndRejectsEveryError() {
        for (user in listOf(false, true)) {
            for (password in listOf(false, true)) {
                for (error in listOf(false, true)) {
                    val evidence = PasswordLoginDiscoveryEvidence(user, password, error)
                    val expected = when {
                        error -> PasswordLoginDiscoveryState.FAILED
                        password -> PasswordLoginDiscoveryState.PASSWORD_FORM
                        else -> PasswordLoginDiscoveryState.PENDING
                    }
                    assertEquals(evidence.toString(), expected, passwordLoginDiscoveryState(evidence))
                }
            }
        }
    }

    @Test
    fun terminalDiscoveryErrorIsPreservedWithoutCredentialRetryOrPasswordSubmit() {
        val evidence = PasswordLoginDiscoveryEvidence(true, false, true)
        val discoveryError = PasswordLoginFailureException(
            PasswordLoginStage.DISCOVERY,
            PasswordLoginFailure.DISCOVERY_FAILED,
            evidence,
        )
        val screen = TaggedPasswordScreen(passwordInputFailure = discoveryError)

        val error = runCatching {
            performPasswordLogin(screen, "acceptance-user", "acceptance-password", 30_000, 90_000)
        }.exceptionOrNull()

        assertSame(discoveryError, error)
        assertSame(evidence, discoveryError.evidence)
        assertFalse(error!!.message!!.contains("acceptance-user"))
        assertFalse(error.message!!.contains("acceptance-password"))
        assertEquals(1, screen.operations.count { it == "click:$PASSWORD_LOGIN_NEXT_TAG:90000" })
        assertFalse(screen.operations.any { it.startsWith("replace:$PASSWORD_LOGIN_INPUT_TAG") })
        assertFalse(screen.operations.any { it.startsWith("click:$PASSWORD_LOGIN_SUBMIT_TAG") })
        assertFalse(screen.authenticated)
    }

    @Test
    fun discoveryTimeoutKeepsItsStageBeforePasswordAuthenticationStarts() {
        val screen = TaggedPasswordScreen(
            passwordInputFailure = AssertionError("Timed out waiting for UI tag $PASSWORD_LOGIN_INPUT_TAG after 90s"),
        )

        val error = runCatching {
            performPasswordLogin(screen, "acceptance-user", "acceptance-password", 30_000, 90_000)
        }.exceptionOrNull()

        assertEquals(
            "Password login failed at auth-discovery: ui-action-failed",
            error?.message,
        )
        assertFalse(screen.authenticated)
        assertFalse(screen.operations.any { it == "replace:$PASSWORD_LOGIN_INPUT_TAG" })
        assertEquals(1, screen.operations.count { it == "click:$PASSWORD_LOGIN_NEXT_TAG:90000" })
    }

    @Test
    fun initialFormFailureIsNotCalledAnApiFailure() {
        val screen = object : PasswordLoginUi {
            override fun waitForTag(tag: String, timeoutMillis: Long) = throw AssertionError("missing form")
            override fun replaceTagText(tag: String, value: String) = error("unexpected credential input")
            override fun performEnabledTagClick(tag: String, timeoutMillis: Long) = error("unexpected click")
        }
        val error = runCatching {
            performPasswordLogin(screen, "acceptance-user", "acceptance-password", 30_000, 90_000)
        }.exceptionOrNull() as PasswordLoginFailureException
        assertEquals(PasswordLoginStage.USER_FORM, error.stage)
        assertEquals(PasswordLoginFailure.UI_ACTION_FAILED, error.failure)
    }

    @Test
    fun passwordActionFailureIsNotCalledDiscoveryOrLogout() {
        val screen = TaggedPasswordScreen(passwordSubmitFailure = AssertionError("action rejected"))
        val error = runCatching {
            performPasswordLogin(screen, "acceptance-user", "acceptance-password", 30_000, 90_000)
        }.exceptionOrNull() as PasswordLoginFailureException
        assertEquals(PasswordLoginStage.PASSWORD_SUBMIT, error.stage)
        assertEquals(PasswordLoginFailure.UI_ACTION_FAILED, error.failure)
        assertFalse(screen.authenticated)
        assertEquals(1, screen.operations.count { it == "click:$PASSWORD_LOGIN_SUBMIT_TAG:90000" })
    }

    @Test
    fun exactTagsCompleteLoginWithoutGenericAccessibilityFields() {
        val screen = TaggedPasswordScreen()

        // The failed physical driver searched this empty class-based view of
        // the screen. The tagged contract remains fully actionable.
        assertFalse(screen.genericEditableFieldAvailable)
        performPasswordLogin(
            ui = screen,
            user = "acceptance-user",
            password = "acceptance-password",
            uiTimeoutMillis = 30_000,
            authTimeoutMillis = 90_000,
        )

        assertTrue(screen.authenticated)
        assertEquals(
            listOf(
                "wait:$PASSWORD_LOGIN_USER_TAG:30000",
                "replace:$PASSWORD_LOGIN_USER_TAG",
                "click:$PASSWORD_LOGIN_NEXT_TAG:90000",
                "wait:$PASSWORD_LOGIN_INPUT_TAG:90000",
                "replace:$PASSWORD_LOGIN_INPUT_TAG",
                "click:$PASSWORD_LOGIN_SUBMIT_TAG:90000",
            ),
            screen.operations,
        )
    }

    /** A two-screen form that exposes only the app's semantics contract. */
    private class TaggedPasswordScreen(
        private val passwordInputFailure: Throwable? = null,
        private val passwordSubmitFailure: Throwable? = null,
    ) : PasswordLoginUi {
        val genericEditableFieldAvailable = false
        val operations = mutableListOf<String>()
        var authenticated = false
            private set

        private var visibleTag = PASSWORD_LOGIN_USER_TAG
        private var user = ""
        private var password = ""

        override fun waitForTag(tag: String, timeoutMillis: Long) {
            operations += "wait:$tag:$timeoutMillis"
            if (tag == PASSWORD_LOGIN_INPUT_TAG && passwordInputFailure != null) {
                throw passwordInputFailure
            }
            check(tag == visibleTag) { "tag $tag is not visible" }
        }

        override fun replaceTagText(tag: String, value: String) {
            operations += "replace:$tag"
            check(tag == visibleTag) { "tag $tag is not editable" }
            when (tag) {
                PASSWORD_LOGIN_USER_TAG -> user = value
                PASSWORD_LOGIN_INPUT_TAG -> password = value
                else -> error("tag $tag is not a password-login field")
            }
        }

        override fun performEnabledTagClick(tag: String, timeoutMillis: Long) {
            operations += "click:$tag:$timeoutMillis"
            when (tag) {
                PASSWORD_LOGIN_NEXT_TAG -> {
                    check(visibleTag == PASSWORD_LOGIN_USER_TAG && user.isNotBlank())
                    visibleTag = PASSWORD_LOGIN_INPUT_TAG
                }
                PASSWORD_LOGIN_SUBMIT_TAG -> {
                    check(visibleTag == PASSWORD_LOGIN_INPUT_TAG && password.isNotBlank())
                    passwordSubmitFailure?.let { throw it }
                    authenticated = true
                }
                else -> error("tag $tag is not a password-login action")
            }
        }
    }
}
