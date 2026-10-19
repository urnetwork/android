@file:Suppress("UNUSED_PARAMETER")

package com.bringyour.network

import java.nio.file.Files

/**
 * Inert JVM substitutes, not Android or gomobile implementations. The runner
 * inserts the unmodified production method bodies below. Storage faults model
 * the SDK's checked-read exception and tolerant-getter empty/nil contracts.
 */
internal class LocalState : AutoCloseable {
    val directory = Files.createTempDirectory("stored-session-boundary-").toFile()
    var available = true
    var failReadAt: Int? = null
    var reads = 0
    var checkedReads = 0
    var legacyReads = 0
    var logoutCalls = 0
    var validAdmin = true
    private var admin = "synthetic-admin-credential"
    private var client = "synthetic-client-credential"
    private var instance: String? = "synthetic-instance"

    init {
        persistAuth()
        directory.resolve("settings.fixture").writeText("synthetic-saved-settings")
        directory.resolve("key.fixture").writeText("synthetic-device-key")
    }

    private fun persistAuth() {
        directory.resolve("auth.fixture").writeText("$admin\n$client\n$instance")
    }

    private fun read() {
        reads += 1
        check(available && failReadAt != reads) { "synthetic unavailable auth storage" }
    }

    var byJwt: String
        get() {
            legacyReads += 1
            return runCatching { read(); admin }.getOrDefault("")
        }
        set(value) { admin = value; persistAuth() }

    var byClientJwt: String
        get() {
            legacyReads += 1
            return runCatching { read(); client }.getOrDefault("")
        }
        set(value) { client = value; persistAuth() }

    val instanceId: String?
        get() = runCatching { read(); instance }.getOrNull()

    val authStateSnapshot: AuthSnapshot
        get() {
            checkedReads += 1
            read()
            return AuthSnapshot(admin, client, validAdmin)
        }

    fun parseByJwt(): Any? = byJwt.takeIf { it.isNotEmpty() && validAdmin }

    var deviceLocalKeyMaterial: String?
        get() = directory.resolve("key.fixture").takeIf { it.isFile }?.readText()
        set(value) {
            val file = directory.resolve("key.fixture")
            if (value == null) file.delete() else file.writeText(value)
        }

    fun logout() {
        logoutCalls += 1
        directory.listFiles()!!.forEach { it.deleteRecursively() }
        admin = ""
        client = ""
        instance = null
    }

    fun savedFiles(): Map<String, String> = directory.listFiles()!!
        .filter { it.isFile }.associate { it.name to it.readText() }

    override fun close() { directory.deleteRecursively() }
}

internal class AuthSnapshot(private val admin: String, val byClientJwt: String, private val valid: Boolean) {
    fun parseByJwt(): Any? = admin.takeIf { it.isNotEmpty() && valid }
}

internal class AsyncLocalState(val localState: LocalState?)
internal class Api(var byJwt: String? = "synthetic-api-credential") {
    fun setClientInfo(value: Any) {}
}
internal class NetworkSpace(val asyncLocalState: AsyncLocalState, val api: Api? = Api())
internal class NetworkSpaceManagerProvider(var current: NetworkSpace?) {
    fun setNetworkSpace(space: NetworkSpace) { current = space }
}
internal class ClientEventQueue {
    fun close() {}
    fun flushAndWait(millis: Long) {}
}
internal object BuildConfig { const val VERSION_NAME = "synthetic-test-build" }
internal object Log { fun w(tag: String, message: String) {} }
internal object Sdk {
    const val EventPlatformAndroid = 0
    fun newClientInfo(type: String, version: String): Any = Any()
    fun newClientEventQueue(space: NetworkSpace, platform: Int, version: String, language: String) = ClientEventQueue()
    fun newLoginViewController(api: Api?): Any = Any()
}
internal class DeviceManager {
    var clearCalls = 0
    var onLogoutStarted: ((Any) -> Unit)? = null
    var onAuthLogout: ((Any) -> Unit)? = null
    fun clearDevice() { clearCalls += 1 }
}
internal class LoginState(val attemptId: Long? = 1)
internal class LoginStateFlow { val value = LoginState() }
internal class LoginStartupTracker {
    var logoutCalls = 0
    var authLogoutCalls = 0
    fun pending(attempt: Long, stage: LoginStartupStage): Boolean = true
    fun loggedOut() { logoutCalls += 1 }
    fun authLoggedOut() { authLogoutCalls += 1 }
}
internal class Notices {
    fun clear() {}
    fun authLoggedOut(cause: Any) {}
}
internal class Commands { fun signOutStarted(device: Any) {} }
internal class StartConnect { fun cancel() {} }
internal class SnapshotWriter { fun clear() {} }
internal enum class SsoProvider { Synthetic }
internal fun ssoOAuthAttempts(app: Any, provider: SsoProvider): Any = Any()
internal fun pendingSolanaPaymentStore(app: Any): Any = Any()
internal object BittensorBridgeReturns { val shared = Any() }
internal fun clearSignedOutNetworkState(ssoAttempts: List<Any>, pendingSolanaPayment: Any, bittensorBridgeReturns: Any) {}
internal class Handler(looper: Any) { fun post(action: () -> Unit) { action() } }
internal class LoginActivity
internal class Intent(context: Any, activity: Class<*>) {
    companion object {
        const val FLAG_ACTIVITY_NEW_TASK = 1
        const val FLAG_ACTIVITY_TASK_ON_HOME = 2
    }
    fun setFlags(flags: Int) {}
}

// @production_types@

/** Runs the actual owning methods; framework setup and DeviceLocal construction are inert. */
internal class MainApplicationFixture(val store: LocalState) {
    val space = NetworkSpace(AsyncLocalState(store))
    val networkSpaceManagerProvider = NetworkSpaceManagerProvider(space)
    val asyncLocalState get() = networkSpaceManagerProvider.current?.asyncLocalState
    val api get() = networkSpaceManagerProvider.current?.api
    val deviceManager = DeviceManager()
    var clientEventQueue: ClientEventQueue? = null
    var loginVc: Any? = null
    val loginStartupState = LoginStateFlow()
    val loginStartupTracker = LoginStartupTracker()
    val signInNotices = Notices()
    val connectCommands = Commands()
    val startConnect = StartConnect()
    val widgetSnapshotWriter: SnapshotWriter? = null
    val device: Any? = Any()
    val mainLooper = Any()
    val applicationContext = Any()
    var uiIsPro = true
    var uiPollingSubscriptionBalance = true
    var initializationCalls = 0
    var receivedClient: String? = null
    var initialize: (String) -> DeviceInitResult = { DeviceInitResult.Ready }
    val TAG = "synthetic-startup"
    val CLIENT_INFO_DEVICE_TYPE = "android"
    val CLIENT_EVENT_LOGOUT_DRAIN_MILLIS = 1500L

    fun restore() { updateActiveNetworkSpace(space) }
    fun startClient(jwt: String): ClientSessionStartResult = startClientSession(jwt)
    fun staleCleanup() { logoutStaleLocalState(store) }
    fun stop() { deviceManager.clearDevice() }
    fun clearBalanceRecovery() {}
    fun startActivity(intent: Intent) {}

    private fun initDevice(jwt: String): DeviceInitResult {
        installAuthLogoutListeners()
        initializationCalls += 1
        receivedClient = jwt
        return initialize(jwt)
    }

    private fun installAuthLogoutListeners() {
// @production_auth_logout_listeners@
    }

// @production_methods@
}
