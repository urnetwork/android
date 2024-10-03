package com.bringyour.network.ui.shared.viewmodels

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bringyour.client.ReferralCodeViewController
import com.bringyour.network.ByDeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ReferralCodeViewModel @Inject constructor(
    byDeviceManager: ByDeviceManager,
): ViewModel() {

    private var referralCodeVc: ReferralCodeViewController? = null

    var referralLink by mutableStateOf<String?>(null)
        private set

    val addReferralCodeListener = {
        referralCodeVc?.addReferralCodeListener { code ->
            referralLink = "https://ur.io/app?bonus=$code"
        }
    }

    init {

        referralCodeVc = byDeviceManager.byDevice?.openReferralCodeViewController()

        addReferralCodeListener()

        referralCodeVc?.start()

    }

}