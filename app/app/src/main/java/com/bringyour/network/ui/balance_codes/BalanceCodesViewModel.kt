package com.bringyour.network.ui.balance_codes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.BlockedLocation
import com.bringyour.sdk.RedeemedBalanceCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BalanceCodesViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
): ViewModel() {

    private val _isFetchingBalanceCodes = MutableStateFlow<Boolean>(false)
    val isFetchingBalanceCodes: StateFlow<Boolean> = _isFetchingBalanceCodes.asStateFlow()

    private val _balanceCodes = MutableStateFlow<List<RedeemedBalanceCode>>(emptyList())
    val balanceCodes: StateFlow<List<RedeemedBalanceCode>> = _balanceCodes.asStateFlow()

    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()

    private val _displayBottomSheet = MutableStateFlow<Boolean>(false)
    val displayBottonSheet: StateFlow<Boolean> = _displayBottomSheet.asStateFlow()

    val setDisplayBottomSheet: (Boolean) -> Unit = {
        _displayBottomSheet.value = it
    }

    val fetchNetworkBalanceCodes: () -> Unit = {

        if (!_isFetchingBalanceCodes.value) {

            _isFetchingBalanceCodes.value = true

            deviceManager.device?.api?.getNetworkRedeemedBalanceCodes { result, err ->

                viewModelScope.launch {
                    if (err != null) {
                        _errorEvents.send("Error fetching network balance codes")
                        _isFetchingBalanceCodes.value = false
                        return@launch
                    }

                    if (result.error != null) {
                        _errorEvents.send("Error fetching network balance codes: ${result.error.message}")
                        _isFetchingBalanceCodes.value = false
                        return@launch
                    }

                    if (result.balanceCodes != null) {

                        val balanceCodes = mutableListOf<RedeemedBalanceCode>()
                        val n = result.balanceCodes.len()

                        for (i in 0 until n) {
                            val balanceCode = result.balanceCodes.get(i)
                            balanceCodes.add(balanceCode)
                        }

                        _balanceCodes.value = balanceCodes
                    }
                    _isFetchingBalanceCodes.value = false

                }

            } ?: {
                viewModelScope.launch {
                    _errorEvents.send("API not found")
                    _isFetchingBalanceCodes.value = false
                }
            }

        }

    }

    init {
        fetchNetworkBalanceCodes()
    }

}