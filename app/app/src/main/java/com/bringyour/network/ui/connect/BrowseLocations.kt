package com.bringyour.network.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.bringyour.sdk.ConnectLocation
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSearchInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BrowseLocations(
    selectedLocation: ConnectLocation?,
    connectCountries: List<ConnectLocation>,
    promotedLocations: List<ConnectLocation>,
    cities: List<ConnectLocation>,
    regions: List<ConnectLocation>,
    devices: List<ConnectLocation>,
    bestSearchMatches: List<ConnectLocation>,
    getLocationColor: (String) -> Color,
    filterLocations: (String) -> Unit,
    connect: (ConnectLocation?) -> Unit,
    fetchLocationsState: FilterLocationsState,
    searchQueryTextFieldValue: TextFieldValue,
    setSearchQueryTextFieldValue: (TextFieldValue) -> Unit,
    currentSearchQuery: String,
    keyboardController: SoftwareKeyboardController?,
    lazyListState: LazyListState,
    refreshLocations: () -> Unit
) {

    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    Column(
        Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            URSearchInput(
                value = searchQueryTextFieldValue,
                onValueChange = { query ->
                    if (query.text != searchQueryTextFieldValue.text) {
                        setSearchQueryTextFieldValue(query)

                        debounceJob?.cancel()
                        debounceJob = scope.launch {
                            delay(250L)
                            filterLocations(query.text)
                        }
                    }
                },
                onSearch = {
                    filterLocations(searchQueryTextFieldValue.text)
                    keyboardController?.hide()
                },
                placeholder = stringResource(id = R.string.search_placeholder),
                keyboardController = keyboardController,
                onClear = {
                    setSearchQueryTextFieldValue(TextFieldValue(""))
                    filterLocations("")
                    keyboardController?.hide()
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))


        if (fetchLocationsState == FilterLocationsState.Error) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                FetchLocationsError(
                    onRefresh = {
                        filterLocations(currentSearchQuery)
                    }
                )
            }
        } else {
            LocationsList(
                onLocationSelect = { location ->
                    connect(location)
                },
                promotedLocations = promotedLocations,
                connectCountries = connectCountries,
                cities = cities,
                regions = regions,
                bestSearchMatches = bestSearchMatches,
                getLocationColor = getLocationColor,
                selectedLocation = selectedLocation,
                devices = devices,
                onRefresh = {
                    refreshLocations()
                },
                searchQuery = currentSearchQuery,
                listState = lazyListState,
                isRefreshing = fetchLocationsState == FilterLocationsState.Loading
            )
        }
    }
}