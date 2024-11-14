package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.client.ConnectLocation
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    selectedLocation: ConnectLocation?,
    connect: (ConnectLocation?) -> Unit,
    locationsViewModel: LocationsListViewModel,
    content: @Composable (PaddingValues) -> Unit,
) {

    val fetchLocationsState by remember { locationsViewModel.filterLocationsState }.collectAsState()

    ProvidersBottomSheet(
        scaffoldState = scaffoldState,
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
    ) { innerPadding ->
        content(innerPadding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
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
    content: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            keyboardController?.hide()
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 94.dp,
        sheetDragHandle = {},
        sheetContent = {

            BottomSheetContentContainer {

                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = TextFaint,
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Box(
                            Modifier
                                .size(
                                    width = 48.dp,
                                    height = 4.dp
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
                            ProviderRow(
                                location = "Best available provider",
                                onClick = {
                                    // passing null for connect location will connect to best available
                                    connect(null)
                                },
                                color = Red400
                            )
                        } else {

                            val key =
                                if (selectedLocation.countryCode.isNullOrEmpty()) selectedLocation.connectLocationId.toString()
                                else selectedLocation.countryCode

                            ProviderRow(
                                location = selectedLocation.name,
                                providerCount = selectedLocation.providerCount,
                                onClick = {
                                    connect(selectedLocation)
                                },
                                color = getLocationColor(key)
                            )
                        }
                    }

                    BrowseLocations(
                        connectCountries = connectCountries,
                        promotedLocations = promotedLocations,
                        cities = cities,
                        regions = regions,
                        devices = devices,
                        bestSearchMatches = bestSearchMatches,
                        selectedLocation = selectedLocation,
                        keyboardController = keyboardController,
                        currentSearchQuery = currentSearchQuery,
                        setSearchQueryTextFieldValue = setSearchQueryTextFieldValue,
                        searchQueryTextFieldValue = searchQueryTextFieldValue,
                        fetchLocationsState = fetchLocationsState,
                        connect = { location ->
                            connect(location)
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                  },
                        getLocationColor = getLocationColor,
                        filterLocations = filterLocations,
                    )

//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .height(24.dp)
//                            .background(Color.Red)
//                    ) {
//                        Text("hi")
//                    }
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PreviewBottomSheet() {
    val scaffoldState = rememberBottomSheetScaffoldState()

    URNetworkTheme {
        ProvidersBottomSheet(
            scaffoldState = scaffoldState,
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
            currentSearchQuery = ""
        ) {
            Text("Hello world")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun PreviewBottomSheetExpanded() {

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )

    URNetworkTheme {
        ProvidersBottomSheet(
            scaffoldState = scaffoldState,
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
            currentSearchQuery = ""
        ) {
            Text("Hello world")
        }
    }
}
