package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.NetworkUser
import com.bringyour.sdk.NetworkUserViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.components.LoginMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AccountViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel(), DefaultLifecycleObserver {

    private var networkUserVc: NetworkUserViewController? = null
    private val subs = mutableListOf<com.bringyour.sdk.Sub>()
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, NetworkUserViewController>(
            open = { openNetworkUserViewController(it) },
            close = { device, vc -> closeNetworkUserViewController(device, vc) },
        )

    var loginMode by mutableStateOf<LoginMode>(LoginMode.Guest)
        private set

    val setLoginMode: (LoginMode) -> Unit = { mode ->
        Log.i("AccountViewModel", "setting login mode to $mode")
        loginMode = mode
    }

    private val _networkUser = MutableStateFlow<NetworkUser?>(null)
    val networkUser: StateFlow<NetworkUser?> = _networkUser.asStateFlow()

    private val setNetworkUser: (NetworkUser?) -> Unit = { nu ->
        _networkUser.value = nu
    }

    private val addNetworkUserListener = {
        networkUserVc?.addNetworkUserListener {
            viewModelScope.launch {
                setNetworkUser(networkUserVc?.networkUser)
            }
        }?.let { subs.add(it) }
    }

    var clientId by mutableStateOf("")
        private set

    val upgradePlan = {}

    val getCurrentPlan = {}

    val refreshNetworkUser: () -> Unit = {
        networkUserVc?.fetchNetworkUser()
    }

    init {

        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, success ->
            viewModelScope.launch {
                // a parsed jwt is a real account: guest mode is gone, and every
                // account now has a network name (auto-generated for seedphrase
                // accounts until claimed). networkName is a gomobile-bound Go
                // string, so it is never null and can't be tested for one.
                setLoginMode(if (success) LoginMode.Authenticated else LoginMode.Guest)
            }
        }

        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                _networkUser.value = null
                clientId = device?.clientId?.toString() ?: ""
                controllerOwner.setDevice(device)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun openNetworkUserViewController(device: DeviceLocal): NetworkUserViewController {
        val vc = device.openNetworkUserViewController()
        networkUserVc = vc
        addNetworkUserListener()
        vc.start()
        return vc
    }

    private fun closeNetworkUserViewController(
        device: DeviceLocal,
        vc: NetworkUserViewController,
    ) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (networkUserVc === vc) {
            networkUserVc = null
        }
    }

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        processLifecycle.removeObserver(this)
        controllerOwner.close()
        super.onCleared()
    }

}
