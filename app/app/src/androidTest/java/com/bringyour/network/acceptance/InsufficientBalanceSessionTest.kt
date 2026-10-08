package com.bringyour.network.acceptance

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bringyour.network.BuildConfig
import com.bringyour.network.LoginActivity
import com.bringyour.network.LoginStartupState
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.POST_LOGIN_INTRO_CLOSE_TAG
import com.bringyour.network.ui.POST_LOGIN_MAIN_READY_TAG
import com.bringyour.network.ui.POST_LOGIN_OVERLAY_CLOSE_TAG
import com.bringyour.network.ui.POST_LOGIN_WELCOME_ENTER_TAG
import com.bringyour.network.ui.PostLoginUiAction
import com.bringyour.network.ui.nextPostLoginUiAction
import com.bringyour.network.ui.performTransientUiActionIfPresent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The device half of MAIN's insufficient-balance case, driven by
 * test-insufficient-balance-driver on the owned acceptance AVD.
 *
 * Like [PhysicalLowbarSessionTest], one instrumentation process stays alive for
 * the whole platform run: the VPN service lives in the app process, so a fresh
 * instrumentation per step would restart the app and end the tunnel, which is
 * exactly the automatic disconnect the case must rule out. The host writes one
 * `insufficient-balance-command` at a time and reads
 * `insufficient-balance-status` (see InsufficientBalanceSession.kt).
 *
 * The UI is read and pressed through the same Compose test tags MAIN uses. The
 * egress and traffic probes run in the test APK's UID, which the VPN captures
 * like any other app; the app's own UID is excluded from its VPN. The
 * notification count is sampled from the app's active notifications so it
 * counts posts since setup, not just what is showing.
 *
 * A failed command reports `failed` and the session keeps serving, so teardown
 * can still disconnect and log out.
 */
