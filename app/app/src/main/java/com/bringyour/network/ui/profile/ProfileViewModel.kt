package com.bringyour.network.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.sdk.Sdk
import com.bringyour.sdk.NetworkNameValidationViewController
import com.bringyour.sdk.NetworkUser
import com.bringyour.sdk.NetworkUserViewController
import com.bringyour.sdk.Sub
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private var networkUserVc: NetworkUserViewController? = null
    private var networkNameValidationVc: NetworkNameValidationViewController? = null

    var isEditingProfile by mutableStateOf(false)
        private set

    var isValidatingNetworkName by mutableStateOf(false)
        private set

    val setIsValidatingNetworkName: (Boolean) -> Unit = { iv ->
        isValidatingNetworkName = iv
    }

    var isUpdatingProfile by mutableStateOf(false)
        private set

    // todo - make this more robust with error messages
    var errorUpdatingProfile by mutableStateOf(false)
        private set

    val setErrorUpdatingProfile: (Boolean) -> Unit = { errExists ->
        errorUpdatingProfile = errExists
    }

    private var networkUser: NetworkUser? = null

    var networkNameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    var networkNameIsValid by mutableStateOf(true)
        private set

    val setNetworkNameIsValid: (Boolean) -> Unit = { isValid ->
        networkNameIsValid = isValid
    }

    var usernameIsValid by mutableStateOf(false)
        private set

    private val addIsUpdatingListener = {
        networkUserVc?.addIsUpdatingListener { isUpdating ->
            isUpdatingProfile = isUpdating
        }
    }

    val setNetworkNameTextFieldValue: (TextFieldValue) -> Unit = {
        networkNameTextFieldValue = it
    }

    val setIsEditingProfile: (Boolean) -> Unit = {
        isEditingProfile = it
    }

    val validateNetworkName: (String) -> Unit = { nn ->

        if (networkUser?.networkName != nn) {
            setIsValidatingNetworkName(true)

            networkNameValidationVc?.networkCheck(nn) { result, err ->
                viewModelScope.launch {

                    if (err == null) {
                        if (result.available) {
                            setNetworkNameIsValid(true)
                        } else {
                            setNetworkNameIsValid(false)
                        }
                    } else {
                        setNetworkNameIsValid(false)
                    }

                    setIsValidatingNetworkName(false)
                }
            }
        } else {
            setNetworkNameIsValid(true)
            setIsValidatingNetworkName(false)
        }

    }

    val updateProfile: () -> Unit = {
        if (networkNameIsValid && usernameIsValid) {
            setIsEditingProfile(false)
            networkUserVc?.updateNetworkUser(networkNameTextFieldValue.text)
        } else {
            setErrorUpdatingProfile(true)
        }
    }

    val cancelEdits = {
        setNetworkNameTextFieldValue(TextFieldValue(networkUser?.networkName ?: ""))
        setIsEditingProfile(false)
    }

    val setNetworkUser: (NetworkUser?) -> Unit = { nu ->
        networkUser = nu
        setNetworkNameTextFieldValue(TextFieldValue(nu?.networkName ?: ""))
    }

    private var updateSuccessListener: (() -> Unit)? = null

    val updateSuccessSub: (() -> Unit) -> Sub? = { callback ->
        updateSuccessListener = callback
        networkUserVc?.addNetworkUserUpdateSuccessListener {
            updateSuccessListener?.invoke()
        }
    }

    val addUpdateErrorListener = {
        networkUserVc?.addNetworkUserUpdateErrorListener {
            cancelEdits()
            setErrorUpdatingProfile(true)
        }
    }

    init {

        networkUserVc = deviceManager.vcManager?.openNetworkUserViewController()

        networkNameValidationVc = Sdk.newNetworkNameValidationViewController(
            networkSpaceManagerProvider.getNetworkSpace()?.api
        )

        addIsUpdatingListener()

        addUpdateErrorListener()

        networkUserVc?.start()

    }

    override fun onCleared() {
        super.onCleared()

        networkUserVc?.let {
            deviceManager.vcManager?.closeViewController(it)
        }

        updateSuccessListener = null
    }

}