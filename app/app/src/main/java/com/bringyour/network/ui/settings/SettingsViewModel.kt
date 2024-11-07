package com.bringyour.network.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.AccountPreferencesViewController
import com.bringyour.client.Client
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    networkSpaceManagerProvider: NetworkSpaceManagerProvider,
    @ApplicationContext private val context: Context
): ViewModel() {

    private var accountPreferencesVc: AccountPreferencesViewController? = null

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _requestPermission = MutableStateFlow(false)
    val requestPermission: StateFlow<Boolean> = _requestPermission

    var notificationsPermanentlyDenied by mutableStateOf(false)

    val setNotificationsPermanentlyDenied: (Boolean) -> Unit = { pd ->
        notificationsPermanentlyDenied = pd
    }

    var allowProductUpdates by mutableStateOf(false)
        private set

    var provideWhileDisconnected by mutableStateOf(false)
        private set

    val setAllowProductUpdates: (Boolean) -> Unit = { allow ->
        allowProductUpdates = allow
    }

    val urIdUrl: (String) -> String? = { clientId ->
        networkSpaceManagerProvider.getNetworkSpace()?.connectLinkUrl(clientId)
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
        viewModelScope.launch {
            accountPreferencesVc?.let { vc ->
                vc.addAllowProductUpdatesListener {
                    setAllowProductUpdates(vc.allowProductUpdates)
                }
            }
        }
    }

    val updateAllowProductUpdates: (Boolean) -> Unit = { allow ->
        accountPreferencesVc?.updateAllowProductUpdates(allow)
    }

    val toggleProvideWhileDisconnected: () -> Unit = {
        val currentProvideWhileDisconnected = provideWhileDisconnected
        byDeviceManager.provideWhileDisconnected = !currentProvideWhileDisconnected
        provideWhileDisconnected = !currentProvideWhileDisconnected
    }

    init {
        accountPreferencesVc = byDeviceManager.byDevice?.openAccountPreferencesViewController()

        provideWhileDisconnected = byDeviceManager.byDevice?.provideWhileDisconnected ?: false

        addAllowProductUpdatesListener()

        accountPreferencesVc?.start()
    }

}