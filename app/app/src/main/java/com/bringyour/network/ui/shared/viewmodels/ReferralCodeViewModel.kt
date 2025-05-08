package com.bringyour.network.ui.shared.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    var referralLink by mutableStateOf<String?>(null)
        private set

    var totalReferralCount by mutableLongStateOf(0)
        private set

    val fetchReferralLink = {
        deviceManager.device?.api?.getNetworkReferralCode { result, error ->
            viewModelScope.launch {
                referralLink = "https://ur.io/c?bonus=${result.referralCode}"
                totalReferralCount = result.totalReferrals
            }
        }
    }

    init {
        fetchReferralLink()
    }

}