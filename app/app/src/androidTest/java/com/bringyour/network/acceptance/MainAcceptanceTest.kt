package com.bringyour.network.acceptance

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bringyour.network.BuildConfig
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.POST_LOGIN_INTRO_CLOSE_TAG
import com.bringyour.network.ui.POST_LOGIN_WELCOME_ENTER_TAG
import com.bringyour.network.ui.PostLoginUiAction
import com.bringyour.network.ui.nextPostLoginUiAction
import com.bringyour.network.ui.performTransientUiActionIfPresent
import com.bringyour.network.ui.login.ACCEPTANCE_INSTANT_ERROR_TAG
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainAcceptanceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val acceptanceDir = File(context.filesDir, "acceptance")
    private val fixtureFile = File(acceptanceDir, "guest-secret-key")
    private val credentialsFile = File(acceptanceDir, "credentials")
    private val testsFile = File(acceptanceDir, "tests.json")
    private val resultFile = File(acceptanceDir, "result")
    private val screenshotsDir = File(acceptanceDir, "screenshots")

    private fun log(message: String) {
        Log.i(TAG, message)
        println("$TAG: $message")
    }

    private fun waitFor(
        description: String,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
        condition: () -> Boolean,
    ) {
        try {
            compose.waitUntil(timeoutMillis) { condition() }
        } catch (error: Throwable) {
            throw AssertionError("Timed out waiting for $description after ${timeoutMillis / 1_000}s", error)
        }
    }

    private fun nodes(matcher: SemanticsMatcher): Int =
        compose.onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .size

    private fun tagExists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun contentDescriptionExists(description: String): Boolean =
        compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun waitForTag(tag: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS) {
        waitFor("UI tag $tag", timeoutMillis) { tagExists(tag) }
    }

    private fun waitForEitherTag(first: String, second: String, timeoutMillis: Long = AUTH_TIMEOUT_MILLIS): String {
        var result = ""
        waitFor("UI tag $first or $second", timeoutMillis) {
            when {
                tagExists(first) -> {
                    result = first
                    true
                }
                tagExists(second) -> {
                    result = second
                    true
                }
                else -> false
            }
        }
        return result
    }

    private fun taggedText(tag: String): String {
        val text = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.Text)
        return text?.joinToString(separator = "") { it.text }.orEmpty()
    }

    private fun clickableMatcher(target: SemanticsMatcher): SemanticsMatcher {
        val direct = target and hasClickAction()
        val clickableParent = hasClickAction() and hasAnyDescendant(target)
        return when {
            nodes(direct) > 0 -> direct
            nodes(clickableParent) > 0 -> clickableParent
            else -> target
        }
    }

    private fun clickTag(tag: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS) {
        waitForTag(tag, timeoutMillis)

        val matcher = clickableMatcher(hasTestTag(tag))
        val node = compose.onAllNodes(matcher, useUnmergedTree = true)[0]
        runCatching { node.performScrollTo() }
        node.assertExists().performClick()
    }

    private fun replaceTagText(tag: String, value: String) {
        waitForTag(tag)
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assertExists()
            .performTextReplacement(value)
    }

    private fun launchLoggedOutApp() {
        instrumentation.runOnMainSync {
            (context.applicationContext as MainApplication).logout()
            context.startActivity(
                Intent(context, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
        }
        waitForTag("acceptance.password.user")
    }

    private fun postLoginUiAction(): PostLoginUiAction? = nextPostLoginUiAction(
        welcomeEnterPresent = tagExists(POST_LOGIN_WELCOME_ENTER_TAG),
        introClosePresent = tagExists(POST_LOGIN_INTRO_CLOSE_TAG),
        closePresent = contentDescriptionExists("close"),
        closeOverlayPresent = contentDescriptionExists("Close Overlay"),
    )

    private fun dismissPostLoginUiAction(action: PostLoginUiAction) {
        val target = when (action) {
            PostLoginUiAction.WelcomeEnter -> hasTestTag(POST_LOGIN_WELCOME_ENTER_TAG)
            PostLoginUiAction.IntroClose -> hasTestTag(POST_LOGIN_INTRO_CLOSE_TAG)
            PostLoginUiAction.Close -> hasContentDescription("close")
            PostLoginUiAction.CloseOverlay -> hasContentDescription("Close Overlay")
        }
        val matcher = clickableMatcher(target)
        performTransientUiActionIfPresent(
            isPresent = { nodes(matcher) > 0 },
            action = {
                compose.onAllNodes(matcher, useUnmergedTree = true)[0].performClick()
            },
        )
    }

    private fun waitForMain() {
        // Post-login surfaces are asynchronous and can appear in sequence. A
        // one-shot scan can observe the gap between them and then wait forever
        // for navigation hidden below the next surface. Keep advancing until
        // navigation is present with no dismissable surface above it.
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(AUTH_TIMEOUT_MILLIS)
        while (true) {
            val action = postLoginUiAction()
            if (action == null && tagExists("acceptance.nav.connect")) break
            if (System.nanoTime() >= deadlineNanos) {
                throw AssertionError(
                    "Timed out advancing through post-login UI after ${AUTH_TIMEOUT_MILLIS / 1_000}s"
                )
            }

            if (action != null) dismissPostLoginUiAction(action)
            compose.waitForIdle()

            val remainingMillis = TimeUnit.NANOSECONDS
                .toMillis(deadlineNanos - System.nanoTime())
                .coerceIn(1, 1_000)
            runCatching {
                compose.waitUntil(remainingMillis) {
                    val currentAction = postLoginUiAction()
                    (currentAction == null && tagExists("acceptance.nav.connect")) ||
                        currentAction != action
                }
            }
        }
        clickTag("acceptance.nav.connect")
        waitForTag("acceptance.connect", AUTH_TIMEOUT_MILLIS)
    }

    private fun createInstantAccount(): String {
        log("create instant account through local UI")
        clickTag("acceptance.login.instant")
        clickTag("acceptance.instant.terms")
        compose.performEnabledSemanticsClick("acceptance.instant.create", AUTH_TIMEOUT_MILLIS)
        if (waitForEitherTag("acceptance.instant.copy", ACCEPTANCE_INSTANT_ERROR_TAG) == ACCEPTANCE_INSTANT_ERROR_TAG) {
            throw AssertionError("instant signup failed: ${taggedText(ACCEPTANCE_INSTANT_ERROR_TAG)}")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val previousClipboard = clipboard.primaryClip
        clickTag("acceptance.instant.copy")

        val copiedValue = try {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        } finally {
            if (previousClipboard == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                }
            } else {
                clipboard.setPrimaryClip(previousClipboard)
            }
        }
        val secretKey = copiedValue
            ?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")
            ?: throw AssertionError("instant-account UI did not copy a secret key")
        assertEquals("instant-account secret key word count", 24, secretKey.split(" ").size)
        writeFixture(secretKey)

        clickTag("acceptance.instant.continue")
        waitForMain()
        return secretKey
    }

    private fun loginWithSecretKey(secretKey: String) {
        log("sign in with saved secret key through local UI")
        clickTag("acceptance.login.secret")
        replaceTagText("acceptance.secret.input", secretKey)
        compose.performEnabledSemanticsClick("acceptance.secret.submit", AUTH_TIMEOUT_MILLIS)
        waitForMain()
    }

    private fun completePasswordPrompt(
        password: String,
        verificationCode: String? = null,
        retainClient: Boolean = true,
    ) {
        waitForTag("acceptance.password.input", AUTH_TIMEOUT_MILLIS)
        replaceTagText("acceptance.password.input", password)
        compose.performEnabledSemanticsClick("acceptance.password.submit", AUTH_TIMEOUT_MILLIS)
        if (verificationCode != null) {
            val destination = waitForEitherTag("acceptance.nav.connect", "acceptance.verify.code")
            if (destination == "acceptance.verify.code") {
                replaceTagText("acceptance.verify.code", verificationCode)
            }
        }
        waitForMain()
        if (retainClient) retainActiveClient()
    }

    private fun loginWithPassword(
        user: String,
        password: String,
        verificationCode: String? = null,
        retainClient: Boolean = true,
    ) {
        log("sign in with acceptance account through local UI")
        replaceTagText("acceptance.password.user", user)
        compose.performEnabledSemanticsClick("acceptance.password.next", AUTH_TIMEOUT_MILLIS)
        completePasswordPrompt(password, verificationCode, retainClient)
    }

    private data class SignupInputs(
        val networkPrefix: String,
        val password: String,
        val emailDomain: String,
        val emailPrefix: String,
        val phoneNumber: String,
    )

    private fun readSignupInputs(): SignupInputs {
        check(testsFile.isFile) { "resolved tests fixture was not installed" }
        val root = JSONObject(testsFile.readText())
        check(root.getJSONObject("lifecycle").getBoolean("allow_account_create_delete")) {
            "acceptance account create/delete is not authorized"
        }
        val signup = root.getJSONObject("signup")
        val email = signup.getJSONObject("email")
        val phone = signup.getJSONObject("phone")
        return SignupInputs(
            networkPrefix = signup.getString("network_name_prefix"),
            password = signup.getString("password"),
            emailDomain = email.getString("domain"),
            emailPrefix = email.getString("local_part_prefix"),
            phoneNumber = phone.getString("number"),
        )
    }

    private fun deleteCurrentNetwork() {
        val application = context.applicationContext as MainApplication
        val api = application.api ?: throw AssertionError("network delete has no active API")
        val complete = CountDownLatch(1)
        var failure: Throwable? = null
        api.networkDelete { _, error ->
            failure = error
            complete.countDown()
        }
        check(complete.await(45, TimeUnit.SECONDS)) { "network delete timed out" }
        failure?.let { throw AssertionError("network delete failed", it) }
        instrumentation.runOnMainSync { application.logout() }
    }

    private fun openNewPasswordSignup(
        userAuth: String,
        inputs: SignupInputs,
    ) {
        repeat(2) { attempt ->
            replaceTagText("acceptance.password.user", userAuth)
            compose.performEnabledSemanticsClick("acceptance.password.next", AUTH_TIMEOUT_MILLIS)
            when (waitForEitherTag("acceptance.create.network", "acceptance.password.input")) {
                "acceptance.create.network" -> return
                else -> {
                    // A configured fixture can survive interruption. Its exact
                    // identity is verified by server policy, so a code prompt
                    // here is a product/configuration failure.
                    completePasswordPrompt(inputs.password, null, retainClient = false)
                    deleteCurrentNetwork()
                    launchLoggedOutApp()
                    check(attempt == 0) { "dedicated password fixture could not be reset" }
                }
            }
        }
    }

    private fun passwordSignupLifecycle(
        method: String,
        userAuth: String,
        inputs: SignupInputs,
        iteration: Int,
    ) {
        log("$method signup through local UI")
        val suffix = "${BuildConfig.FLAVOR}-${System.currentTimeMillis()}-$iteration"
            .lowercase().replace(Regex("[^a-z0-9-]"), "-")
        val networkName = "${inputs.networkPrefix}-$method-$suffix".take(49).trimEnd('-')

        openNewPasswordSignup(userAuth, inputs)
        replaceTagText("acceptance.create.network", networkName)
        replaceTagText("acceptance.create.password", inputs.password)
        clickTag("acceptance.create.terms")
        compose.performEnabledSemanticsClick("acceptance.create.submit", AUTH_TIMEOUT_MILLIS)
        waitForMain()
        val createdNetwork = currentNetworkId()
        capture("$iteration-$method-signup")

        try {
            logoutThroughUi()
            loginWithPassword(userAuth, inputs.password, retainClient = false)
            assertEquals("$method login returned a different network", createdNetwork, currentNetworkId())
            capture("$iteration-$method-login")
            deleteCurrentNetwork()
            launchLoggedOutApp()
        } catch (error: Throwable) {
            // Once a signup has returned a JWT, always try to delete it before
            // propagating the product assertion. This keeps retries deterministic.
            runCatching {
                if (!(context.applicationContext as MainApplication).api?.byJwt.isNullOrEmpty()) {
                    deleteCurrentNetwork()
                }
            }.onFailure { error.addSuppressed(it) }
            throw error
        }
    }

    private fun retainActiveClient() {
        val application = context.applicationContext as MainApplication
        val clientJwt = application.asyncLocalState?.localState?.byClientJwt.orEmpty()
        val parts = clientJwt.split(".")
        check(parts.size == 3) { "password login returned an invalid client JWT" }
        val payload = String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        val clientId = JSONObject(payload).getString("client_id")
        check(clientId.isNotBlank()) { "password login returned no client ID" }

        acceptanceDir.mkdirs()
        val retained = File(acceptanceDir, "active-client-ids")
        val clientIds = if (retained.isFile) retained.readLines().toMutableSet() else mutableSetOf()
        if (clientIds.add(clientId)) {
            val temporary = File(acceptanceDir, "active-client-ids.tmp")
            temporary.writeText(clientIds.sorted().joinToString(separator = "\n", postfix = "\n"))
            check(temporary.renameTo(retained)) { "could not retain the active client ID" }
            retained.setReadable(false, false)
            retained.setWritable(false, false)
            retained.setReadable(true, true)
            retained.setWritable(true, true)
        }
    }

    private fun logoutThroughUi() {
        log("log out through local UI")
        clickTag("acceptance.nav.account")
        clickTag("acceptance.account.avatar")
        clickTag("acceptance.account.logout")
        waitForTag("acceptance.password.user", AUTH_TIMEOUT_MILLIS)
        val application = context.applicationContext as MainApplication
        assertTrue("logout retained the SDK network session", application.api?.byJwt.isNullOrEmpty())
    }

    private fun currentNetworkId(): String {
        val application = context.applicationContext as MainApplication
        val networkId = runCatching {
            application.asyncLocalState?.localState?.parseByJwt()?.networkId?.toString()
        }.getOrNull().orEmpty()
        assertTrue("authenticated SDK state has no network ID", networkId.isNotBlank())
        return networkId
    }

    private fun handleVpnConsentIfPresent() {
        val button = device.wait(
            Until.findObject(By.text(Pattern.compile("(?i)^(allow|ok)$"))),
            8_000,
        ) ?: device.findObject(By.res("android:id/button1"))
        button?.click()
    }

    private fun publicIp(): String {
        val value = EgressProbeRequest.queryPublicIp(instrumentation, EGRESS_TIMEOUT_MILLIS)
        waitForTag("acceptance.connect.status")
        return value
    }

    private fun connectAndVerifyEgress(iteration: Int) {
        val before = publicIp()
        log("physical egress before connect: $before")

        clickTag("acceptance.connect")
        handleVpnConsentIfPresent()
        waitFor("connected status", CONNECT_TIMEOUT_MILLIS) {
            contentDescriptionExists("Connected")
        }
        capture("${iteration}-connected")

        val after = publicIp()
        log("network egress after connect: $after")
        assertNotEquals("public IP did not change after connect", before, after)

        clickTag("acceptance.disconnect")
        waitForTag("acceptance.connect", CONNECT_TIMEOUT_MILLIS)
        waitFor("disconnected status", CONNECT_TIMEOUT_MILLIS) {
            contentDescriptionExists("Disconnected")
        }
        capture("${iteration}-disconnected")
    }

    private fun readCredentials(): Pair<String, String> {
        val lines = credentialsFile.readLines()
        check(lines.size == 2 && lines.all { it.isNotBlank() }) {
            "acceptance credentials were not installed at ${credentialsFile.absolutePath}"
        }
        return lines[0] to lines[1]
    }

    private fun readFixture(): String? {
        if (!fixtureFile.isFile) return null
        val secretKey = fixtureFile.readText().trim().lowercase().replace(Regex("\\s+"), " ")
        check(secretKey.split(" ").size == 24) { "invalid saved instant-account secret key" }
        return secretKey
    }

    private fun writeFixture(secretKey: String) {
        acceptanceDir.mkdirs()
        val temporary = File(acceptanceDir, "guest-secret-key.tmp")
        temporary.writeText("$secretKey\n")
        check(temporary.renameTo(fixtureFile)) { "could not persist instant-account fixture" }
        fixtureFile.setReadable(false, false)
        fixtureFile.setWritable(false, false)
        fixtureFile.setReadable(true, true)
        fixtureFile.setWritable(true, true)
    }

    private fun capture(name: String) {
        screenshotsDir.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        try {
            FileOutputStream(File(screenshotsDir, "$name.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test(timeout = 3_600_000)
    fun mainEnvironmentAcceptance() {
        val arguments = InstrumentationRegistry.getArguments()
        val expectedBuildId = arguments.getString("acceptanceBuildId").orEmpty()
        assertTrue("acceptanceBuildId argument is required", expectedBuildId.isNotBlank())
        assertEquals(
            "installed app is not the APK built for this acceptance run",
            expectedBuildId,
            BuildConfig.URNETWORK_ACCEPTANCE_BUILD_ID,
        )
        assertEquals("acceptance APK must target the main environment", "main", BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        assertEquals("acceptance APK must target the official network", "ur.network", BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME)

        val repetitions = arguments.getString("repeat")?.toIntOrNull() ?: 1
        assertTrue("repeat must be positive", repetitions > 0)
        val (user, password) = readCredentials()
        val signupInputs = readSignupInputs()
        acceptanceDir.mkdirs()
        resultFile.delete()
        screenshotsDir.deleteRecursively()
        launchLoggedOutApp()

        var secretKey = readFixture()
        repeat(repetitions) { zeroBasedIteration ->
            val iteration = zeroBasedIteration + 1
            log("BEGIN repetition $iteration/$repetitions; flavor=${BuildConfig.FLAVOR}; build=$expectedBuildId")
            try {
                val email = "${signupInputs.emailPrefix}-${BuildConfig.FLAVOR}-${System.currentTimeMillis()}-$iteration@${signupInputs.emailDomain}"
                    .lowercase().replace('_', '-')
                passwordSignupLifecycle("email", email, signupInputs, iteration)
                passwordSignupLifecycle("phone", signupInputs.phoneNumber, signupInputs, iteration)

                if (secretKey == null) {
                    secretKey = createInstantAccount()
                } else {
                    loginWithSecretKey(checkNotNull(secretKey))
                }
                val guestNetworkId = currentNetworkId()
                capture("${iteration}-instant-account")
                logoutThroughUi()

                loginWithSecretKey(checkNotNull(secretKey))
                assertEquals(
                    "secret-key login recovered a different network",
                    guestNetworkId,
                    currentNetworkId(),
                )
                capture("${iteration}-secret-key-login")
                logoutThroughUi()

                loginWithPassword(user, password)
                connectAndVerifyEgress(iteration)
                logoutThroughUi()
                log("PASS repetition $iteration/$repetitions")
            } catch (error: Throwable) {
                capture("${iteration}-failure")
                throw error
            } finally {
                // A failed assertion must not leave a production connection or
                // authenticated device behind.  This recovery path does not
                // replace the UI logout assertion above.
                if (!tagExists("acceptance.password.user")) {
                    runCatching {
                        if (tagExists("acceptance.disconnect")) clickTag("acceptance.disconnect", 5_000)
                    }
                    instrumentation.runOnMainSync {
                        (context.applicationContext as MainApplication).logout()
                    }
                }
            }

            if (iteration < repetitions) {
                launchLoggedOutApp()
            }
        }

        resultFile.writeText("$expectedBuildId\n$repetitions\n${BuildConfig.FLAVOR}\n")
        resultFile.setReadable(false, false)
        resultFile.setWritable(false, false)
        resultFile.setReadable(true, true)
        resultFile.setWritable(true, true)
    }

    private companion object {
        const val TAG = "URAcceptance"
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val AUTH_TIMEOUT_MILLIS = 90_000L
        const val CONNECT_TIMEOUT_MILLIS = 120_000L
        // Two independent endpoints are attempted sequentially so one external
        // DNS timeout cannot decide the data-plane result.
        const val EGRESS_TIMEOUT_MILLIS = 45_000L
    }
}
