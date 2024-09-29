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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    private val _requestPermission = MutableStateFlow(false)
    val requestPermission: StateFlow<Boolean> = _requestPermission

    var notificationsPermanentlyDenied by mutableStateOf(false)

    val setNotificationsPermanentlyDenied: (Boolean) -> Unit = { pd ->
        notificationsPermanentlyDenied = pd
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

}