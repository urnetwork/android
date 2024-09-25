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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    // private val networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    var networkUserVc: NetworkUserViewController? = null

//    var isNetworkUserLoading by mutableStateOf(false)
//        private set

    var isEditingProfile by mutableStateOf(false)
        private set

    var isUpdatingProfile by mutableStateOf(false)
        private set

    private var networkUser by mutableStateOf<NetworkUser?>(null)

//    var networkName by mutableStateOf<String?>(null)
//        private set

    var nameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    var networkNameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    var networkNameIsValid by mutableStateOf(false)
        private set

    var nameIsValid by mutableStateOf(false)
        private set

//    val addIsLoadingListener = {
//        networkUserVc?.addIsLoadingListener { isLoading ->
//            isNetworkUserLoading = isLoading
//        }
//    }

//    private val addNetworkUserListener = {
//        networkUserVc?.addNetworkUserListener {
//            networkUser = networkUserVc?.networkUser
//            setNameTextFieldValue(TextFieldValue(networkUser?.userName ?: ""))
//        }
//    }

    private val addIsUpdatingListener = {
        networkUserVc?.addIsUpdatingListener { isUpdating ->
            isUpdatingProfile = isUpdating
        }
    }

    val setNetworkNameTextFieldValue: (TextFieldValue) -> Unit = {
        networkNameTextFieldValue = it
        validateNetworkName(networkNameTextFieldValue.text)
    }

    val setNameTextFieldValue: (TextFieldValue) -> Unit = {
        nameTextFieldValue = it
        validateName(nameTextFieldValue.text)
    }

    val setIsEditingProfile: (Boolean) -> Unit = {
        isEditingProfile = it
    }

    val validateNetworkName: (String) -> Unit = { nn ->

        // todo - validate && check availability of network name

        networkNameIsValid = true

    }

    val validateName: (String) -> Unit = { nn ->

        nameIsValid = nn.length < 100

    }

    val updateProfile = {
        if (networkNameIsValid && nameIsValid) {
            networkUserVc?.updateNetworkUser(networkNameTextFieldValue.text, nameTextFieldValue.text)
        }
    }

    val cancelEdits = {
        setNameTextFieldValue(TextFieldValue(networkUser?.userName ?: ""))
        setNetworkNameTextFieldValue(TextFieldValue(networkUser?.networkName ?: ""))
        setIsEditingProfile(false)
    }

    val initNetworkUser: (NetworkUser?) -> Unit = { nu ->
        networkUser = nu

        setNameTextFieldValue(TextFieldValue(nu?.userName ?: ""))
        setNetworkNameTextFieldValue(TextFieldValue(nu?.networkName ?: ""))
    }

    init {

        networkUserVc = byDeviceManager.byDevice?.openNetworkUserViewController()

        addIsUpdatingListener()

    }

    override fun onCleared() {
        super.onCleared()

        networkUserVc?.let {
            byDeviceManager.byDevice?.closeViewController(it)
        }
    }

}