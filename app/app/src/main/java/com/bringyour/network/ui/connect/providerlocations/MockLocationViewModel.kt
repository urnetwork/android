package com.bringyour.network.ui.connect.providerlocations

import androidx.lifecycle.ViewModel
import com.bringyour.network.location.MockLocationController
import com.bringyour.network.location.MockLocationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UI bridge for the mock location controller: exposes state and UI actions.
 * SDK event feeds are managed at process lifetime by MockLocationFeeder.
 */
@HiltViewModel
class MockLocationViewModel @Inject constructor(
    private val controller: MockLocationController,
) : ViewModel() {

    val state: StateFlow<MockLocationState> = controller.state

    fun setEnabled(enabled: Boolean) {
        controller.setEnabled(enabled)
    }

    fun refreshEligibility() {
        controller.refreshEligibility()
    }
}

