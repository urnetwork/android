package com.bringyour.network.acceptance

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.bringyour.network.BuildConfig
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.ConnectLocationId
import com.bringyour.sdk.ConnectViewController
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.PacketStats
import com.bringyour.sdk.PeerViewController
import com.bringyour.sdk.Sdk
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A long-lived, host-commanded physical-device session for LOWBAR validation.
 *
 * The host installs credentials in the same private acceptance directory used
 * by [MainAcceptanceTest], starts this test once, and then atomically writes
 * `physical-command` records of the form `ID|VERB|ARG`. Keeping one process and
 * one authenticated client alive is important: otherwise every transport or
 * underlay cell would silently measure a fresh process and create another
 * production client. Results contain aggregate counters only; client IDs are
 * retained separately solely so the host can delete the temporary clients.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalLowbarSessionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val uiDevice = UiDevice.getInstance(instrumentation)
    private val acceptanceDir = File(context.filesDir, "acceptance")
    private val credentialsFile = File(acceptanceDir, "credentials")
    private val commandFile = File(acceptanceDir, "physical-command")
    private val statusFile = File(acceptanceDir, "physical-status")
    private val samplesFile = File(acceptanceDir, "physical-memory.ndjson")
    private val summaryFile = File(acceptanceDir, "physical-summary.json")
    private val activeClientFile = File(acceptanceDir, "physical-active-client-id")

    @Volatile
    private var phase = "startup"

    private fun waitFor(
        description: String,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (condition()) return
            } catch (error: Throwable) {
                lastError = error
            }
            SystemClock.sleep(100)
        }
        throw AssertionError(
            "Timed out waiting for $description after ${timeoutMillis / 1_000}s",
            lastError,
        )
    }

    private fun launchLoggedOutApp(application: MainApplication) {
        instrumentation.runOnMainSync {
            application.logout()
            context.startActivity(
                Intent(context, LoginActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }
        waitFor("password-login user field") { editableFields().isNotEmpty() }
    }

    private fun editableFields(): List<UiObject2> =
        uiDevice.findObjects(By.clazz("android.widget.EditText"))
            // Compose can replace a semantics node between findObjects and
            // isEnabled while the login screen advances. A stale candidate is
            // simply no longer an editable field; the surrounding wait loop
            // will query the current tree again.
            .mapNotNull { field ->
                runCatching { field.takeIf { it.isEnabled } }.getOrNull()
            }

    private fun clickText(text: String, timeoutMillis: Long = UI_TIMEOUT_MILLIS) {
        val objectToClick = uiDevice.wait(Until.findObject(By.text(text)), timeoutMillis)
            ?: throw AssertionError("Timed out waiting for text $text")
        objectToClick.click()
    }

    private fun loginWithPassword(application: MainApplication) {
        val lines = credentialsFile.readLines()
        check(lines.size == 2 && lines.all { it.isNotBlank() }) {
            "physical credentials were not installed"
        }
        val userField = editableFields().firstOrNull()
            ?: throw AssertionError("password-login user field is unavailable")
        userField.text = lines[0]
        clickText("Get started")
        waitFor("password field", AUTH_TIMEOUT_MILLIS) {
            editableFields().any { it.text != lines[0] }
        }
        val passwordField = editableFields().lastOrNull { it.text != lines[0] }
            ?: throw AssertionError("password field is unavailable")
        passwordField.text = lines[1]
        clickText("Continue")
        waitFor("authenticated DeviceLocal", AUTH_TIMEOUT_MILLIS) { application.device != null }
        instrumentation.runOnMainSync {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        }
        retainActiveClient(application)
    }

    private fun retainActiveClient(application: MainApplication) {
        val clientJwt = application.asyncLocalState?.localState?.byClientJwt.orEmpty()
        val parts = clientJwt.split(".")
        check(parts.size == 3) { "password login returned an invalid client JWT" }
        val payload = String(
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        val clientId = JSONObject(payload).getString("client_id")
        check(clientId.matches(Regex("[A-Za-z0-9._-]+"))) {
            "password login returned an invalid client ID"
        }
        writePrivate(activeClientFile, "$clientId\n")
    }

    private fun handleVpnConsentIfPresent() {
        val button = uiDevice.wait(
            Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)^(allow|ok)$"))),
            8_000,
        ) ?: uiDevice.findObject(By.res("android:id/button1"))
        button?.click()
    }

    private fun writePrivate(destination: File, text: String) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(text)
        temporary.setReadable(false, false)
        temporary.setWritable(false, false)
        temporary.setReadable(true, true)
        temporary.setWritable(true, true)
        check(!destination.exists() || destination.delete()) {
            "could not replace ${destination.name}"
        }
        check(temporary.renameTo(destination)) {
            "could not publish ${destination.name}"
        }
    }

    private fun packetJson(stats: PacketStats?): JSONObject? {
        stats ?: return null
        val result = JSONObject()
            .put("remoteEgressPackets", stats.remoteEgressPacketCount)
            .put("remoteEgressBytes", stats.remoteEgressByteCount)
            .put("remoteIngressPackets", stats.remoteIngressPacketCount)
            .put("remoteIngressBytes", stats.remoteIngressByteCount)
            .put("localEgressPackets", stats.localEgressPacketCount)
            .put("localEgressBytes", stats.localEgressByteCount)
            .put("localIngressPackets", stats.localIngressPacketCount)
            .put("localIngressBytes", stats.localIngressByteCount)
            .put("blockEgressPackets", stats.blockEgressPacketCount)
            .put("blockEgressBytes", stats.blockEgressByteCount)
            .put("blockIngressPackets", stats.blockIngressPacketCount)
            .put("blockIngressBytes", stats.blockIngressByteCount)
        val transports = JSONObject()
        stats.transportStats?.let { list ->
            for (i in 0 until list.len()) {
                val entry = list.get(i) ?: continue
                val transportStats = entry.stats ?: continue
                transports.put(
                    entry.transportType,
                    JSONObject()
                        .put("egressPackets", transportStats.remoteEgressPacketCount)
                        .put("egressBytes", transportStats.remoteEgressByteCount)
                        .put("ingressPackets", transportStats.remoteIngressPacketCount)
                        .put("ingressBytes", transportStats.remoteIngressByteCount),
                )
            }
        }
        return result.put("transports", transports)
    }

    private fun snapshot(application: MainApplication, startElapsedMs: Long): JSONObject {
        val sdk = Sdk.getMemoryStats()
        val javaRuntime = Runtime.getRuntime()
        val device = application.device
        val result = JSONObject()
            .put("type", "sample")
            .put("elapsedMs", SystemClock.elapsedRealtime() - startElapsedMs)
            .put("timeUnixMs", System.currentTimeMillis())
            .put("phase", phase)
            .put("goHeapLiveBytes", sdk.heapLiveByteCount)
            .put("goHeapGoalBytes", sdk.heapGoalByteCount)
            .put("goRuntimeBytes", sdk.totalRuntimeByteCount)
            .put("goMemoryLimitBytes", sdk.memoryLimitByteCount)
            .put("goHeapAllocBytes", sdk.heapAllocByteCount)
            .put("goHeapSystemBytes", sdk.heapSystemByteCount)
            .put("goHeapInuseBytes", sdk.heapInuseByteCount)
            .put("goHeapIdleBytes", sdk.heapIdleByteCount)
            .put("goHeapReleasedBytes", sdk.heapReleasedByteCount)
            .put(
                "goHeapRetainedBytes",
                maxOf(0L, sdk.heapIdleByteCount - sdk.heapReleasedByteCount),
            )
            .put(
                "goHeapFragmentationBytes",
                maxOf(0L, sdk.heapInuseByteCount - sdk.heapAllocByteCount),
            )
            .put("goHeapObjects", sdk.heapObjectCount)
            .put("goStackInuseBytes", sdk.stackInuseByteCount)
            .put("goMspanInuseBytes", sdk.getMSpanInuseByteCount())
            .put("goMcacheInuseBytes", sdk.getMCacheInuseByteCount())
            .put("goGcSystemBytes", sdk.gcSystemByteCount)
            .put("goOtherSystemBytes", sdk.otherSystemByteCount)
            .put("goProfilingBucketBytes", sdk.profilingBucketByteCount)
            .put("goSystemBytes", sdk.systemByteCount)
            .put("goMemoryProfileRateBytes", sdk.memoryProfileRateByteCount)
            .put("goTotalAllocatedBytes", sdk.totalAllocatedByteCount)
            .put("goMallocCount", sdk.mallocCount)
            .put("goFreeCount", sdk.freeCount)
            .put("goGcCycles", sdk.gcCycleCount)
            .put("goForcedGcCycles", sdk.getForcedGCCycleCount())
            .put("goGcPauseTotalNs", sdk.gcPauseTotalNanoseconds)
            .put("goroutines", sdk.goroutineCount)
            .put("poolTaken", sdk.poolTakenCount)
            .put("poolReturned", sdk.poolReturnedCount)
            .put("poolOutstanding", sdk.poolTakenCount - sdk.poolReturnedCount)
            .put("poolCreated", sdk.poolCreatedCount)
            .put("poolRetained", sdk.poolRetainedCount)
            .put("poolRetainedBytes", sdk.poolRetainedByteCount)
            .put("poolCapacityBytes", sdk.poolCapacityByteCount)
            .put("packetPoolRetained", sdk.packetPoolRetainedCount)
            .put("packetPoolRetainedBytes", sdk.packetPoolRetainedByteCount)
            .put("largeObjectPoolRetained", sdk.largeObjectPoolRetainedCount)
            .put("largeObjectPoolRetainedBytes", sdk.largeObjectPoolRetainedByteCount)
            .put("idleMemoryTrimCount", sdk.idleMemoryTrimCount)
            .put("lastIdleMemoryTrimDroppedBytes", sdk.lastIdleMemoryTrimDroppedByteCount)
            .put("androidPssKib", Debug.getPss())
            .put("nativeHeapAllocatedBytes", Debug.getNativeHeapAllocatedSize())
            .put("javaHeapUsedBytes", javaRuntime.totalMemory() - javaRuntime.freeMemory())
            .put("javaHeapCommittedBytes", javaRuntime.totalMemory())
            .put("javaHeapMaxBytes", javaRuntime.maxMemory())
            .put("threadCount", File("/proc/self/task").list()?.size ?: -1)
            .put("fdCount", File("/proc/self/fd").list()?.size ?: -1)
        if (device != null) {
            val tracked = device.memoryUsed()
            result
                .put("connected", device.connectEnabled)
                .put("tunnelStarted", device.tunnelStarted)
                .put("provideEnabled", device.provideEnabled)
                .put("provideMode", device.provideMode)
                .put("trackedMemory", JSONObject()
                    .put("targetBytes", tracked.targetByteCount)
                    .put("dnsBytes", tracked.dnsByteCount)
                    .put("clientSendBytes", tracked.clientSendByteCount)
                    .put("clientReceiveBytes", tracked.clientReceiveByteCount)
                    .put("providerSendBytes", tracked.providerSendByteCount)
                    .put("providerReceiveBytes", tracked.providerReceiveByteCount)
                    .put("totalBytes", tracked.totalByteCount))
                .put("packets", packetJson(device.packetStats))
                .put("providerPackets", packetJson(device.providerPacketStats))

            val exits = device.exits
            var liveExitCount = 0
            var p2pOnlyExitCount = 0
            for (i in 0 until exits.len()) {
                val exit = exits.get(i) ?: continue
                if (!exit.done) liveExitCount += 1
                if (!exit.done && exit.p2pOnly) p2pOnlyExitCount += 1
            }
            result
                .put("liveExitCount", liveExitCount)
                .put("p2pOnlyExitCount", p2pOnlyExitCount)
                .put("transportMode", device.transportSettings?.mode)
                .put("autoDegraded", device.transportStatus?.autoDegraded)
                .put("autoConstraint", device.transportStatus?.autoConstraint)
        }
        return result
    }

    private class SampleSummary {
        val count = AtomicLong()
        val thresholdBreaches = AtomicLong()
        val peakGoRuntimeBytes = AtomicLong()
        val peakGoHeapLiveBytes = AtomicLong()
        val peakAndroidPssKib = AtomicLong()
        val peakGoroutines = AtomicLong()
        val peakPoolOutstanding = AtomicLong()

        fun observe(sample: JSONObject) {
            count.incrementAndGet()
            updateMax(peakGoRuntimeBytes, sample.optLong("goRuntimeBytes"))
            updateMax(peakGoHeapLiveBytes, sample.optLong("goHeapLiveBytes"))
            updateMax(peakAndroidPssKib, sample.optLong("androidPssKib"))
            updateMax(peakGoroutines, sample.optLong("goroutines"))
            updateMax(peakPoolOutstanding, sample.optLong("poolOutstanding"))
            if (sample.optLong("goRuntimeBytes") > GO_RUNTIME_SPIKE_BYTES) {
                thresholdBreaches.incrementAndGet()
            }
        }

        fun json(): JSONObject = JSONObject()
            .put("sampleCount", count.get())
            .put("goRuntimeThresholdBytes", GO_RUNTIME_SPIKE_BYTES)
            .put("goRuntimeThresholdBreachCount", thresholdBreaches.get())
            .put("peakGoRuntimeBytes", peakGoRuntimeBytes.get())
            .put("peakGoHeapLiveBytes", peakGoHeapLiveBytes.get())
            .put("peakAndroidPssKib", peakAndroidPssKib.get())
            .put("peakGoroutines", peakGoroutines.get())
            .put("peakPoolOutstanding", peakPoolOutstanding.get())

        private fun updateMax(target: AtomicLong, value: Long) {
            var current = target.get()
            while (current < value && !target.compareAndSet(current, value)) {
                current = target.get()
            }
        }
    }

    private fun startSampler(
        application: MainApplication,
        startElapsedMs: Long,
        stopped: AtomicBoolean,
        summary: SampleSummary,
    ) = thread(name = "physical-lowbar-memory", isDaemon = true) {
        FileOutputStream(samplesFile, false).bufferedWriter().use { writer ->
            var nextSample = SystemClock.elapsedRealtime()
            while (!stopped.get()) {
                val record = runCatching { snapshot(application, startElapsedMs) }
                    .getOrElse { error ->
                        JSONObject()
                            .put("type", "sample-error")
                            .put("elapsedMs", SystemClock.elapsedRealtime() - startElapsedMs)
                            .put("timeUnixMs", System.currentTimeMillis())
                            .put("phase", phase)
                            .put("errorType", error.javaClass.simpleName)
                    }
                if (record.optString("type") == "sample") summary.observe(record)
                writer.append(record.toString()).append('\n')
                writer.flush()
                nextSample += SAMPLE_INTERVAL_MILLIS
                val sleepMillis = nextSample - SystemClock.elapsedRealtime()
                if (sleepMillis > 0) SystemClock.sleep(sleepMillis)
            }
        }
    }

    private fun status(
        id: String,
        state: String,
        application: MainApplication,
        startElapsedMs: Long,
        extra: JSONObject = JSONObject(),
    ) {
        val value = snapshot(application, startElapsedMs)
            .put("type", "status")
            .put("commandId", id)
            .put("state", state)
            .put("extra", extra)
        writePrivate(statusFile, "${value}\n")
    }

    private fun stopClient(connectVc: ConnectViewController, device: DeviceLocal) {
        if (device.connectEnabled || connectVc.connected) {
            connectVc.disconnect()
            waitFor("client disconnect", CONNECT_TIMEOUT_MILLIS) {
                !device.connectEnabled && !connectVc.connected
            }
        }
    }

    private fun stopProvider(application: MainApplication, device: DeviceLocal) {
        if (device.provideEnabled || device.provideMode != Sdk.ProvideModeNone) {
            application.deviceManager.provideControlMode = ProvideControlMode.NEVER
            device.providePaused = true
            waitFor("provider stop", CONNECT_TIMEOUT_MILLIS) {
                !device.provideEnabled && device.provideMode == Sdk.ProvideModeNone
            }
        }
    }

    private fun configureClientMode(device: DeviceLocal, mode: String) {
        require(mode in setOf(Sdk.TransportModeH1, Sdk.TransportModeH3, Sdk.TransportModeAuto)) {
            "unsupported transport mode"
        }
        device.transportSettings = Sdk.transportSettingsWithMode(
            Sdk.defaultTransportSettings(),
            mode,
        )
        waitFor("transport policy $mode") { device.transportSettings?.mode == mode }
    }

    private fun connectPublic(
        application: MainApplication,
        device: DeviceLocal,
        connectVc: ConnectViewController,
        mode: String,
    ) {
        stopClient(connectVc, device)
        stopProvider(application, device)
        configureClientMode(device, mode)
        connectVc.connectBestAvailable()
        handleVpnConsentIfPresent()
        waitFor("public VPN connection", CONNECT_TIMEOUT_MILLIS) {
            connectVc.connected && device.connectEnabled && device.tunnelStarted
        }
    }

    private fun startProvider(
        application: MainApplication,
        device: DeviceLocal,
        connectVc: ConnectViewController,
    ) {
        stopClient(connectVc, device)
        application.deviceManager.provideNetworkMode = ProvideNetworkMode.WIFI
        application.deviceManager.provideControlMode = ProvideControlMode.NETWORK
        device.providePaused = false
        handleVpnConsentIfPresent()
        waitFor("same-network provider", CONNECT_TIMEOUT_MILLIS) {
            device.provideEnabled &&
                device.provideMode == Sdk.ProvideModeNetwork &&
                device.tunnelStarted
        }
    }

    private fun peerLocation(peerVc: PeerViewController): ConnectLocation? {
        val peers = peerVc.peers ?: return null
        for (i in 0 until peers.len()) {
            val peer = peers.get(i) ?: continue
            if (!peer.provideEnabled || peer.clientId == null) continue
            val locationId = ConnectLocationId()
            locationId.clientId = peer.clientId
            return ConnectLocation().apply {
                connectLocationId = locationId
                name = "same-network-peer"
                networkPeer = true
            }
        }
        return null
    }

    private fun connectPeer(
        application: MainApplication,
        device: DeviceLocal,
        connectVc: ConnectViewController,
        peerVc: PeerViewController,
    ) {
        stopClient(connectVc, device)
        stopProvider(application, device)
        waitFor("connectable same-network peer", PEER_TIMEOUT_MILLIS) {
            peerLocation(peerVc) != null
        }
        connectVc.connect(checkNotNull(peerLocation(peerVc)))
        handleVpnConsentIfPresent()
        waitFor("same-network peer VPN connection", CONNECT_TIMEOUT_MILLIS) {
            connectVc.connected && device.connectEnabled && device.tunnelStarted
        }
    }

    private fun commandResult(
        command: String,
        application: MainApplication,
        device: DeviceLocal,
        connectVc: ConnectViewController,
        peerVc: PeerViewController,
        startElapsedMs: Long,
    ): Boolean {
        val parts = command.trim().split('|', limit = 3)
        require(parts.size >= 2) { "invalid physical command" }
        val id = parts[0]
        val verb = parts[1]
        val argument = parts.getOrElse(2) { "" }
        require(id.matches(Regex("[A-Za-z0-9._-]+"))) { "invalid physical command ID" }
        require(argument.matches(Regex("[A-Za-z0-9._-]*"))) { "invalid physical command argument" }
        phase = when (verb) {
            "phase" -> argument.ifEmpty { "idle" }
            else -> "$verb${if (argument.isEmpty()) "" else "-$argument"}"
        }
        status(id, "running", application, startElapsedMs)
        when (verb) {
            "phase" -> Unit
            "connect" -> connectPublic(application, device, connectVc, argument)
            "disconnect" -> stopClient(connectVc, device)
            "provide" -> startProvider(application, device, connectVc)
            "stop-provide" -> stopProvider(application, device)
            "peer-connect" -> connectPeer(application, device, connectVc, peerVc)
            "free-memory" -> {
                val before = Sdk.getMemoryStats().totalRuntimeByteCount
                Sdk.freeMemory()
                val after = Sdk.getMemoryStats().totalRuntimeByteCount
                status(
                    id,
                    "complete",
                    application,
                    startElapsedMs,
                    JSONObject().put("beforeBytes", before).put("afterBytes", after),
                )
                return false
            }
            "trim-memory" -> {
                val before = Sdk.getMemoryStats().totalRuntimeByteCount
                Sdk.trimMemory()
                val after = Sdk.getMemoryStats().totalRuntimeByteCount
                status(
                    id,
                    "complete",
                    application,
                    startElapsedMs,
                    JSONObject().put("beforeBytes", before).put("afterBytes", after),
                )
                return false
            }
            "heap-profile" -> {
                require(argument.isNotEmpty()) { "heap profile label is required" }
                val profile = File(acceptanceDir, "physical-heap-$argument.pprof")
                val before = Sdk.getMemoryStats().totalRuntimeByteCount
                Sdk.writeHeapProfile(profile.absolutePath)
                val after = Sdk.getMemoryStats().totalRuntimeByteCount
                status(
                    id,
                    "complete",
                    application,
                    startElapsedMs,
                    JSONObject()
                        .put("profileName", profile.name)
                        .put("profileBytes", profile.length())
                        .put("beforeBytes", before)
                        .put("afterBytes", after),
                )
                return false
            }
            "snapshot" -> Unit
            "finish" -> {
                stopClient(connectVc, device)
                stopProvider(application, device)
                status(id, "complete", application, startElapsedMs)
                return true
            }
            else -> throw IllegalArgumentException("unsupported physical command")
        }
        status(id, "complete", application, startElapsedMs)
        return false
    }

    @Test(timeout = 10_800_000)
    fun physicalLowbarSession() {
        val arguments = InstrumentationRegistry.getArguments()
        val expectedBuildId = arguments.getString("acceptanceBuildId").orEmpty()
        assertTrue("acceptanceBuildId argument is required", expectedBuildId.isNotBlank())
        assertEquals(
            "installed app is not the APK built for this physical run",
            expectedBuildId,
            BuildConfig.URNETWORK_ACCEPTANCE_BUILD_ID,
        )
        assertEquals("main", BuildConfig.BRINGYOUR_BUNDLE_ENV_NAME)
        assertEquals("ur.network", BuildConfig.BRINGYOUR_BUNDLE_HOST_NAME)

        acceptanceDir.mkdirs()
        commandFile.delete()
        statusFile.delete()
        samplesFile.delete()
        summaryFile.delete()
        activeClientFile.delete()

        val application = context.applicationContext as MainApplication
        val stopped = AtomicBoolean(false)
        val summary = SampleSummary()
        val startElapsedMs = SystemClock.elapsedRealtime()
        var connectVc: ConnectViewController? = null
        var peerVc: PeerViewController? = null
        var sampler: Thread? = null

        try {
            launchLoggedOutApp(application)
            loginWithPassword(application)
            val device = checkNotNull(application.device)
            connectVc = device.openConnectViewController().also { it.start() }
            peerVc = device.openPeerViewController().also { it.start() }
            sampler = startSampler(application, startElapsedMs, stopped, summary)
            phase = "ready"
            status("0", "ready", application, startElapsedMs)

            var lastCommandId = ""
            val deadline = startElapsedMs + MAX_SESSION_MILLIS
            var finished = false
            while (!finished && SystemClock.elapsedRealtime() < deadline) {
                val text = runCatching { commandFile.readText().trim() }.getOrDefault("")
                if (text.isNotEmpty()) {
                    val id = text.substringBefore('|')
                    if (id != lastCommandId) {
                        lastCommandId = id
                        try {
                            finished = commandResult(
                                text,
                                application,
                                device,
                                checkNotNull(connectVc),
                                checkNotNull(peerVc),
                                startElapsedMs,
                            )
                        } catch (error: Throwable) {
                            status(
                                id,
                                "error",
                                application,
                                startElapsedMs,
                                JSONObject().put("errorType", error.javaClass.simpleName),
                            )
                            throw error
                        }
                    }
                }
                SystemClock.sleep(COMMAND_POLL_MILLIS)
            }
            assertTrue("physical session reached its safety timeout", finished)
        } finally {
            stopped.set(true)
            sampler?.join(5_000)
            val device = application.device
            if (device != null) {
                runCatching { connectVc?.let { stopClient(it, device) } }
                runCatching { stopProvider(application, device) }
                runCatching {
                    peerVc?.stop()
                    peerVc?.let(device::closePeerViewController)
                }
                runCatching {
                    connectVc?.stop()
                    connectVc?.let(device::closeConnectViewController)
                }
            }
            writePrivate(summaryFile, "${summary.json()}\n")
            instrumentation.runOnMainSync { application.logout() }
        }
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 30_000L
        const val AUTH_TIMEOUT_MILLIS = 90_000L
        const val CONNECT_TIMEOUT_MILLIS = 120_000L
        const val PEER_TIMEOUT_MILLIS = 180_000L
        const val COMMAND_POLL_MILLIS = 250L
        const val SAMPLE_INTERVAL_MILLIS = 1_000L
        const val MAX_SESSION_MILLIS = 10_200_000L
        const val GO_RUNTIME_SPIKE_BYTES = 28L * 1024 * 1024
    }
}
