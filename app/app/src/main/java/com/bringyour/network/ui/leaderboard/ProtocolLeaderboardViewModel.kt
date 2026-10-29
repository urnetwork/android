package com.bringyour.network.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Protocol tab of the leaderboard (apple/LEADERTOP.md): the board's top
 * 400 from the sdk's SnTop and the caller's standing from SnTopMe, made on the
 * device's api.
 *
 * Binds [ProtocolLeaderboardModel] to the app: the device (a replaced device
 * drops the former account's board and answers) and the tab's visibility
 * (LifecycleStartEffect in ProtocolLeaderboardTab). Answers arrive on sdk
 * threads and are posted to the main thread.
 */
@HiltViewModel
class ProtocolLeaderboardViewModel @Inject constructor(
    deviceManager: DeviceManager,
) : ViewModel() {

    private val model = ProtocolLeaderboardModel(
        settings = defaultProtocolLeaderboardSettings(),
        post = { block -> viewModelScope.launch { block() } },
    )
    private var removeDeviceChangeListener: (() -> Unit)? = null

    val ui: ProtocolLeaderboardUi get() = model.ui

    val actionFailed: Boolean get() = model.actionFailed

    val settingNamePublic: Boolean get() = model.ui.settingNamePublic

    /** The network's own name from the jwt: the card's name once the switch shows it. */
    val ownNetworkName: StateFlow<String> = deviceManager.jwtFlow
        .map { it?.networkName ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, deviceManager.jwtFlow.value?.networkName ?: "")

    init {
        removeDeviceChangeListener = deviceManager.addDeviceChangeListener { device ->
            viewModelScope.launch {
                model.setClient(device?.api?.let { SdkProtocolTopClient(it) })
            }
        }
    }

    fun setVisible(visible: Boolean) = model.setVisible(visible)

    fun refresh() = model.refresh()

    fun retry() = model.retry()

    fun loadMore() = model.loadMore()

    fun onLastVisibleRow(index: Int) = model.onLastVisibleRow(index)

    fun seekTo(position: Long) = model.seekTo(position)

    fun setNamePublic(public: Boolean) = model.setNamePublic(public)

    fun clearActionFailed() = model.clearActionFailed()

    /** The screenshot tests' board, which they draw with no device. */
    internal fun useClient(client: ProtocolTopClient?) = model.setClient(client)

    override fun onCleared() {
        removeDeviceChangeListener?.invoke()
        removeDeviceChangeListener = null
        model.close()
        super.onCleared()
    }
}
