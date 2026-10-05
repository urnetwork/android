package com.bringyour.network

import android.content.Context
import androidx.core.content.edit
import com.bringyour.network.ui.shared.models.ProvidePowerMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

internal const val PROVIDE_PAUSE_PREFERENCES = "provide_pause"
internal const val PROVIDE_POWER_MODE_KEY = "provide_power_mode"

/**
 * The provide power mode the user picked, stored with the app rather than in
 * the network space: it is about this device's battery, not the account. And
 * the pause decision the application last applied to `device.providePaused`
 * (see `providePauseDecision`), for the screens that explain a pause.
 */
@Singleton
class ProvidePauseState @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // lazy: the application is injected before credential storage unlocks
    // (Direct Boot), and these preferences live in credential storage
    private val preferences by lazy {
        context.getSharedPreferences(PROVIDE_PAUSE_PREFERENCES, Context.MODE_PRIVATE)
    }

    private val _powerMode by lazy {
        MutableStateFlow(
            ProvidePowerMode.fromString(preferences.getString(PROVIDE_POWER_MODE_KEY, null))
                ?: ProvidePowerMode.DEFAULT
        )
    }
    val powerMode: StateFlow<ProvidePowerMode> by lazy {
        _powerMode.asStateFlow()
    }

    private val _decision = MutableStateFlow(ProvidePauseDecision.Providing)
    val decision: StateFlow<ProvidePauseDecision> = _decision.asStateFlow()

    // set by the application, which decides the pause again on a change
    var onPowerModeChange: ((ProvidePowerMode) -> Unit)? = null

    /**
     * Stores the user's choice and has the application decide the pause again.
     * An unchanged mode does nothing.
     */
    fun setPowerMode(mode: ProvidePowerMode) {
        if (_powerMode.value == mode) {
            return
        }
        preferences.edit {
            putString(PROVIDE_POWER_MODE_KEY, ProvidePowerMode.toString(mode))
        }
        _powerMode.value = mode
        onPowerModeChange?.invoke(mode)
    }

    /** The decision the application applied, for the screens that explain a pause. */
    fun publishDecision(decision: ProvidePauseDecision) {
        _decision.value = decision
    }
}
