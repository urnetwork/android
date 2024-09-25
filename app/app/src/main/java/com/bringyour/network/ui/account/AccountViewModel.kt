package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.NetworkUser
import com.bringyour.client.NetworkUserViewController
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

    var networkUserVc: NetworkUserViewController? = null

    var loginMode by mutableStateOf<LoginMode>(LoginMode.Guest)
        private set

    val setLoginMode: (LoginMode) -> Unit = { mode ->
        Log.i("AccountViewModel", "setting login mode to $mode")
        loginMode = mode
    }

//    var networkName by mutableStateOf<String?>(null)
//        private set

//    private val setNetworkName: (String) -> Unit = { name ->
//        Log.i("AccountViewModel", "setting network name to $name")
//        networkName = name
//        Log.i("AccountViewModel", "networkName is $networkName")
//    }

    var networkUser by mutableStateOf<NetworkUser?>(null)
        private set

    private val setNetworkUser: (NetworkUser?) -> Unit = { nu ->
        networkUser = nu
    }

    private val addNetworkUserListener = {
        networkUserVc?.addNetworkUserListener {
            setNetworkUser(networkUserVc?.networkUser)
        }
    }

    var clientId by mutableStateOf("")
        private set

    val upgradePlan = {}

    val getCurrentPlan = {}

    init {

        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        networkUserVc = byDeviceManager.byDevice?.openNetworkUserViewController()

        addNetworkUserListener()

        localState?.parseByJwt { jwt, _ ->
            viewModelScope.launch {
                setLoginMode(if (jwt?.guestMode == true) LoginMode.Guest else LoginMode.Authenticated)
               // setNetworkName(jwt?.networkName ?: "guest")
            }
        }

        byDeviceManager.byDevice.let { device ->
            viewModelScope.launch {
                clientId = device?.clientId()?.idStr ?: ""
            }
        }

        viewModelScope.launch {
            networkUserVc?.start()
        }

    }

}