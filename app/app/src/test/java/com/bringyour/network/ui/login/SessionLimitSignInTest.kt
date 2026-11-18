package com.bringyour.network.ui.login

import com.bringyour.network.AuthClientRequester
import com.bringyour.network.AuthClientWireResult
import com.bringyour.network.ClientSessionStarter
import com.bringyour.network.LoginClientCompletion
import com.bringyour.network.LoginClientCoordinator
import com.bringyour.network.LoginStartupFailure
import com.bringyour.network.LoginStartupStage
import com.bringyour.network.LoginStartupState
import com.bringyour.network.LoginStartupTracker
import com.bringyour.network.authClientRefusal
import com.bringyour.sdk.Sdk
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The sign-in screens at the network's cap of active sessions (session1118
 * C.md §1, §5 A): the sdk answers every route that can create a session with
 * a result error coded Sdk.SessionErrorCodeLimitReached, and every sign-in
 * screen shows sessions_limit_reached_sign_in for it where it shows its
 * errors, in place of the server's message, a generic error or a wrong one.
 * The check is the helper's (SessionLimitSignIn.kt), and the client created
 * right after a sign-in fails with its own failure. The screens need a
 * device, so their wiring is read from the sources.
 */
class SessionLimitSignInTest {
    companion object {
        // gradle runs unit tests with the module directory as the working
        // directory; the other candidates cover runners that start a level up
        private fun moduleFile(path: String): File {
            val file = listOf("", "app/", "app/app/")
                .map { File(it + path) }
                .firstOrNull { it.isFile }
            assertNotNull("$path not found", file)
            return file!!
        }

        private fun source(sourceSet: String, path: String) =
            moduleFile("src/$sourceSet/java/com/bringyour/network/$path").readText()

        // the text between two markers, failing when either is missing
        private fun between(source: String, start: String, end: String): String {
            val from = source.indexOf(start)
            assertTrue("missing: $start", 0 <= from)
            val to = source.indexOf(end, from + start.length)
            assertTrue("missing after $start: $end", 0 <= to)
            return source.substring(from, to)
        }

        // the sign-in screens of every flavor (play, github, solana_dapp, ethos_dapp)
        private val FLAVORS = listOf("google", "ungoogle", "solana_dapp", "ethos_dapp")
    }

    @Test
    fun onlyTheSdksSessionLimitCodeIsTheRefusal() {
        // the server's code, which the sdk passes through (api_session_limit.go)
        assertEquals("session_limit_reached", Sdk.SessionErrorCodeLimitReached)
        assertTrue(isSessionLimitReached(Sdk.SessionErrorCodeLimitReached))
        // the cap of revoked sessions is not freed by signing sessions out: it
        // stays the server's message, as does every other code
        for (code in listOf(null, "", "session_retention_limit_reached", "signature_mismatch", "SESSION_LIMIT_REACHED")) {
            assertFalse("$code", isSessionLimitReached(code))
        }
    }

    @Test
    fun aClientRefusedAtTheCapIsNeitherAClientLimitNorAPlainRefusal() {
        assertEquals(
            LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED,
            authClientRefusal(Sdk.SessionErrorCodeLimitReached, Sdk.ClientLimitPromptNone),
        )
        // the code decides, whatever the prompt: no upgrade and no removed
        // device frees a session
        assertEquals(
            LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED,
            authClientRefusal(Sdk.SessionErrorCodeLimitReached, Sdk.ClientLimitPromptUpgrade),
        )
        assertEquals(LoginStartupFailure.AUTH_CLIENT_UPGRADE_REQUIRED, authClientRefusal("", Sdk.ClientLimitPromptUpgrade))
        assertEquals(LoginStartupFailure.AUTH_CLIENT_LIMIT_EXCEEDED, authClientRefusal(null, Sdk.ClientLimitPromptRemoveClient))
        assertEquals(LoginStartupFailure.AUTH_CLIENT_REJECTED, authClientRefusal(null, null))
        assertEquals(LoginStartupFailure.AUTH_CLIENT_REJECTED, authClientRefusal("session_retention_limit_reached", ""))
        assertEquals("auth-client-session-limit-reached", LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED.wireValue)
    }

    @Test
    fun aSignInWhoseClientIsRefusedAtTheCapFailsWithTheRefusalsMessage() {
        val tracker = LoginStartupTracker()
        val attempt = tracker.begin(LoginStartupStage.AUTH_CLIENT)
        var completion: LoginClientCompletion? = null
        LoginClientCoordinator(
            tracker,
            AuthClientRequester { callback ->
                callback(
                    AuthClientWireResult(
                        clientId = null,
                        byClientJwt = null,
                        failure = LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED,
                        failureMessage = "the app's message",
                    ),
                    null,
                )
            },
            ClientSessionStarter { error("must not start") },
            dispatch = { it() },
        ).authenticate(attempt) { completion = it }

        assertEquals(
            LoginClientCompletion.Failed(LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED, "the app's message"),
            completion,
        )
        assertTrue(isSessionLimitReached(completion!!))
        assertEquals(
            LoginStartupState.Failed(attempt, LoginStartupStage.AUTH_CLIENT, LoginStartupFailure.AUTH_CLIENT_SESSION_LIMIT_REACHED),
            tracker.state.value,
        )

        // any other outcome keeps the screen's own error
        assertFalse(isSessionLimitReached(LoginClientCompletion.Ready("00000000-0000-4000-8000-000000000001")))
        assertFalse(isSessionLimitReached(LoginClientCompletion.Failed(LoginStartupFailure.AUTH_CLIENT_REJECTED, "x")))
        assertFalse(isSessionLimitReached(LoginClientCompletion.Failed(LoginStartupFailure.AUTH_CLIENT_LIMIT_EXCEEDED)))
    }

