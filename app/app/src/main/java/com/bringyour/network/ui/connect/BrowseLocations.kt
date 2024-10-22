package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.bringyour.client.ConnectLocation
import com.bringyour.network.R
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.Black
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
                },
                placeholder = stringResource(id = R.string.search_placeholder),
                keyboardController = keyboardController,
                onClear = {
                    setSearchQueryTextFieldValue(TextFieldValue(""))
                    filterLocations("")
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        when(fetchLocationsState) {
            FilterLocationsState.Loading -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(color = Black)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            FilterLocationsState.Loaded -> {

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
                        filterLocations(currentSearchQuery)
                    },
                    searchQuery = currentSearchQuery,
                )
            }
            FilterLocationsState.Error -> {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    FetchLocationsError(
                        onRefresh = {
                            filterLocations(currentSearchQuery)
                        }
                    )
                }
            }
        }
    }
}