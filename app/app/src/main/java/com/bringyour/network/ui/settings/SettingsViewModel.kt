package com.bringyour.network.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.ForegroundDeviceControllerOwner
import com.bringyour.network.TAG
import com.bringyour.network.ui.shared.models.ProvideControlMode
import com.bringyour.network.ui.shared.models.ProvideNetworkMode
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.AccountPreferencesViewController
import com.bringyour.sdk.AddAuthArgs
import com.bringyour.sdk.AuthCodeCreateArgs
import com.bringyour.sdk.DeviceLocal
import com.bringyour.sdk.GenerateSeedphraseArgs
import com.bringyour.sdk.ReferralNetwork
import com.bringyour.sdk.RegenerateSeedphraseArgs
import com.bringyour.sdk.RemoveAuthArgs
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.StripeCreateCustomerPortalArgs
import com.bringyour.sdk.Sub
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    @ApplicationContext private val context: Context
): ViewModel(), DefaultLifecycleObserver {

    private var accountPreferencesVc: AccountPreferencesViewController? = null
    private val subs = mutableListOf<Sub>()
    private var removeDeviceChangeListener: (() -> Unit)? = null
    private val processLifecycle = ProcessLifecycleOwner.get().lifecycle
    private val controllerOwner =
        ForegroundDeviceControllerOwner<DeviceLocal, AccountPreferencesViewController>(
            open = { openAccountPreferencesViewController(it) },
            close = { device, vc -> closeAccountPreferencesViewController(device, vc) },
        )

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _requestPermission = MutableStateFlow(false)
    val requestPermission: StateFlow<Boolean> = _requestPermission

    // the LIVE effective provide mode — drives the provide indicator.
    // ProvideMode is a bit set: compare per-case, never with ranges.
    private val _provideMode = MutableStateFlow(Sdk.ProvideModeNone)
    val provideMode: StateFlow<Long> = _provideMode

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

    private var notificationPermissionRequested = false

    val setNotificationsPermanentlyDenied: (Boolean) -> Unit = { pd ->
        notificationsPermanentlyDenied = pd
    }

    var allowProductUpdates by mutableStateOf(false)
        private set

    var provideControlMode by mutableStateOf(ProvideControlMode.NEVER)
        private set

    val setAllowProductUpdates: (Boolean) -> Unit = { allow ->
        allowProductUpdates = allow
    }

    var version by mutableStateOf("")
        private set

    /**
     * Device name and spec, from this device's network client record
     */
    private var deviceId: com.bringyour.sdk.Id? = null

    var deviceName by mutableStateOf("")
        private set

    var deviceSpec by mutableStateOf("")
        private set

    var isUpdatingDeviceName by mutableStateOf(false)
        private set

    fun fetchDeviceInfo() {
        val device = deviceManager.device ?: return
        val clientId = device.clientId?.idStr ?: return
        device.api?.getNetworkClients { result, _ ->
            val clients = result?.clients ?: return@getNetworkClients
            val n = clients.len()
            for (i in 0 until n) {
                val info = clients.get(i) ?: continue
                if (info.clientId?.idStr == clientId) {
                    viewModelScope.launch {
                        deviceId = info.deviceId
                        deviceName = info.deviceName.ifEmpty { info.deviceDescription }
                        deviceSpec = info.deviceSpec
                    }
                    break
                }
            }
        }
    }

    fun updateDeviceName(name: String, onResult: (Boolean) -> Unit) {
        val device = deviceManager.device ?: return onResult(false)
        val id = deviceId ?: return onResult(false)
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return onResult(false)
        }
        isUpdatingDeviceName = true
        val args = com.bringyour.sdk.DeviceSetNameArgs()
        args.deviceId = id
        args.deviceName = trimmed
        device.api?.deviceSetName(args) { result, err ->
            viewModelScope.launch {
                isUpdatingDeviceName = false
                if (err == null && result?.error == null) {
                    deviceName = trimmed
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }
    }

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

    fun onPermissionResult(isGranted: Boolean, activity: ComponentActivity? = null) {
        notificationPermissionRequested = true
        _permissionGranted.value = isGranted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isGranted) {
            setNotificationsPermanentlyDenied(false)
        } else {
            val shouldShowRationale = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            ) ?: true
            setNotificationsPermanentlyDenied(!shouldShowRationale)
        }
    }

    val triggerPermissionRequest: () -> Unit = {
        notificationPermissionRequested = true
        _requestPermission.value = true
    }

    val resetPermissionRequest: () -> Unit = {
        _requestPermission.value = false
    }

    fun checkPermissionStatus(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _permissionGranted.value = true
            setNotificationsPermanentlyDenied(false)
            return
        }

        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        _permissionGranted.value = isGranted

        if (isGranted) {
            setNotificationsPermanentlyDenied(false)
        } else {
            val shouldShowRationale = activity.shouldShowRequestPermissionRationale(
                Manifest.permission.POST_NOTIFICATIONS
            )
            setNotificationsPermanentlyDenied(notificationPermissionRequested && !shouldShowRationale)
        }
    }

    private fun addAllowProductUpdatesListener(vc: AccountPreferencesViewController) {
        vc.addAllowProductUpdatesListener {
            viewModelScope.launch {
                if (accountPreferencesVc !== vc) {
                    return@launch
                }
                setAllowProductUpdates(vc.allowProductUpdates)
            }
        }?.let { subs.add(it) }
    }

    val toggleAllowProductUpdates: () -> Unit = {
        val currentAllowProductUpdates = allowProductUpdates
        accountPreferencesVc?.updateAllowProductUpdates(!currentAllowProductUpdates)
    }

    val setProvideControlMode: (ProvideControlMode) -> Unit = { mode ->
        deviceManager.provideControlMode = mode
        this.provideControlMode = mode
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
        } ?: run {
            _isDeletingAccount.value = false
        }
    }

    private val _isAddingAuth = MutableStateFlow(false)
    val isAddingAuth: StateFlow<Boolean> = _isAddingAuth

    val addAuth: (
        args: AddAuthArgs,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { args, onSuccess, onError ->

        _isAddingAuth.value = true

        deviceManager.device?.api?.addAuth(args) { result, err ->
            viewModelScope.launch {
                _isAddingAuth.value = false

                if (err != null) {
                    onError(err.message ?: "Failed to add sign-in method")
                } else if (result?.error != null) {
                    onError(result.error.message ?: "Failed to add sign-in method")
                } else if (result != null) {
                    onSuccess()
                } else {
                    onError("Failed to add sign-in method")
                }
            }
        } ?: run {
            _isAddingAuth.value = false
            onError("Unable to connect. Please try again.")
        }
    }

    private val _isRemovingAuth = MutableStateFlow(false)
    val isRemovingAuth: StateFlow<Boolean> = _isRemovingAuth

    val removeAuth: (
        authType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { authType, onSuccess, onError ->

        _isRemovingAuth.value = true

        val args = RemoveAuthArgs()
        args.authType = authType

        deviceManager.device?.api?.removeAuth(args) { result, err ->
            viewModelScope.launch {
                _isRemovingAuth.value = false

                if (err != null) {
                    onError(err.message ?: "Failed to remove sign-in method")
                } else if (result?.error != null) {
                    onError(result.error.message ?: "Failed to remove sign-in method")
                } else if (result != null) {
                    onSuccess()
                } else {
                    onError("Failed to remove sign-in method")
                }
            }
        } ?: run {
            _isRemovingAuth.value = false
            onError("Unable to connect. Please try again.")
        }
    }

    private val _generatedSeedphrase = MutableStateFlow<String?>(null)
    val generatedSeedphrase: StateFlow<String?> = _generatedSeedphrase

    private val _isGeneratingSeedphrase = MutableStateFlow(false)
    val isGeneratingSeedphrase: StateFlow<Boolean> = _isGeneratingSeedphrase

    private val _isRegeneratingSeedphrase = MutableStateFlow(false)
    val isRegeneratingSeedphrase: StateFlow<Boolean> = _isRegeneratingSeedphrase

    val generateSeedphrase: (onError: (String) -> Unit) -> Unit = { onError ->

        _isGeneratingSeedphrase.value = true

        deviceManager.device?.api?.generateSeedphrase(GenerateSeedphraseArgs()) { result, err ->
            viewModelScope.launch {
                _isGeneratingSeedphrase.value = false

                if (err != null) {
                    onError(err.message ?: "Failed to generate seedphrase")
                } else if (result?.error != null) {
                    onError(result.error.message ?: "Failed to generate seedphrase")
                } else if (result != null && result.seedphrase.isNotEmpty()) {
                    _generatedSeedphrase.value = result.seedphrase
                } else {
                    onError("Failed to generate seedphrase")
                }
            }
        } ?: run {
            _isGeneratingSeedphrase.value = false
            onError("Unable to connect. Please try again.")
        }
    }

    val regenerateSeedphrase: (onError: (String) -> Unit) -> Unit = { onError ->

        _isRegeneratingSeedphrase.value = true

        deviceManager.device?.api?.regenerateSeedphrase(RegenerateSeedphraseArgs()) { result, err ->
            viewModelScope.launch {
                _isRegeneratingSeedphrase.value = false

                if (err != null) {
                    onError(err.message ?: "Failed to regenerate seedphrase")
                } else if (result?.error != null) {
                    onError(result.error.message ?: "Failed to regenerate seedphrase")
                } else if (result != null && result.seedphrase.isNotEmpty()) {
                    _generatedSeedphrase.value = result.seedphrase
                } else {
                    onError("Failed to regenerate seedphrase")
                }
            }
        } ?: run {
            _isRegeneratingSeedphrase.value = false
            onError("Unable to connect. Please try again.")
        }
    }

    val dismissSeedphraseDisplay: () -> Unit = {
        _generatedSeedphrase.value = null
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
                    viewModelScope.launch { _isCreatingAuthCode.value = false }
                    return@authCodeCreate
                }

                if (result.error != null) {
                    Log.i(TAG, "result error creating auth code: ${result.error.message}")
                    viewModelScope.launch { _isCreatingAuthCode.value = false }
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

    private fun addProvideEnabledListener(
        device: DeviceLocal,
        vc: AccountPreferencesViewController,
    ) {
        // Track the LIVE effective provide mode: Network provide (same-network
        // peers) is always active, so the provider merely existing
        // (provideEnabled) no longer means the device provides publicly.
        // ProvideMode is a bit set: compare per-case, never with ranges.
        val sub = device.addProvideModeChangeListener { provideMode ->
            viewModelScope.launch {
                if (accountPreferencesVc !== vc) {
                    return@launch
                }
                _provideMode.value = provideMode
            }
        }
        sub?.let { subs.add(it) }
    }

    private fun addProvidePausedListener(
        device: DeviceLocal,
        vc: AccountPreferencesViewController,
    ) {
        val sub = device.addProvidePausedChangeListener {
            viewModelScope.launch {
                if (accountPreferencesVc !== vc) {
                    return@launch
                }
                _providePaused.value = device.providePaused
            }
        }
        sub?.let { subs.add(it) }
    }

    // the indicator encodes the LIVE effective provide tier (apple parity):
    // Network provide (incl. Auto while idle) = solid green dot; Public
    // provide = green dot + outer green ring (yellow while paused, which
    // stops public only); not providing = red dot, no ring
    val provideIndicatorColor: Color
        get() = when (provideMode.value) {
            Sdk.ProvideModePublic -> if (providePaused.value) Yellow else Green
            Sdk.ProvideModeNetwork, Sdk.ProvideModeFriendsAndFamily -> Green
            else -> Red
        }

    val provideIndicatorRingColor: Color?
        get() = when {
            provideMode.value != Sdk.ProvideModePublic -> null
            providePaused.value -> Yellow
            else -> Green
        }

    init {
        provideControlMode = deviceManager.provideControlMode

        _allowProvideOnCell.value = deviceManager.provideNetworkMode == ProvideNetworkMode.ALL

        version = Sdk.getVersion()

        processLifecycle.addObserver(this)
        controllerOwner.setForeground(
            processLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                _routeLocal.value = device?.routeLocal == true
                _providePaused.value = device?.providePaused == true
                _provideMode.value = device?.provideMode ?: Sdk.ProvideModeNone
                controllerOwner.setDevice(device)
                if (device != null) {
                    fetchReferralNetwork()
                    fetchStripeCustomerPortalUrl()
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        controllerOwner.setForeground(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        controllerOwner.setForeground(false)
    }

    private fun openAccountPreferencesViewController(
        device: DeviceLocal
    ): AccountPreferencesViewController {
        val vc = device.openAccountPreferencesViewController()
        accountPreferencesVc = vc
        addAllowProductUpdatesListener(vc)
        addProvideEnabledListener(device, vc)
        addProvidePausedListener(device, vc)
        vc.start()
        return vc
    }

    private fun closeAccountPreferencesViewController(
        device: DeviceLocal,
        vc: AccountPreferencesViewController,
    ) {
        subs.forEach { it.close() }
        subs.clear()
        vc.stop()
        device.closeViewController(vc)
        if (accountPreferencesVc === vc) {
            accountPreferencesVc = null
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
