package com.bringyour.network.ui.blocked_regions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import com.bringyour.network.DeviceManager
import com.bringyour.sdk.BlockedLocation
import com.bringyour.sdk.ConnectLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


@HiltViewModel
class AddBlockedLocationViewModel @Inject constructor(): ViewModel() {

    private var countries: List<ConnectLocation> = listOf()

    private val _filteredCountries = MutableStateFlow<List<ConnectLocation>>(emptyList())
    val filteredCountries: StateFlow<List<ConnectLocation>> = _filteredCountries.asStateFlow()

    val initCountries: (List<ConnectLocation>) -> Unit = { c ->
        countries = c.sortedBy { it.name.lowercase() }
        _filteredCountries.value = countries
    }


    private val _searchQueryTextFieldValue = MutableStateFlow(TextFieldValue(""))
    val searchQueryTextFieldValue: StateFlow<TextFieldValue> = _searchQueryTextFieldValue


//    var currentSearchQuery by mutableStateOf("")
//        private set

    val updateSearchQuery: (TextFieldValue) -> Unit = {
        _searchQueryTextFieldValue.value = it
    }

    val filterLocations: (String) -> Unit = { query ->
        if (query.isEmpty()) {
            _filteredCountries.value = countries
        } else {

            _filteredCountries.value = countries.filter { it.name.lowercase().contains(query.lowercase()) }

        }
    }

}