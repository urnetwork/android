package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.bringyour.network.ui.components.BottomSheetContentContainer
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        refreshLocations = locationsViewModel.refreshLocations
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
    refreshLocations: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
        if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
            keyboardController?.hide()
            refreshLocations()
        }

        if (scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
            refreshLocations()
        }
    }

    val lazyListState = rememberLazyListState()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    val isListAtTop = remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    var dragOffset by remember { mutableFloatStateOf(0f) }
    val maxDragOffset = 500f

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 102.dp,
        sheetDragHandle = {},
        sheetSwipeEnabled = isListAtTop.value,
        sheetContent = {

            BottomSheetContentContainer {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    dragOffset += delta
                                },
                                onDragStopped = {
                                    scope.launch {
                                        if (dragOffset > maxDragOffset) {
                                            scaffoldState.bottomSheetState.partialExpand()
                                        } else {
                                            scaffoldState.bottomSheetState.expand()
                                        }
                                        dragOffset = 0f
                                    }
                                }
                            )
                        ,
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

                        Column(
                            Modifier
                                .fillMaxWidth(),
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

                        }

                    }

                    when(fetchLocationsState) {
                        FilterLocationsState.Loaded,
                        FilterLocationsState.Loading -> {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {

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
            currentSearchQuery = "",
            refreshLocations = {}
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
            currentSearchQuery = "",
            refreshLocations = {}
        ) {
            Text("Hello world")
        }
    }
}
