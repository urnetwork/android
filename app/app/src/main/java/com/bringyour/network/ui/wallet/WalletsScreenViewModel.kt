package com.bringyour.network.ui.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class WalletsScreenViewModel @Inject constructor(
    // private val deviceManager: DeviceManager
): ViewModel() {

    var isPresentedConnectWalletSheet by mutableStateOf(false)
        private set

    val setIsPresentedConnectWalletSheet: (Boolean) -> Unit = { isPresented ->
        isPresentedConnectWalletSheet = isPresented
    }

}