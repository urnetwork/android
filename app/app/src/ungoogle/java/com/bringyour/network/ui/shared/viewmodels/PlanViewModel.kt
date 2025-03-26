package com.bringyour.network.ui.shared.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _onUpgradeSuccess = MutableSharedFlow<Unit>()
    val onUpgradeSuccess: SharedFlow<Unit> = _onUpgradeSuccess.asSharedFlow()

    private val _currentPlan = MutableStateFlow<Plan>(Plan.Basic)
    val currentPlan: StateFlow<Plan> = _currentPlan.asStateFlow()

    var inProgress by mutableStateOf(false)
        private set

    var formattedSubscriptionPrice by mutableStateOf("")

    val upgrade: () -> Unit = {

    }
}
