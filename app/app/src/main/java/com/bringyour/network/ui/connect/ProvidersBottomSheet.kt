package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.R
import com.bringyour.sdk.ConnectLocation
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    sheetState: SheetState,
    selectedLocation: ConnectLocation?,
    connect: (ConnectLocation?) -> Unit,
    locationsViewModel: LocationsListViewModel,
    setIsPresented: (Boolean) -> Unit,
) {

    val fetchLocationsState by remember { locationsViewModel.filterLocationsState }.collectAsState()

    ProvidersBottomSheet(
        sheetState = sheetState,
        selectedLocation = selectedLocation,
        connectCountries = locationsViewModel.connectCountries,
        promotedLocations = locationsViewModel.promotedLocations,
        cities = locationsViewModel.cities,
        regions = locationsViewModel.regions,
        devices = locationsViewModel.devices,
        bestSearchMatches = locationsViewModel.bestSearchMatches,
        getLocationColor = locationsViewModel.getLocationColor,
        filterLocations = locationsViewModel.filterLocations,
        fetchLocationsState = fetchLocationsState,
        searchQueryTextFieldValue = locationsViewModel.searchQueryTextFieldValue,
        setSearchQueryTextFieldValue = locationsViewModel.setSearchQueryTextFieldValue,
        currentSearchQuery = locationsViewModel.currentSearchQuery,
        connect = connect,
        refreshLocations = locationsViewModel.refreshLocations,
        setIsPresented = setIsPresented
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    sheetState: SheetState,
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
    refreshLocations: () -> Unit,
    setIsPresented: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // hide keyboard when sheet is being closed
    LaunchedEffect(sheetState.currentValue) {
        if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
            keyboardController?.hide()
        }
    }

    val lazyListState = rememberLazyListState()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    ModalBottomSheet(
        onDismissRequest = { setIsPresented(false) },
        sheetState = sheetState,
        modifier = Modifier
            .padding(
                top = WindowInsets.systemBars
                    .asPaddingValues()
                    .calculateTopPadding()
            ),
        containerColor = Black
    ) {


        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {

            Column(
                Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Row(
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

            }



            when (fetchLocationsState) {
                FilterLocationsState.Loaded,
                FilterLocationsState.Loading -> {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {

                        LocationsList(
                            onLocationSelect = { location ->
                                connect(location)

                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) {
                                        setIsPresented(false)
                                    }
                                }
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
                            listState = lazyListState
                        )

                        if (fetchLocationsState == FilterLocationsState.Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Black.copy(alpha = 0.5f))
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(24.dp)
                                        .align(Alignment.Center),
                                )
                            }
                        }

                    }

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

}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PreviewBottomSheet() {
    val sheetState = rememberStandardBottomSheetState()

    URNetworkTheme {
        ProvidersBottomSheet(
            sheetState = sheetState,
            connect = {},
            selectedLocation = null,
            connectCountries = listOf<ConnectLocation>(),
            promotedLocations = listOf<ConnectLocation>(),
            cities = listOf<ConnectLocation>(),
            regions = listOf<ConnectLocation>(),
            devices = listOf<ConnectLocation>(),
            bestSearchMatches = listOf<ConnectLocation>(),
            getLocationColor = { _ ->
                BlueMedium
            },
            filterLocations = { _ -> },
            fetchLocationsState = FilterLocationsState.Loaded,
            searchQueryTextFieldValue = TextFieldValue(""),
            setSearchQueryTextFieldValue = {},
            currentSearchQuery = "",
            refreshLocations = {},
            setIsPresented = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PreviewBottomSheetExpanded() {

//    val scaffoldState = rememberBottomSheetScaffoldState(
//        bottomSheetState = rememberStandardBottomSheetState(
//            SheetValue.Expanded
//        )
//    )

    val sheetState = rememberStandardBottomSheetState()

    URNetworkTheme {
        ProvidersBottomSheet(
            sheetState = sheetState,
            connect = {},
            selectedLocation = null,
            connectCountries = listOf<ConnectLocation>(),
            promotedLocations = listOf<ConnectLocation>(),
            cities = listOf<ConnectLocation>(),
            regions = listOf<ConnectLocation>(),
            devices = listOf<ConnectLocation>(),
            bestSearchMatches = listOf<ConnectLocation>(),
            getLocationColor = { _ ->
                BlueMedium
            },
            filterLocations = { _ -> },
            fetchLocationsState = FilterLocationsState.Loaded,
            searchQueryTextFieldValue = TextFieldValue(""),
            setSearchQueryTextFieldValue = {},
            currentSearchQuery = "",
            refreshLocations = {},
            setIsPresented = {}
        )
    }
}