@RunWith(AndroidJUnit4::class)
class InsufficientBalanceSessionTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uiDevice = UiDevice.getInstance(instrumentation)
    private val acceptanceDir = File(context.filesDir, "acceptance")
    private val credentialsFile = File(acceptanceDir, "credentials")
    private val commandFile = File(acceptanceDir, "insufficient-balance-command")
    private val statusFile = File(acceptanceDir, "insufficient-balance-status")
    private val activeClientLedger = ActiveClientLedger(File(acceptanceDir, "active-client-ids"))
    private val notificationCounter = InsufficientBalanceNotificationCounter()
    // the user's kill switch setting before the session, restored on finish
    private var originalRouteLocal: Boolean? = null

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private fun tagExists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun contentDescriptionExists(description: String): Boolean =
        compose.onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()

    private fun nodes(matcher: SemanticsMatcher): Int =
        compose.onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .size

    private fun waitFor(description: String, timeoutMillis: Long, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis) { condition() }
        } catch (error: Throwable) {
            throw AssertionError("Timed out waiting for $description after ${timeoutMillis / 1_000}s", error)
        }
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
        waitFor("UI tag $tag", timeoutMillis) { tagExists(tag) }
        val node = compose.onAllNodes(clickableMatcher(hasTestTag(tag)), useUnmergedTree = true)[0]
        runCatching { node.performScrollTo() }
        node.assertExists().performClick()
    }

    /** The password tags may sit under an autofill save prompt, as in MainAcceptanceTest. */
    private inner class PasswordLoginUiOnEmulator : PasswordLoginUi {
        private val compose = ComposePasswordLoginUi(this@InsufficientBalanceSessionTest.compose)

        override fun waitForTag(tag: String, timeoutMillis: Long) =
            uiDevice.withVerifiedAutofillSaveDismissed { compose.waitForTag(tag, timeoutMillis) }

        override fun replaceTagText(tag: String, value: String) =
            uiDevice.withVerifiedAutofillSaveDismissed { compose.replaceTagText(tag, value) }

        override fun performEnabledTagClick(tag: String, timeoutMillis: Long) =
            uiDevice.withVerifiedAutofillSaveDismissed { compose.performEnabledTagClick(tag, timeoutMillis) }
    }

    private fun postLoginUiAction(): PostLoginUiAction? = nextPostLoginUiAction(
        welcomeEnterPresent = tagExists(POST_LOGIN_WELCOME_ENTER_TAG),
        introClosePresent = tagExists(POST_LOGIN_INTRO_CLOSE_TAG),
        closePresent = contentDescriptionExists("close"),
        closeOverlayPresent = tagExists(POST_LOGIN_OVERLAY_CLOSE_TAG),
    )

    private fun dismissPostLoginUiAction(action: PostLoginUiAction) {
        val target = when (action) {
            PostLoginUiAction.WelcomeEnter -> hasTestTag(POST_LOGIN_WELCOME_ENTER_TAG)
            PostLoginUiAction.IntroClose -> hasTestTag(POST_LOGIN_INTRO_CLOSE_TAG)
            PostLoginUiAction.Close -> hasContentDescription("close")
            PostLoginUiAction.CloseOverlay -> hasTestTag(POST_LOGIN_OVERLAY_CLOSE_TAG)
        }
        val matcher = clickableMatcher(target)
        performTransientUiActionIfPresent(
            isPresent = { nodes(matcher) > 0 },
            action = { compose.onAllNodes(matcher, useUnmergedTree = true)[0].performClick() },
        )
    }

    /** MainAcceptanceTest's post-login wait, ending on the connect screen. */
    private fun waitForConnectScreen(application: MainApplication) {
        waitForMainUi(object : MainUiWaitDriver {
            override fun nowNanos(): Long = System.nanoTime()

            override fun observe(): MainUiWaitEvidence {
                val action = postLoginUiAction()
                return MainUiWaitEvidence(
                    action = action,
                    mainNavigationPresent = action == null && tagExists("acceptance.nav.connect"),
                    signupFormErrorPresent = false,
                    mainNavigationReady = tagExists(POST_LOGIN_MAIN_READY_TAG),
                )
            }

            override fun dismiss(action: PostLoginUiAction) = dismissPostLoginUiAction(action)
            override fun waitForIdle() = compose.waitForIdle()
            override fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
                try {
                    compose.waitUntil(timeoutMillis, condition)
                } catch (_: ComposeTimeoutException) {
                    // The shared wait owns the overall deadline. Only this
                    // bounded poll timeout is expected; driver failures escape.
                }
            }

            override fun timeout(): Nothing {
                val state = application.loginStartupState.value
                throw AssertionError("Timed out reaching the connect screen; login state=${state.javaClass.simpleName}")
            }
        }, AUTH_TIMEOUT_MILLIS)
        clickTag("acceptance.nav.connect")
        waitFor("connect screen", AUTH_TIMEOUT_MILLIS) {
            tagExists(INSUFFICIENT_BALANCE_CONNECT_TAG) || tagExists(INSUFFICIENT_BALANCE_DISCONNECT_TAG)
        }
    }

    private fun login(application: MainApplication) {
        val lines = credentialsFile.readLines()
        check(lines.size == 2 && lines.all { it.isNotBlank() }) {
            "insufficient-balance credentials were not installed"
        }
        instrumentation.runOnMainSync {
            application.logout()
            context.startActivity(
                Intent(context, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }
        performPasswordLogin(
            ui = PasswordLoginUiOnEmulator(),
            user = lines[0],
            password = lines[1],
            uiTimeoutMillis = UI_TIMEOUT_MILLIS,
            authTimeoutMillis = AUTH_TIMEOUT_MILLIS,
        )
        val deadline = SystemClock.elapsedRealtime() + AUTH_TIMEOUT_MILLIS
        while (application.loginStartupState.value !is LoginStartupState.Ready) {
            when (val state = application.loginStartupState.value) {
                is LoginStartupState.Failed ->
                    throw AssertionError("login startup failed at ${state.stage.wireValue}: ${state.failure.wireValue}")
                else -> Unit
            }
            check(SystemClock.elapsedRealtime() < deadline) { "login did not reach an authenticated device" }
            SystemClock.sleep(100)
        }
        waitForConnectScreen(application)
    }

    /** The connect screen is hidden while a probe activity is in front; wait for it to return. */
    private fun returnToConnectScreen() {
        val visible = runCatching {
            compose.waitUntil(RETURN_TIMEOUT_MILLIS) { tagExists("acceptance.connect.status") }
            true
        }.getOrDefault(false)
        if (!visible) {
            instrumentation.runOnMainSync {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    },
                )
            }
            waitFor("connect screen after a probe", UI_TIMEOUT_MILLIS) { tagExists("acceptance.connect.status") }
        }
    }

    private fun observe(application: MainApplication): InsufficientBalanceObservation {
        returnToConnectScreen()
        compose.waitForIdle()
        return InsufficientBalanceObservation(
            // the user's request, which only Disconnect may clear
            connectRequested = application.device?.connectEnabled == true,
            connected = contentDescriptionExists(context.getString(R.string.connected)),
            alert = tagExists(INSUFFICIENT_BALANCE_ALERT_TAG),
            disconnectVisible = tagExists(INSUFFICIENT_BALANCE_DISCONNECT_TAG),
            upgradeVisible = tagExists(INSUFFICIENT_BALANCE_UPGRADE_TAG),
            notifications = notificationCounter.count(),
        )
    }

    private fun egress(): List<Pair<String, String>> {
        val fields = try {
            listOf("ip" to EgressProbeRequest.queryPublicIp(instrumentation, EGRESS_TIMEOUT_MILLIS))
        } catch (error: Throwable) {
            // a probe that cannot leave is what a held tunnel looks like
            listOf("egress_error" to (error.message ?: error.javaClass.simpleName))
        }
        returnToConnectScreen()
        return fields
    }

    private fun traffic(): List<Pair<String, String>> {
        val message = runCatching {
            EgressProbeRequest.downloadTraffic(instrumentation, EGRESS_TIMEOUT_MILLIS, TRAFFIC_BYTES)
        }.getOrElse { "ACCEPTANCE_ERROR=${it.message ?: it.javaClass.simpleName}" }
        returnToConnectScreen()
        return listOf("traffic" to message)
    }

    private fun connect(application: MainApplication) {
        returnToConnectScreen()
        clickTag(INSUFFICIENT_BALANCE_CONNECT_TAG)
        uiDevice.clickVerifiedVpnConsentIfPresent()
        waitFor("connect requested", UI_TIMEOUT_MILLIS) { application.device?.connectEnabled == true }
    }

    private fun writeStatus(text: String) {
        acceptanceDir.mkdirs()
        val temporary = File(acceptanceDir, "${statusFile.name}.tmp")
        temporary.writeText(text)
        temporary.setReadable(false, false)
        temporary.setReadable(true, true)
        check(temporary.renameTo(statusFile)) { "could not publish ${statusFile.name}" }
    }

    private fun startNotificationSampler(stopped: AtomicBoolean): Thread {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return thread(name = "insufficient-balance-notifications", isDaemon = true) {
            while (!stopped.get()) {
                runCatching {
                    notificationCounter.sample(
                        notificationManager.activeNotifications.map { it.id to it.postTime },
                    )
                }
                SystemClock.sleep(NOTIFICATION_SAMPLE_MILLIS)
            }
        }
    }

    /** Runs one command; true when the session should end. */
    private fun runCommand(command: InsufficientBalanceCommand, application: MainApplication): Boolean {
        writeStatus(insufficientBalanceStatusText(command.id, "running"))
        val fields: List<Pair<String, String>> = when (command.verb) {
            InsufficientBalanceVerb.OBSERVE -> observe(application).statusFields()
            InsufficientBalanceVerb.CONNECT -> {
                connect(application)
                emptyList()
            }
            InsufficientBalanceVerb.EGRESS -> egress()
            InsufficientBalanceVerb.TRAFFIC -> traffic()
            InsufficientBalanceVerb.PRESS_DISCONNECT -> {
                returnToConnectScreen()
                clickTag(INSUFFICIENT_BALANCE_DISCONNECT_TAG, PRESS_TIMEOUT_MILLIS)
                emptyList()
            }
            InsufficientBalanceVerb.KILL_SWITCH -> {
                // the Settings kill switch toggle writes exactly this
                val device = checkNotNull(application.device) { "no authenticated device" }
                device.routeLocal = command.argument != "on"
                emptyList()
            }
            InsufficientBalanceVerb.FINISH -> {
                finish(application)
                writeStatus(insufficientBalanceStatusText(command.id, "complete"))
                return true
            }
        }
        writeStatus(insufficientBalanceStatusText(command.id, "complete", fields))
        return false
    }

    private fun finish(application: MainApplication) {
        runCatching {
            if (application.device?.connectEnabled == true) {
                returnToConnectScreen()
                clickTag(INSUFFICIENT_BALANCE_DISCONNECT_TAG, PRESS_TIMEOUT_MILLIS)
            }
        }
        originalRouteLocal?.let { routeLocal -> runCatching { application.device?.routeLocal = routeLocal } }
    }

    @Test(timeout = 7_200_000)
    fun insufficientBalanceSession() {
        val arguments = InstrumentationRegistry.getArguments()
        val expectedBuildId = arguments.getString("acceptanceBuildId").orEmpty()
        assertTrue("acceptanceBuildId argument is required", expectedBuildId.isNotBlank())
        assertEquals(
            "installed app is not the APK built for this acceptance run",
            expectedBuildId,
            BuildConfig.URNETWORK_ACCEPTANCE_BUILD_ID,
        )
        assertEquals("main", BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        assertEquals("bringyour.com", BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME)

        acceptanceDir.mkdirs()
        commandFile.delete()
        statusFile.delete()

        val application = context.applicationContext as MainApplication
        val ledgerFailure = AtomicReference<Throwable?>()
        val removeAllocationListener = application.addLoginClientAllocationListener { allocation ->
            runCatching { activeClientLedger.retain(allocation.clientId) }
                .onFailure { ledgerFailure.compareAndSet(null, it) }
        }
        val stopped = AtomicBoolean(false)
        var sampler: Thread? = null
        var activeCommandId = "0"
        try {
            login(application)
            ledgerFailure.get()?.let { throw it }
            originalRouteLocal = application.device?.routeLocal
            // counts posts since setup, so a notice left by an earlier run is not ours
            sampler = startNotificationSampler(stopped)
            writeStatus(insufficientBalanceStatusText("0", "ready"))

            var lastCommandId = "0"
            val deadline = SystemClock.elapsedRealtime() + MAX_SESSION_MILLIS
            var finished = false
            while (!finished && SystemClock.elapsedRealtime() < deadline) {
                val text = runCatching { commandFile.readText().trim() }.getOrDefault("")
                val id = text.substringBefore('|')
                if (text.isNotEmpty() && id != lastCommandId) {
                    lastCommandId = id
                    activeCommandId = id
                    finished = try {
                        runCommand(parseInsufficientBalanceCommand(text), application)
                    } catch (error: Throwable) {
                        log("command $id failed: ${error.message}")
                        writeStatus(
                            insufficientBalanceStatusText(
                                id,
                                "failed",
                                listOf("error" to (error.message ?: error.javaClass.simpleName)),
                            ),
                        )
                        false
                    }
                }
                SystemClock.sleep(COMMAND_POLL_MILLIS)
            }
            assertTrue("insufficient-balance session reached its safety timeout", finished)
        } catch (error: Throwable) {
            runCatching {
                writeStatus(
                    insufficientBalanceStatusText(
                        activeCommandId,
                        "failed",
                        listOf("error" to (error.message ?: error.javaClass.simpleName)),
                    ),
                )
            }.onFailure(error::addSuppressed)
            throw error
        } finally {
            stopped.set(true)
            sampler?.join(1_000)
            removeAllocationListener()
            runCatching { finish(application) }
            credentialsFile.delete()
            instrumentation.runOnMainSync { application.logout() }
        }
    }

    private companion object {
        const val TAG = "URInsufficientBalance"
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val AUTH_TIMEOUT_MILLIS = 90_000L
        const val PRESS_TIMEOUT_MILLIS = 5_000L
        const val RETURN_TIMEOUT_MILLIS = 5_000L
        // the probe's own 40 s bound answers before this
        const val EGRESS_TIMEOUT_MILLIS = 45_000L
        const val TRAFFIC_BYTES = 4L * 1024 * 1024
        const val COMMAND_POLL_MILLIS = 250L
        const val NOTIFICATION_SAMPLE_MILLIS = 200L
        const val MAX_SESSION_MILLIS = 7_000_000L
    }
}
