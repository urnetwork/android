@file:Suppress("UNUSED_PARAMETER")

package com.bringyour.network

import java.nio.file.Files
import android.os.Handler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

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
    var clientWriteFailure = false
    var adminWriteFailure = false
    var validAdmin = true
    var introWriteFailure = false
    var canPromptIntroFunnel = false
        set(value) { check(!introWriteFailure); field = value }
    var keyReadFailure = false
    var keyWrites = 0
    var keyClears = 0
    var keyCheckedReads = 0
    var routeLocal = true
    var connectLocation: String? = "synthetic-route"
    var defaultLocation: String? = "synthetic-default-route"
    var canShowRatingDialog = true
    var provideControlMode = "never"
    var provideNetworkMode = "wifi"
    var provideMode = 0
    var vpnInterfaceWhileOffline = false
    var canRefer = true
    var allowForeground = true
    var performanceProfile = "synthetic-profile"
    var provideSecretKeys: String? = null
    var blockerEnabled = false
    var blockActionOverrides: String? = null
    var dnsResolverSettings: String? = null

    fun readDeviceLocalKeyMaterial(): KeyReadResult {
        keyCheckedReads += 1
        check(!keyReadFailure) { "synthetic checked key load failure" }
        return KeyReadResult(deviceLocalKeyMaterial)
    }
    private var admin = "synthetic-admin-credential"
    private var client = "synthetic-client-credential"
    private var instance: String? = "synthetic-instance"
    private var instanceGeneration = 0

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
        set(value) {
            check(!adminWriteFailure) { "synthetic admin persistence failure" }
            // Match SetByJwt's paired-state reset; equal bytes still represent
            // a distinct explicit login at the application ownership boundary.
            if (admin != value) { client = ""; instance = null }
            admin = value
            persistAuth()
        }

    var byClientJwt: String
        get() {
            legacyReads += 1
            return runCatching { read(); client }.getOrDefault("")
        }
        set(value) {
            check(!clientWriteFailure) { "synthetic client persistence failure" }
            // SetByClientJwt gives a changed client its own stable instance.
            if (client != value || (value.isNotEmpty() && instance == null)) {
                instance = if (value.isEmpty()) null else "synthetic-instance-${++instanceGeneration}"
            }
            client = value
            persistAuth()
        }

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
        get() = if (keyReadFailure) null else directory.resolve("key.fixture").takeIf { it.isFile }?.readText()
        set(value) {
            keyWrites += 1
            if (value == null) keyClears += 1
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

internal class KeyReadResult(val keyMaterial: String?)

internal class AuthSnapshot(private val admin: String, val byClientJwt: String, private val valid: Boolean) {
    fun parseByJwt(): Any? = admin.takeIf { it.isNotEmpty() && valid }
}

internal class AsyncLocalState(val localState: LocalState?)
internal class Api(initialByJwt: String? = "synthetic-api-credential") {
    var authWriteFailure = false
    var byJwt: String? = initialByJwt
        set(value) { check(!authWriteFailure); field = value }
    fun setClientInfo(value: Any) {}
    var createReply: ((NetworkCreateResult?, Throwable?) -> Unit)? = null
    var creates = 0
    fun networkCreate(args: NetworkCreateArgs, callback: (NetworkCreateResult?, Throwable?) -> Unit) { creates += 1; createReply = callback }
}
internal class NetworkSpace(val asyncLocalState: AsyncLocalState, val api: Api? = Api(), val nativeIdentity: Any = Any(), val key: String = "same-key") {
    fun same(other: NetworkSpace?): Boolean = other != null && nativeIdentity === other.nativeIdentity
    fun wrapper() = NetworkSpace(asyncLocalState, api, nativeIdentity, key)
}
internal class NetworkSpaceManager(var activeNetworkSpace: NetworkSpace?)
// @production_login_request@

internal class NetworkSpaceManagerProvider(var current: NetworkSpace?) {
    val manager = NetworkSpaceManager(current)
    fun getNetworkSpaceManager(): NetworkSpaceManager = manager
    fun setNetworkSpace(space: NetworkSpace) { current = space }
    fun getNetworkSpace(): NetworkSpace? = current
}
internal class ClientEventQueue {
    fun close() {}
    fun flushAndWait(millis: Long) {}
}
internal object BuildConfig { const val VERSION_NAME = "synthetic-test-build" }
internal object Log { fun w(tag: String, message: String) {}
fun e(tag: String, message: String, error: Throwable) {} }
internal object Sdk {
    const val EventPlatformAndroid = 0
    const val ProvideModePublic = 1
    const val ProvideModeNetwork = 2
    fun newDeviceLocalWithMemoryTarget(space: NetworkSpace, jwt: String, description: String,
        spec: String, version: String, instance: String, ignored: Boolean, key: String?, memory: Long): DeviceLocal? =
        factories.getValue(space)(key)
    val factories = mutableMapOf<NetworkSpace, (String?) -> DeviceLocal?>()
    fun newClientInfo(type: String, version: String): Any = Any()
    fun newClientEventQueue(space: NetworkSpace, platform: Int, version: String, language: String) = ClientEventQueue()
    fun newLoginViewController(api: Api?): Sub = Sub()
}
internal enum class ProvideControlMode { ALWAYS, NETWORK, NEVER;
    companion object {
        fun fromString(value: String): ProvideControlMode? = null
        fun toString(value: ProvideControlMode): String = "never"
    }
}
internal enum class ProvideNetworkMode { WIFI;
    companion object {
        fun fromString(value: String): ProvideNetworkMode? = null
        fun toString(value: ProvideNetworkMode): String = "wifi"
    }
}
internal class Sub(val closeAction: () -> Unit = {}) { fun close() = closeAction() }
internal class DeviceLocal(val networkSpace: NetworkSpace, val keyMaterial: String) {
    var authLogoutCause: String? = "synthetic-revocation"
    val authListeners = mutableListOf<() -> Unit>()
    var closes = 0
    var providePaused = false
    var routeLocal = true
    var provideMode = 0
    var connectLocation: String? = null
    var defaultLocation: String? = null
    var canShowRatingDialog = true
    var provideControlMode = "never"
    var vpnInterfaceWhileOffline = false
    var canRefer = true
    var allowForeground = true
    var provideNetworkMode = "wifi"
    var canPromptIntroFunnel = false
    var performanceProfile = "synthetic-profile"
    var blockerEnabled = false
    var blockActionOverrides: String? = null
    var dnsResolverSettings: String? = null
    fun addAuthLogoutListener(listener: () -> Unit): Sub {
        authListeners += listener
        // Keep the callable so tests can force an already-admitted callback after close.
        return Sub()
    }
    fun addProvideSecretKeysListener(listener: (String?) -> Unit): Sub = Sub()
    fun addJwtRefreshListener(listener: (String?) -> Unit): Sub = Sub()
    fun setAutoSave(value: Boolean) {}
    fun loadProvideSecretKeys(value: String) {}
    fun initProvideSecretKeys() {}
    fun close() { closes += 1 }
}
internal class JwtManager { fun updateJwt(jwt: Any) {}; fun clearJwt() {} }
internal class DeviceChanges { fun prepareUpdate(device: DeviceLocal): () -> Unit = {} }
internal class DeviceManager {
    var clearCalls = 0
    var onLogoutStarted: ((DeviceLocal) -> Unit)? = null
// @production_callback_declaration@
    var device: DeviceLocal? = null
    val deviceLock = Any()
    val jwtManager = JwtManager()
    val deviceChanges = DeviceChanges()
    var provideSecretKeysSub: Sub? = null
    var jwtRefreshSub: Sub? = null
    var authLogoutSub: Sub? = null
    var clearHook: () -> Unit = {}
    val TAG = "synthetic-manager"
    val DEVICE_MEMORY_TARGET_BYTE_COUNT = 64L * 1024 * 1024
    fun clearDevice() { clearCalls += 1; device?.close(); device = null; clearHook() }
    fun closeDeviceSubscriptionsLocked() { authLogoutSub?.close() }
    fun persistDeviceLocalKeyMaterial(state: LocalState, device: DeviceLocal) {
        state.deviceLocalKeyMaterial = device.keyMaterial
    }
    fun addLocalStateChangeSubscriptionsLocked(state: LocalState, device: DeviceLocal) {}
    fun getAppVersion(): String = "synthetic-build"
    fun createDeviceLocalWithDefaults(networkSpace: NetworkSpace, byClientJwt: String,
        deviceDescription: String, deviceSpec: String, instanceId: String): DeviceLocal? =
        Sdk.factories.getValue(networkSpace)(null)
// @production_device_init@
}
internal class Notices {
    var cause: String? = null
    var deliveries = 0
    fun clear() { cause = null }
    fun authLoggedOut(value: String) { cause = value; deliveries += 1 }
}
internal class StartConnect { fun cancel() {} }
internal class SnapshotWriter { fun clear() {} }
internal enum class SsoProvider { Synthetic }
internal fun ssoOAuthAttempts(app: Any, provider: SsoProvider): Any = Any()
internal fun pendingSolanaPaymentStore(app: Any): Any = Any()
internal object BittensorBridgeReturns { val shared = Any() }
internal fun clearSignedOutNetworkState(ssoAttempts: List<Any>, pendingSolanaPayment: Any, bittensorBridgeReturns: Any) {}
internal class LoginActivity(var app: MainApplicationFixture? = null) {
    val lifecycleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var finishes = 0
    private fun finishAuthenticatedLogin() { finishes += 1 }
// @production_activity_methods@
}
internal class NetworkCreateArgs {
    var networkName = ""
    var terms = false
    var productUpdatesOptOut = false
    var referralCode = ""
}
internal class NetworkCreateResult(val seedphrase: String?, val network: CreatedNetwork?, val error: CreateError? = null)
internal class CreatedNetwork(val byJwt: String)
internal class CreateError(val message: String?)
internal class Referral { val createCode: String? = null }
internal object CantReachPrompt { val shared = this; fun report(error: Throwable) {} }
internal class InstantFixture(val networkSpaceManagerProvider: NetworkSpaceManagerProvider) {
    val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val _inProgress = MutableStateFlow(false)
    val _error = MutableStateFlow<String?>(null)
    val _seedphrase = MutableStateFlow<String?>(null)
    var loginRequest: LoginRequest? = null
    val referralInput = Referral()
// @production_instant_create@
}
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
    var loginVc: Sub? = null
    val loginStartupTracker = LoginStartupTracker()
    val loginStartupState get() = loginStartupTracker.state
    val signInNotices = Notices()
    val commands = ArrayDeque<Runnable>()
    val connectCommands = ConnectCommands(execute = { commands.addLast(it) }, postMain = { Handler.queue.addLast { it.run() } })
    val startConnect = StartConnect()
    val widgetSnapshotWriter: SnapshotWriter? = null
    val device get() = deviceManager.device
    private var authSessionOwner = Any()
    private var activeLoginRequest: LoginRequest? = null
    val networkSpaceManager get() = networkSpaceManagerProvider.manager
    val mainLooper = android.os.Looper.getMainLooper()
    val mainHandler = Handler(mainLooper)
    val applicationContext = Any()
    var uiIsPro = true
    var uiPollingSubscriptionBalance = true
    val TAG = "synthetic-startup"
    val CLIENT_INFO_DEVICE_TYPE = "android"
    val CLIENT_EVENT_LOGOUT_DRAIN_MILLIS = 1500L

    val deviceDescription = "synthetic-description"
    val deviceSpec = "synthetic-spec"
    var navigations = 0
    var passwordReply: ((PasswordAuthWireResult?, String?) -> Unit)? = null
    var clientRequests = 0
    var clientReply: ((AuthClientWireResult?, String?) -> Unit)? = null
    val loginClientCoordinator = LoginClientCoordinator(
        tracker = loginStartupTracker,
        requester = AuthClientRequester { clientRequests += 1; clientReply = it },
        starter = ClientSessionStarter(::startClientSession),
        dispatch = ::dispatchLoginAction,
    )
    val passwordLoginCoordinator = PasswordLoginCoordinator(
        tracker = loginStartupTracker,
        requester = PasswordAuthRequester { _, _, callback ->
            captureLoginRequest(loginStartupState.value.attemptId)
            passwordReply = callback
        },
        sessionAuthenticator = NetworkSessionAuthenticator { attempt, jwt, newNetwork, completion ->
            val request = activeLoginRequest?.takeIf { it.attemptId == attempt }
            if (request == null) completion(LoginClientCompletion.Failed(LoginStartupFailure.STALE_ATTEMPT))
            else authenticateNetworkSession(request, jwt, newNetwork, completion)
        },
        dispatch = ::dispatchLoginAction,
    )
    var vpnServiceUpdateGeneration = 0L
    var vpnServiceUpdatePosted = false
    var deviceProvideSub: Sub? = null
    var deviceProvidePausedSub: Sub? = null
    var deviceProvideNetworkSub: Sub? = null
    var deviceConnectSub: Sub? = null
    var deviceRouteLocalSub: Sub? = null
    var tunnelChangeSub: Sub? = null
    var contractStatusChangeSub: Sub? = null
    var lastLoggedContractStatus: String? = null
    fun resetTunnelRecoveryState() {}
    var stopHook: () -> Unit = {}
    fun stopVpnService() { stopHook() }
    fun removeNetworkCallback() {}
    fun removeOfflineCallback() {}
    fun removeDefaultNetworkCallback() {}
    fun removePowerSaveReceiver() {}
    fun removeNetworkPolicyReceiver() {}
    fun removeThermalStatusListener() {}
    init {
        Handler.queue.clear()
        android.os.Looper.isMain = true
        Sdk.factories[space] = { key -> DeviceLocal(space, key ?: "synthetic-new-key") }
    }
    fun restore() { updateActiveNetworkSpace(space) }
    fun replaceSpace(space: NetworkSpace) { networkSpaceManager.activeNetworkSpace = space; updateActiveNetworkSpace(space) }
    fun startClient(jwt: String): ClientSessionStartResult {
        if (activeLoginRequest?.attemptId != loginStartupState.value.attemptId) captureLoginRequest(loginStartupState.value.attemptId)
        return startClientSession(jwt)
    }
    fun selectedCallback(space: NetworkSpace) { Handler.queue.addLast { applySelectedNetworkSpace(space) } }
    fun selectedNow(space: NetworkSpace): Boolean = applySelectedNetworkSpace(space)
    fun clearBalanceRecovery() {}
    fun startActivity(intent: Intent) { navigations += 1 }
    fun beginPassword(completion: (PasswordLoginCompletion) -> Unit = {}): Long =
        authenticateWithPassword("synthetic-user@example.invalid", "synthetic-password") { _, result -> completion(result) }!!

    private fun initDevice(byClientJwt: String): DeviceInitResult {
// @production_application_init_prefix@
        return deviceInitResult
    }

// @production_methods@
}
