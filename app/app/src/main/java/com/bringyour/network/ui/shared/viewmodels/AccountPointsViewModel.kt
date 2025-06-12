package com.bringyour.network.ui.shared.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.AccountPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AccountPointsViewModel @Inject constructor(
    deviceManager: DeviceManager,
): ViewModel() {

    private val _accountPoints = MutableStateFlow<List<AccountPoint>>(emptyList())
    val accountPoints: StateFlow<List<AccountPoint>> = _accountPoints.asStateFlow()

    var totalAccountPoints by mutableIntStateOf(0)
        private set

    val fetchAccountPoints = {

        deviceManager.device?.api?.getAccountPoints { result, error ->

             if (error != null) {
                 return@getAccountPoints
             }

            val n = result.accountPoints.len()

            val accountPoints = mutableListOf<AccountPoint>()
            var totalAccountPoints = 0

            for (i in 0 until n) {

                val accountPoint = result.accountPoints.get(i)
                accountPoints.add(accountPoint)
                totalAccountPoints += accountPoint.pointValue.toInt()

            }

            _accountPoints.value = accountPoints
            this.totalAccountPoints = totalAccountPoints

        }

    }

    init {
        fetchAccountPoints()
    }

}