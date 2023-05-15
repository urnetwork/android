package com.bringyour.network.ui.connect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ConnectViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Connect"
    }
    val text: LiveData<String> = _text
}