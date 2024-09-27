package com.bringyour.network.ui.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.ByDeviceManager
import com.bringyour.network.CircleWalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CircleViewModel @Inject constructor(
    private val byDeviceManager: ByDeviceManager,
    private val circleWalletManager: CircleWalletManager,
): ViewModel() {

    var transferAmountTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var sendToAddress by mutableStateOf(TextFieldValue(""))
        private set

    val setTransferAmount: (TextFieldValue) -> Unit = { tfv ->

        // validate is a number

        transferAmountTextFieldValue = tfv
    }

    val setSendToAddress: (TextFieldValue) -> Unit = { tfv ->
        sendToAddress = tfv
    }



}