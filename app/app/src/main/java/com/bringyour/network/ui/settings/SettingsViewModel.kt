package com.bringyour.network.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.AccountPreferencesViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.TAG
import com.bringyour.sdk.ReferralNetwork
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    networkSpaceManagerProvider: NetworkSpaceManagerProvider,
    @ApplicationContext private val context: Context
): ViewModel() {

    private var accountPreferencesVc: AccountPreferencesViewController? = null

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _requestPermission = MutableStateFlow(false)
    val requestPermission: StateFlow<Boolean> = _requestPermission

    private val _referralNetwork = MutableStateFlow<ReferralNetwork?>(null)
    val referralNetwork: StateFlow<ReferralNetwork?> = _referralNetwork

    private val _showDeleteAccountDialog = MutableStateFlow(false)
    val showDeleteAccountDialog: StateFlow<Boolean> = _showDeleteAccountDialog

    val setShowDeleteAccountDialog: (Boolean) -> Unit = { show ->
        _showDeleteAccountDialog.value = show
    }

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount

    private val _routeLocal = MutableStateFlow(false)
    val routeLocal: StateFlow<Boolean> = _routeLocal

    var notificationsPermanentlyDenied by mutableStateOf(false)

    val setNotificationsPermanentlyDenied: (Boolean) -> Unit = { pd ->
        notificationsPermanentlyDenied = pd
    }

    var allowProductUpdates by mutableStateOf(false)
        private set

    var provideWhileDisconnected by mutableStateOf(false)
        private set

    var allowForeground by mutableStateOf(false)
        private set

    val setAllowProductUpdates: (Boolean) -> Unit = { allow ->
        allowProductUpdates = allow
    }

    val urIdUrl: (String) -> String? = { clientId ->
        networkSpaceManagerProvider.getNetworkSpace()?.connectLinkUrl(clientId)
    }

    var version by mutableStateOf("")
        private set

    fun onPermissionResult(isGranted: Boolean) {
        _permissionGranted.value = isGranted

        if (!isGranted) {
            setNotificationsPermanentlyDenied(true)
        }
    }

    val triggerPermissionRequest: () -> Unit = {
        _requestPermission.value = true
    }

    val resetPermissionRequest: () -> Unit = {
        _requestPermission.value = false
    }

    fun checkPermissionStatus(activity: ComponentActivity) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        _permissionGranted.value = isGranted

        if (!isGranted) {
            val shouldShowRationale = activity.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
            setNotificationsPermanentlyDenied(!shouldShowRationale)

        }
    }

    val addAllowProductUpdatesListener = {
        accountPreferencesVc?.let { vc ->
            vc.addAllowProductUpdatesListener {
                viewModelScope.launch {
                    setAllowProductUpdates(vc.allowProductUpdates)
                }
            }
        }
    }

    val toggleAllowProductUpdates: () -> Unit = {
        val currentAllowProductUpdates = allowProductUpdates
        accountPreferencesVc?.updateAllowProductUpdates(!currentAllowProductUpdates)
    }

    val toggleProvideWhileDisconnected: () -> Unit = {
        val currentProvideWhileDisconnected = provideWhileDisconnected
        deviceManager.provideWhileDisconnected = !currentProvideWhileDisconnected
        provideWhileDisconnected = !currentProvideWhileDisconnected
    }

    val toggleAllowForeground: () -> Unit = {
        val currentAllowForeground = allowForeground
        deviceManager.allowForeground = !currentAllowForeground
        allowForeground = !currentAllowForeground
    }

    val deleteAccount: (
            onSuccess: () -> Unit,
            onFailure: (Exception?) -> Unit
            ) -> Unit = { onSuccess, onFailure ->

                _isDeletingAccount.value = true

        deviceManager.device?.api?.networkDelete { _, exception ->

            viewModelScope.launch {

                if (exception != null) {
                    onFailure(exception)
                } else {
                    onSuccess()
                }
                _isDeletingAccount.value = false

            }
        }
    }

    val toggleRouteLocal: () -> Unit = {
        val currentRouteLocal = routeLocal.value
        deviceManager.device?.routeLocal = !currentRouteLocal
        _routeLocal.value = !currentRouteLocal
    }

    val fetchReferralNetwork: () -> Unit = {

        deviceManager.device?.api?.getReferralNetwork { result, error ->

            if (error != null) {
                Log.i(TAG, "Error fetching referral network: ${error.message}")
                return@getReferralNetwork
            }

            if (result.error != null) {
                Log.i(TAG, "Result error fetching referral network: ${result.error.message}")
                viewModelScope.launch {
                    _referralNetwork.value = result.network
                }
                return@getReferralNetwork
            }

            viewModelScope.launch {
                _referralNetwork.value = result.network
            }

        }

    }

    init {
        accountPreferencesVc = deviceManager.device?.openAccountPreferencesViewController()

        provideWhileDisconnected = deviceManager.device?.provideWhileDisconnected ?: false

        allowForeground = deviceManager.allowForeground

        val routeLocal = deviceManager.device?.routeLocal

        _routeLocal.value = routeLocal == true

        addAllowProductUpdatesListener()

        accountPreferencesVc?.start()

        fetchReferralNetwork()

        version = Sdk.getVersion()

    }

}
