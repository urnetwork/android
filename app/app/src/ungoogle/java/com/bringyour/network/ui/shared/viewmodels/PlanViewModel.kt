package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.TAG
import com.bringyour.network.ui.components.LoginMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private val _onUpgradeSuccess = MutableSharedFlow<Unit>()
    val onUpgradeSuccess: SharedFlow<Unit> = _onUpgradeSuccess.asSharedFlow()

    val triggerUpgradeSuccess: () -> Unit = {
        viewModelScope.launch {
            _onUpgradeSuccess.emit(Unit)
        }
    }

    var networkId by mutableStateOf<String?>(null)
        private set

    // fixme: can we pull this from stripe? should we?
    val formattedSubscriptionPrice = "5.00"

    var inProgress by mutableStateOf(false)
        private set

    init {
        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, _ ->
            networkId = jwt.networkId.toString()
        }
    }
}
