package com.bringyour.network.ui.provide

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProvideViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Provide"
    }
    val text: LiveData<String> = _text
}