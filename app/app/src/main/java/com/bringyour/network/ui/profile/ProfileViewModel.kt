package com.bringyour.network.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bringyour.network.DeviceManager
import com.bringyour.network.NetworkSpaceManagerProvider
import com.bringyour.network.ui.settings.authTypesContains
import com.bringyour.sdk.ChangeNetworkNameArgs
import com.bringyour.sdk.ClaimNetworkNameArgs
import com.bringyour.sdk.NetworkNameValidationViewController
import com.bringyour.sdk.NetworkUser
import com.bringyour.sdk.Sdk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val deviceManager: DeviceManager,
    networkSpaceManagerProvider: NetworkSpaceManagerProvider
): ViewModel() {

    private var networkNameValidationVc: NetworkNameValidationViewController? = null

    var isEditingProfile by mutableStateOf(false)
        private set

    var isValidatingNetworkName by mutableStateOf(false)
        private set

    val setIsValidatingNetworkName: (Boolean) -> Unit = { iv ->
        isValidatingNetworkName = iv
    }

    private var networkUser: NetworkUser? = null

    var networkNameTextFieldValue by mutableStateOf(TextFieldValue())
        private set

    var networkNameIsValid by mutableStateOf(true)
        private set

    val setNetworkNameIsValid: (Boolean) -> Unit = { isValid ->
        networkNameIsValid = isValid
    }

    private val _isSavingNetworkName = MutableStateFlow(false)
    val isSavingNetworkName: StateFlow<Boolean> = _isSavingNetworkName

    private val _networkNameError = MutableStateFlow<String?>(null)
    val networkNameError: StateFlow<String?> = _networkNameError

    private val _needsNameClaim = MutableStateFlow(false)
    val needsNameClaim: StateFlow<Boolean> = _needsNameClaim

    val setNetworkNameTextFieldValue: (TextFieldValue) -> Unit = {
        networkNameTextFieldValue = it
    }

    val setIsEditingProfile: (Boolean) -> Unit = {
        isEditingProfile = it
        if (it) {
            _networkNameError.value = null
        }
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

    val saveNetworkName: (
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { onSuccess, onError ->

        val name = networkNameTextFieldValue.text.trim()
        _isSavingNetworkName.value = true
        _networkNameError.value = null

        val args = ChangeNetworkNameArgs()
        args.newName = name

        deviceManager.device?.api?.changeNetworkName(args) { result, err ->
            viewModelScope.launch {
                _isSavingNetworkName.value = false

                if (err != null) {
                    val msg = err.message ?: "Failed to change network name"
                    _networkNameError.value = msg
                    onError(msg)
                } else if (result?.error != null) {
                    val msg = result.error.message ?: "Failed to change network name"
                    _networkNameError.value = msg
                    onError(msg)
                } else {
                    deviceManager.device?.refreshToken(0)
                    onSuccess()
                }
            }
        } ?: run {
            _isSavingNetworkName.value = false
            val msg = "Unable to connect. Please try again."
            _networkNameError.value = msg
            onError(msg)
        }
    }

    val claimNetworkName: (
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { onSuccess, onError ->

        val name = networkNameTextFieldValue.text.trim()
        _isSavingNetworkName.value = true
        _networkNameError.value = null

        val args = ClaimNetworkNameArgs()
        args.newName = name

        deviceManager.device?.api?.claimNetworkName(args) { result, err ->
            viewModelScope.launch {
                _isSavingNetworkName.value = false

                if (err != null) {
                    val msg = err.message ?: "Failed to claim network name"
                    _networkNameError.value = msg
                    onError(msg)
                } else if (result?.error != null) {
                    val msg = result.error.message ?: "Failed to claim network name"
                    _networkNameError.value = msg
                    onError(msg)
                } else {
                    deviceManager.device?.refreshToken(0)
                    onSuccess()
                }
            }
        } ?: run {
            _isSavingNetworkName.value = false
            val msg = "Unable to connect. Please try again."
            _networkNameError.value = msg
            onError(msg)
        }
    }

    val cancelEdits = {
        setNetworkNameTextFieldValue(TextFieldValue(networkUser?.networkName ?: ""))
        _networkNameError.value = null
        setIsEditingProfile(false)
    }

    val setNetworkUser: (NetworkUser?) -> Unit = { nu ->
        networkUser = nu
        setNetworkNameTextFieldValue(TextFieldValue(nu?.networkName ?: ""))
        _needsNameClaim.value = nu?.let {
            val hasIdentityMethod = authTypesContains(it.authTypes, "email") ||
                authTypesContains(it.authTypes, "phone") ||
                authTypesContains(it.authTypes, "google") ||
                authTypesContains(it.authTypes, "apple") ||
                authTypesContains(it.authTypes, "solana")
            !hasIdentityMethod
        } ?: false
    }

    init {

        networkNameValidationVc = Sdk.newNetworkNameValidationViewController(
            networkSpaceManagerProvider.getNetworkSpace()?.api
        )

    }

    override fun onCleared() {
        super.onCleared()

        networkNameValidationVc?.close()
    }

}
