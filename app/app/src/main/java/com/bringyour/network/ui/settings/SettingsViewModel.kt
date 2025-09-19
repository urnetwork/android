package com.bringyour.network.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.AccountPreferencesViewController
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.AuthCodeCreateArgs
import com.bringyour.sdk.ReferralNetwork
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.StripeCreateCustomerPortalArgs
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

    private val _provideEnabled = MutableStateFlow(false)
    val provideEnabled: StateFlow<Boolean> = _provideEnabled

    private val _providePaused = MutableStateFlow(false)
    val providePaused: StateFlow<Boolean> = _providePaused

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

    var provideControlMode by mutableStateOf(ProvideControlMode.AUTO)
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

    /**
     * Allow providing on cell networks
     */
    private val _allowProvideOnCell = MutableStateFlow(false)
    val allowProvideOnCell: StateFlow<Boolean> = _allowProvideOnCell

    val toggleAllowProvideOnCell: () -> Unit = {
        val currentValue = _allowProvideOnCell.value

        val newValue = if (currentValue) {
            ProvideNetworkMode.WIFI
        } else {
            ProvideNetworkMode.ALL
        }

        deviceManager.provideNetworkMode = newValue
        _allowProvideOnCell.value = !currentValue
    }

    private val _isCreatingAuthCode = MutableStateFlow(false)
    val isCreatingAuthCode: StateFlow<Boolean> = _isCreatingAuthCode

    private val _authCode = MutableStateFlow<String?>(null)
    val authCode: StateFlow<String?> = _authCode

    private val _isFetchingStripePortal = MutableStateFlow(false)
    val isFetchingStripePortal: StateFlow<Boolean> = _isFetchingStripePortal

    private val _stripePortalUrl = MutableStateFlow<String?>(null)
    val stripePortalUrl: StateFlow<String?> = _stripePortalUrl


    private val _isPresentingAuthCodeDialog = MutableStateFlow(false)
    val isPresentingAuthCodeDialog: StateFlow<Boolean> = _isPresentingAuthCodeDialog

    val setIsPresentingAuthCodeDialog: (Boolean) -> Unit = {
        _isPresentingAuthCodeDialog.value = it
    }

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

    val setProvideControlMode: (ProvideControlMode) -> Unit = { mode ->
        deviceManager.provideControlMode = mode
        this.provideControlMode = mode
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

    val authCodeCreate: () -> Unit = {

        if (!_isCreatingAuthCode.value) {

            _authCode.value = null
            _isCreatingAuthCode.value = true

            val args = AuthCodeCreateArgs()
            args.durationMinutes = 5.0
            args.uses = 1
            deviceManager.device?.api?.authCodeCreate(args) { result, error ->

                if (error != null) {
                    Log.i(TAG, "error creating auth code: ${error.message}")
                    return@authCodeCreate
                }

                if (result.error != null) {
                    Log.i(TAG, "result error creating auth code: ${result.error.message}")
                    return@authCodeCreate
                }

                viewModelScope.launch {
                    _authCode.value = result.authCode
                    _isCreatingAuthCode.value = false
                }

            }

        }

    }

    val fetchStripeCustomerPortalUrl: () -> Unit = {

        if (!_isFetchingStripePortal.value) {

            _isFetchingStripePortal.value = true


            val args = StripeCreateCustomerPortalArgs()
            deviceManager.device?.api?.stripeCreateCustomerPortal(args) { result, err ->

                viewModelScope.launch {

                    if (err != null) {
                        Log.i(TAG, "fetchStripeCustomerPortalUrl err is: ${err.toString()}")
                        _isFetchingStripePortal.value = false
                        return@launch
                    }

                    if (result.error != null) {
                        Log.i(TAG, "fetchStripeCustomerPortalUrl result err is: ${result.error.message}")
                        _isFetchingStripePortal.value = false
                        return@launch
                    }
                    
                    _stripePortalUrl.value = result.url
                    _isFetchingStripePortal.value = false

                }

            }

        }

    }

    val addProvideEnabledListener: () -> Unit = {
        deviceManager.device?.let { device ->
            device.addProvideChangeListener {
                viewModelScope.launch {
                    _provideEnabled.value = device.provideEnabled
                }
            }
        }
    }

    val addProvidePausedListener: () -> Unit = {
        deviceManager.device?.let { device ->
            device.addProvidePausedChangeListener {
                viewModelScope.launch {
                    _providePaused.value = device.providePaused
                }
            }
        }
    }

    val provideIndicatorColor: Color
        get() = when {
            !provideEnabled.value -> Red
            providePaused.value -> Yellow
            else -> Green
        }

    init {
        accountPreferencesVc = deviceManager.device?.openAccountPreferencesViewController()
        
        provideControlMode = deviceManager.provideControlMode

        allowForeground = deviceManager.allowForeground

        val routeLocal = deviceManager.device?.routeLocal

        _routeLocal.value = routeLocal == true

        _allowProvideOnCell.value = deviceManager.provideNetworkMode == ProvideNetworkMode.ALL

        addAllowProductUpdatesListener()

        accountPreferencesVc?.start()

        fetchReferralNetwork()

        fetchStripeCustomerPortalUrl()

        version = Sdk.Version

        addProvideEnabledListener()

        addProvidePausedListener()

        deviceManager.device?.let { device ->
            _providePaused.value = device.providePaused
            _provideEnabled.value = device.provideEnabled
        }

    }

}
