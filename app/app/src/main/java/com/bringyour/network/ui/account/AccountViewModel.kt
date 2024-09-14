package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.components.LoginMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AccountViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    var loginMode by mutableStateOf<LoginMode>(LoginMode.Guest)
        private set

    val setLoginMode: (LoginMode) -> Unit = { mode ->
        Log.i("AccountViewModel", "setting login mode to $mode")
        loginMode = mode
    }

    var networkName by mutableStateOf<String?>(null)
        private set

    private val setNetworkName: (String) -> Unit = { name ->
        Log.i("AccountViewModel", "setting network name to $name")
        networkName = name
        Log.i("AccountViewModel", "networkName is $networkName")
    }

    var clientId by mutableStateOf("")
        private set

    val upgradePlan = {}

    val getCurrentPlan = {}

    init {

        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, _ ->
            viewModelScope.launch {
                setLoginMode(if (jwt?.guestMode == true) LoginMode.Guest else LoginMode.Authenticated)
                setNetworkName(jwt?.networkName ?: "guest")
            }
        }

        byDeviceManager.getByDevice().let { device ->
            viewModelScope.launch {
                clientId = device?.clientId()?.idStr ?: ""
            }
        }

    }

}