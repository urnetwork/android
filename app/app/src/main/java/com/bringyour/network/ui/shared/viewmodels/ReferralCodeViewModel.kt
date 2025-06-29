package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    var referralCode by mutableStateOf("")
        private set

    var totalReferralCount by mutableLongStateOf(0)
        private set

    val fetchReferralCode = {
        deviceManager.device?.api?.getNetworkReferralCode { result, error ->
            viewModelScope.launch {
                if (result != null) {
                    referralCode = result.referralCode
                    totalReferralCount = result.totalReferrals
                } else {
                    Log.i(TAG, "Could not fetch referral info: $error")
                }
            }
        }
    }

    init {
        fetchReferralCode()
    }

}