    @Test
    fun theRefusalSaysWhereToSignSessionsOutInEveryLanguage() {
        val res = moduleFile("src/main/res/values/strings.xml").parentFile!!.parentFile!!
        val catalogs = res.listFiles { file -> file.name.startsWith("values") }!!
            .filter { File(it, "strings.xml").exists() }
        assertTrue(catalogs.size > 1)
        for (catalog in catalogs) {
            val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(catalog, "strings.xml")).getElementsByTagName("string")
            val text = (0 until nodes.length).map { nodes.item(it) as Element }
                .firstOrNull { it.getAttribute("name") == "sessions_limit_reached_sign_in" }?.textContent
            assertFalse(catalog.name, text.isNullOrEmpty())
            if (catalog.name == "values") {
                assertEquals(
                    "Too many active sessions. On a device that\\'s signed in to this account, go to Account > Sessions and sign out sessions you no longer use, then try again.",
                    text,
                )
            }
        }
        // one lookup, in the helper
        assertTrue(source("main", "ui/login/SessionLimitSignIn.kt").contains("R.string.sessions_limit_reached_sign_in"))
    }

    @Test
    fun theDeviceClientAfterASignInAndThePasswordSignInMapTheRefusal() {
        val application = source("main", "MainApplication.kt")
        val client = between(application, "currentApi.authNetworkClient(args) { result, error ->", "callback(")
        assertTrue(client.contains("authClientRefusal(it.code, it.clientLimitPrompt)"))
        assertTrue(client.contains("signInErrorMessage("))
        val password = between(application, "currentApi.authLoginWithPassword(args) { result, error ->", "mappedResult to error?.message")
        // the session limit's message, never "incorrect password" or the server's
        assertTrue(password.contains("failureMessage = com.bringyour.network.ui.login.signInErrorMessage("))
        // a retry from the device list says so too, not "something went wrong"
        val retry = between(application, "internal fun retrySignInClientLimit(", "\n    }\n")
        assertTrue(retry.contains("signInClientFailureMessage(this@MainApplication, completion)"))
    }

    @Test
    fun everySharedSignInScreenShowsTheRefusal() {
        // result errors of verify, network create (both ways), code sign-in and Bittensor
        val shown = mapOf(
            "ui/login/LoginVerify.kt" to "signInErrorMessage(context, result.error.code, result.error.message)",
            "ui/login/LoginCreateNetwork.kt" to "signInErrorMessage(context, result.error.code, result.error.message)",
            "ui/login/CreateNetworkInstantViewModel.kt" to "signInErrorMessage(context, result.error.code, result.error.message)",
            "ui/login/AuthCodeLoginSheetViewModel.kt" to "signInErrorMessage(context, result.error.code, result.error.message)",
            "ui/login/BittensorLogin.kt" to "signInErrorMessage(application, it.code, it.message)",
            "ui/settings/AddAuthMethodSheet.kt" to "signInErrorMessage(context, result.error.code, result.error.message)",
            // the client after a sign-in, where its failure shows
            "ui/login/LoginUtils.kt" to "onErr(signInClientFailureMessage(app, completion))",
            "ui/LoginNavHost.kt" to "signInClientFailureMessage(context, completion)",
            "ui/login/SwitchAccountScreen.kt" to "signInClientFailureMessage(context, completion)?.let(setLoginError)",
        )
        for ((path, call) in shown) {
            assertTrue("$path: $call", source("main", path).contains(call))
        }
        assertTrue(source("main", "ui/LoginNavHost.kt").contains("setLoginError = loginViewModel.setLoginError,"))
    }

    @Test
    fun everyFlavorsSignInsShowTheRefusal() {
        for (flavor in FLAVORS) {
            val viewModel = source(flavor, "ui/login/LoginViewModel.kt")
            // SSO, wallet, recovery phrase and email/phone sign-ins: no raw message left
            assertFalse(flavor, viewModel.contains("setLoginError(result.error.message)"))
            assertTrue(flavor, viewModel.contains("setLoginError(signInErrorMessage(ctx, result.error.code, result.error.message))"))
            assertTrue(flavor, viewModel.contains("onError(errorMessage ?: \"Invalid seedphrase\")"))

            val activity = source(flavor, "LoginActivity.kt")
            // the Apple, Google and Bittensor returns
            assertFalse(flavor, activity.contains("loginViewModel.setLoginError(resultError.message)"))
            assertTrue(
                flavor,
                activity.contains("loginViewModel.setLoginError(signInErrorMessage(this@LoginActivity, resultError.code, resultError.message))"),
            )
            // an auth code link: a refused code is not a sign-in, and says why
            val codeLogin = between(activity, "request.api.authCodeLogin(args) { result, err ->", "} ?: run")
            assertTrue(flavor, codeLogin.contains("val loginJwt = result?.takeIf { it.error == null }?.jwt?.takeIf(String::isNotEmpty)"))
            assertTrue(flavor, codeLogin.contains("signInErrorMessage(this@LoginActivity, result?.error?.code, null)"))
            assertTrue(flavor, codeLogin.contains("sessionLimitMessage?.let { loginViewModel.setLoginError(it) }"))
            // the client after the sign-ins this activity completes
            val welcome = between(activity, "private fun welcomeThenAuthClientAndFinish(", "\n    }\n")
            assertTrue(flavor, welcome.contains("signInClientFailureMessage(app, completion)?.let { loginViewModel.setLoginError(it) }"))

            val initial = source(flavor, "ui/login/LoginInitial.kt")
            // the client after the screen's sign-ins and the recovery phrase sheet's
            assertTrue(flavor, initial.contains("onErr = { message ->"))
            assertTrue(flavor, initial.contains("val sessionLimitMessage = signInClientFailureMessage(context, completion)"))
        }
    }
}
