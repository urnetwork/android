package com.bringyour.network.ui.account

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.Api
import com.bringyour.sdk.ClientSessionAction
import com.bringyour.sdk.ClientSessionError
import com.bringyour.sdk.ClientSessionListener
import com.bringyour.sdk.ClientSessionSnapshot
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.NetworkSessionInfo
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SessionsViewModel"

/**
 * Account -> Sessions (server/session/REVOKE-UI-FINAL.md): the account's
 * sign-ins from the sdk's ClientSessionViewController, opened on the device's
 * account api, with sign out of one session or of all the others.
 *
 * Binds [SessionsScreenModel] to the app: the device (a replaced device closes
 * the former one's controller), the process lifecycle (the background closes
 * the controller), and the screen's visibility (LifecycleStartEffect in
 * SessionsScreen). The controller's changes arrive on sdk threads and are
 * posted to the main thread; teardown removes the listener, then closes the
 * controller, and nothing updates after.
 */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    deviceManager: DeviceManager,
) : ViewModel(), DefaultLifecycleObserver {

    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val model = SessionsScreenModel<DeviceLocal>(
        openController = { device, onChange -> SdkSessionsController(device.api, onChange) },
        post = { block -> viewModelScope.launch { block() } },
        clock = { System.currentTimeMillis() },
    )
    private var removeDeviceChangeListener: (() -> Unit)? = null

    val ui: SessionsUi get() = model.ui
    val confirmation: SessionsConfirmation? get() = model.confirmation

    init {
        processLifecycle.addObserver(this)
        model.setForeground(processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                model.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        model.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        model.setForeground(false)
    }

    fun setVisible(visible: Boolean) = model.setVisible(visible)

    fun refresh() = model.refresh()

    fun requestSignOut(sessionId: String) = model.requestSignOut(sessionId)

    fun requestSignOutOthers() = model.requestSignOutOthers()

    fun dismissConfirmation() = model.dismissConfirmation()

    fun confirm() = model.confirm()

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        model.close()
        super.onCleared()
    }
}

/**
 * SessionsController over the sdk's controller, built on the account api
 * (Api.openClientSessionViewController; gomobile binds no constructor that
 * takes a context). The listener fires on an sdk thread.
 */
private class SdkSessionsController(
    api: Api,
    onChange: () -> Unit,
) : SessionsController {
    private val controller = api.openClientSessionViewController()
    private val listenerSub = controller.addClientSessionListener(
        ClientSessionListener { onChange() }
    )

    override fun snapshot(): SessionsSnapshot = sessionsSnapshotFromSdk(controller.snapshot)

    override fun start() = controller.start()

    override fun setVisible(visible: Boolean) = controller.setVisible(visible)

    override fun refresh() = controller.refresh()

    override fun revokeSession(sessionId: String) {
        val id = try {
            Sdk.parseId(sessionId)
        } catch (e: Exception) {
            Log.w(TAG, "not a session id", e)
            return
        }
        controller.revokeSession(id)
    }

    override fun revokeOtherSessions() = controller.revokeOtherSessions()

    override fun close() {
        listenerSub.close()
        controller.close()
    }
}

/** Copies the gomobile snapshot into the screen's data. */
private fun sessionsSnapshotFromSdk(snapshot: ClientSessionSnapshot?): SessionsSnapshot {
    snapshot ?: return SessionsSnapshot.Initial
    val error = { value: ClientSessionError? ->
        value?.let {
            SessionErrorFlags(
                retryable = it.retryable,
                signInRequired = it.signInRequired,
                // the sdk sets it only for its trusted cause
                sessionRevoked = it.sessionRevoked,
                unsupported = it.unsupported,
            )
        }
    }
    val action = { value: ClientSessionAction ->
        SessionActionState(
            sessionId = value.sessionId?.idStr,
            loading = value.loading,
            pending = value.pending,
            error = error(value.error),
        )
    }
    val session = { value: NetworkSessionInfo ->
        SessionEntry(
            sessionId = value.sessionId?.idStr.orEmpty(),
            current = value.current,
            kind = value.kind.orEmpty(),
            createTimeMillis = value.createTime?.unixMilli(),
            lastUse = value.lastUsed?.let {
                SessionLastUse(
                    unixTimeSeconds = it.unixTime,
                    city = it.city.orEmpty(),
                    region = it.region.orEmpty(),
                    country = it.country.orEmpty(),
                    countryCode = it.countryCode.orEmpty(),
                    deviceType = it.deviceType.orEmpty(),
                    appVersion = it.appVersion.orEmpty(),
                )
            },
        )
    }
    val sessions = snapshot.sessions?.let { list ->
        (0 until list.len()).mapNotNull { list.get(it)?.let(session) }
    } ?: listOf()
    val actions = snapshot.actions?.let { list ->
        (0 until list.len()).mapNotNull { list.get(it)?.let(action) }
    } ?: listOf()
    return SessionsSnapshot(
        sessions = sessions,
        currentSessionId = snapshot.currentSessionId?.idStr,
        legacyCoverage = snapshot.legacyCoverage.orEmpty(),
        loaded = snapshot.loaded,
        loading = snapshot.loading,
        refreshing = snapshot.refreshing,
        supported = snapshot.supported,
        bulkAction = snapshot.bulkAction?.let(action),
        actions = actions,
        error = error(snapshot.error),
    )
}
