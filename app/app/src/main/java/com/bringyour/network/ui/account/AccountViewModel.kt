package com.bringyour.network.ui.account

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.AsyncLocalStateManager
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.ui.components.LoginMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class AccountViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val asyncLocalStateManager: AsyncLocalStateManager
): ViewModel() {

    var loginMode by mutableStateOf<LoginMode>(LoginMode.Guest)
        private set

    val setLoginMode: (LoginMode) -> Unit = { mode ->
        Log.i("AccountViewModel", "setting login mode to $mode")
        loginMode = mode
    }

    val setNetworkName: (String) -> Unit = { name ->
        networkName = name
    }

    var networkName by mutableStateOf("")
        private set

    var clientId by mutableStateOf("")
        private set

    val upgradePlan = {}

    val getCurrentPlan = {}

    init {

        val localState = asyncLocalStateManager.getAsyncLocalState()?.localState()

        localState?.parseByJwt().let { jwt ->
            setLoginMode(if (jwt?.guestMode == true) LoginMode.Guest else LoginMode.Authenticated)
            setNetworkName(jwt?.networkName ?: "guest")
        }

        byDeviceManager.getByDevice().let { device ->
            clientId = device?.clientId()?.idStr ?: ""
        }

    }

}