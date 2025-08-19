package com.bringyour.network.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.AccountPoint
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountPointsViewModel
@Inject
constructor(
        deviceManager: DeviceManager,
) : ViewModel() {

    private val _accountPoints = MutableStateFlow<List<AccountPoint>>(emptyList())
    val accountPoints: StateFlow<List<AccountPoint>> = _accountPoints.asStateFlow()

    private val _totalAccountPoints = MutableStateFlow<Double>(0.0)
    val totalAccountPoints: StateFlow<Double> = _totalAccountPoints.asStateFlow()

    private val _payoutPoints = MutableStateFlow<Double>(0.0)
    val payoutPoints: StateFlow<Double> = _payoutPoints.asStateFlow()

    private val _multiplierPoints = MutableStateFlow<Double>(0.0)
    val multiplierPoints: StateFlow<Double> = _multiplierPoints.asStateFlow()

    private val _referralPoints = MutableStateFlow<Double>(0.0)
    val referralPoints: StateFlow<Double> = _referralPoints.asStateFlow()

    private val _reliabilityPoints = MutableStateFlow<Double>(0.0)
    val reliabilityPoints: StateFlow<Double> = _reliabilityPoints.asStateFlow()

    val fetchAccountPoints = {
        deviceManager.device?.api?.getAccountPoints { result, error ->
            if (error != null) {
                return@getAccountPoints
            }

            val n = result.accountPoints?.len() ?: 0

            val accountPoints = mutableListOf<AccountPoint>()
            var totalAccountPoints = 0.0
            var payoutPoints = 0.0
            var referralPoints = 0.0
            var multiplierPoints = 0.0
            var reliabilityPoints = 0.0

            for (i in 0 until n) {

                val accountPoint = result.accountPoints.get(i)
                accountPoints.add(accountPoint)

                val pointValue = Sdk.nanoPointsToPoints(accountPoint.pointValue)

                totalAccountPoints += pointValue

                val event = AccountPointEvent.fromString(accountPoint.event)
                if (event != null) {

                    when (event) {
                        AccountPointEvent.PAYOUT -> payoutPoints += pointValue
                        AccountPointEvent.PAYOUT_LINKED_ACCOUNT -> referralPoints += pointValue
                        AccountPointEvent.PAYOUT_MULTIPLIER -> multiplierPoints += pointValue
                        AccountPointEvent.PAYOUT_RELIABILITY -> reliabilityPoints += pointValue
                    }
                }
            }

            viewModelScope.launch {
                _accountPoints.value = accountPoints
                _totalAccountPoints.value = totalAccountPoints
                _payoutPoints.value = payoutPoints
                _referralPoints.value = referralPoints
                _multiplierPoints.value = multiplierPoints
                _reliabilityPoints.value = reliabilityPoints
            }
        }
    }

    val getTotalPointsByPaymentId: (String) -> Double = { id ->
        _accountPoints.value
            .filter { ap ->
                ap.accountPaymentId.idStr == id
            }
            .sumOf { accountPoint -> Sdk.nanoPointsToPoints(accountPoint.pointValue) }
    }

    val getPayoutEventPointsByPaymentId: (String, AccountPointEvent) -> Double = { id, event ->
        _accountPoints.value
            .filter { ap ->
                ap.accountPaymentId.idStr == id && AccountPointEvent.fromString(ap.event) == event
            }
            .sumOf { accountPoint -> Sdk.nanoPointsToPoints(accountPoint.pointValue) }
    }

    init {
        fetchAccountPoints()
    }
}

enum class AccountPointEvent {
    PAYOUT,
    PAYOUT_LINKED_ACCOUNT,
    PAYOUT_MULTIPLIER,
    PAYOUT_RELIABILITY;

    companion object {
        fun fromString(value: String): AccountPointEvent? {
            return when (value.uppercase()) {
                "PAYOUT" -> PAYOUT
                "PAYOUT_LINKED_ACCOUNT" -> PAYOUT_LINKED_ACCOUNT
                "PAYOUT_MULTIPLIER" -> PAYOUT_MULTIPLIER
                "PAYOUT_RELIABILITY" -> PAYOUT_RELIABILITY
                else -> null
            }
        }
    }
}
