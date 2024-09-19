package com.bringyour.network.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.client.NetworkUser
import com.bringyour.client.NetworkUserViewController
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.components.LoginMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    var networkUserVc: NetworkUserViewController? = null

    var isNetworkUserLoading by mutableStateOf(false)
        private set

    var isEditingProfile by mutableStateOf(false)
        private set

    var networkUser by mutableStateOf<NetworkUser?>(null)
        private set

    var networkName by mutableStateOf<String?>(null)
        private set

    var nameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    var networkNameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    val addIsLoadingListener = {
        networkUserVc?.addIsLoadingListener { isLoading ->
            isNetworkUserLoading = isLoading
        }
    }

    val addNetworkUserListener = {
        networkUserVc?.addNetworkUserListener {
            networkUser = networkUserVc?.networkUser
            setNameTextFieldValue(TextFieldValue(networkUser?.userName ?: ""))
        }
    }

    val setNetworkNameTextFieldValue: (TextFieldValue) -> Unit = {
        networkNameTextFieldValue = it
    }

    val setNameTextFieldValue: (TextFieldValue) -> Unit = {
        nameTextFieldValue = it
    }

    val setIsEditingProfile: (Boolean) -> Unit = {
        isEditingProfile = it
    }

    val updateProfile = {

    }

    val cancelEdits = {
        setNameTextFieldValue(TextFieldValue(networkUser?.userName ?: ""))
        setIsEditingProfile(false)
    }

    init {

        networkUserVc = byDeviceManager.byDevice?.openNetworkUserViewController()

        val networkSpace = networkSpaceManagerProvider.getNetworkSpace()
        val localState = networkSpace?.asyncLocalState

        localState?.parseByJwt { jwt, _ ->
            viewModelScope.launch {
                networkName = jwt?.networkName
                networkNameTextFieldValue = TextFieldValue(networkName ?: "")
            }
        }

        addIsLoadingListener()
        addNetworkUserListener()

        networkUserVc?.start()

    }

    override fun onCleared() {
        super.onCleared()

        networkUserVc?.let {
            byDeviceManager.byDevice?.closeViewController(it)
        }
    }

}