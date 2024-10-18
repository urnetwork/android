package com.bringyour.network.ui.connect

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Red400
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.client.ConnectLocation
import com.bringyour.network.R
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    selectedLocation: ConnectLocation?,
    connect: (ConnectLocation?) -> Unit,
    locationsViewModel: LocationsListViewModel = hiltViewModel(),
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
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    var peekInFocus by remember { mutableStateOf(false) }

    // val focusManager = LocalFocusManager.current

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
                    Modifier
                        .fillMaxSize()
                        .background(color = Black)
                        .focusGroup()
//                        .onKeyEvent { keyEvent ->
//                            if (keyEvent.type == KeyEventType.KeyUp) {
//                                when (keyEvent.key) {
//                                    Key.DirectionUp -> {
//                                        Log.i("KeyEvent", "D-pad Up released")
//                                        focusManager.moveFocus(FocusDirection.Up)
//                                        // Handle D-pad up key release
//                                        true
//                                    }
//                                    Key.DirectionDown -> {
//                                        Log.i("KeyEvent", "D-pad Down released")
//                                        focusManager.moveFocus(FocusDirection.Down)
//                                        // Handle D-pad down key release
//                                        true
//                                    }
//                                    Key.DirectionLeft -> {
//                                        Log.i("KeyEvent", "D-pad Left released")
//                                        // Handle D-pad left key release
//                                        true
//                                    }
//                                    Key.DirectionRight -> {
//                                        Log.i("KeyEvent", "D-pad Right released")
//                                        // Handle D-pad right key release
//                                        true
//                                    }
//                                    else -> false
//                                }
//                            } else {
//                                false
//                            }
//                        },
                        .onFocusChanged {
                            scope.launch {

//                                Log.i("ProvidersBottomSheet", "onFocusChanged: has focus? ${it.hasFocus}")
//                                Log.i("ProvidersBottomSheet", "onFocusChanged: scaffoldState.bottomSheetState.currentValue? ${scaffoldState.bottomSheetState.currentValue}")
//
//                                if (it.hasFocus && scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded) {
//                                    scaffoldState.bottomSheetState.expand()
//                                    peekInFocus = true
//                                }

                            }
                        },
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
                            .onFocusChanged {
                                Log.i("ProvidersBottomSheet", "peek onFocusChanged: has focus? ${it.hasFocus}")
                                peekInFocus = it.isFocused
                                if (it.isFocused) {
                                    scope.launch { scaffoldState.bottomSheetState.expand() }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (peekInFocus && keyEvent.key == Key.DirectionUp && keyEvent.type == KeyEventType.KeyDown) {
                                    Log.i("ProvidersBottomSheet", "CLOSE NOW")
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                    peekInFocus = false
                                    true
                                } else {
                                    false
                                }
                            }
                            .padding(bottom = 16.dp)
                    ) {
                        if (selectedLocation == null || selectedLocation.connectLocationId.bestAvailable) {
                            ProviderRow(
                                location = "Best available provider",
                                onClick = {
                                    // passing null for connect location will connect to best available
                                    connect(null)
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
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
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                },
                                color = getLocationColor(key)
                            )
                        }
                    }

                    // Spacer(modifier = Modifier.height(8.dp))

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
                            onFocusChanged = {
                                Log.i("SearchInput", "onFocusChanged")
                                // scope.launch { scaffoldState.bottomSheetState.expand() }
                            }
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
                                    scope.launch { scaffoldState.bottomSheetState.partialExpand() }
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
                                onFocusChanged = {
                                    Log.i("ProvidersBottomSheet", "caught a provider row change")
                                    scope.launch { scaffoldState.bottomSheetState.expand() }
                                }
                            )
                        }
                        FilterLocationsState.Error -> {
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
