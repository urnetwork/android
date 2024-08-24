package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.Red400
import com.bringyour.network.ui.theme.TextFaint
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    connectViewModel: ConnectViewModel,
    locationsViewModel: LocationsListViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val totalProviderCount by locationsViewModel.totalProviderCount
    val connectCountries = locationsViewModel.connectCountries
    val promotedLocations = locationsViewModel.promotedLocations
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedLocation = connectViewModel.selectedLocation

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
        sheetPeekHeight = 92.dp,
        sheetDragHandle = {},
        sheetContent = {
            Box(
                modifier = Modifier
                    .requiredWidth(LocalConfiguration.current.screenWidthDp.dp + 4.dp)
                    .fillMaxHeight()
                    .offset(y = 1.dp)
                    .border(
                        1.dp,
                        MainBorderBase,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp
                        )
                    )
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(color = Black)
                        .padding(horizontal = 16.dp),
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

                    if (selectedLocation == null) {
                        ProviderRow(
                            location = "Best available provider",
                            /* todo -
                                save totalProviderCount locally
                                then on load, animate the number up or down depending on count fetched
                            */
                            providerCount = totalProviderCount,
                            onClick = {
                                // todo - query bestAvailable
                            },
                            color = Red400
                        )
                    } else {

                        val key = if (selectedLocation.countryCode.isNullOrEmpty()) selectedLocation.connectLocationId.toString()
                            else selectedLocation.countryCode

                        ProviderRow(
                            location = selectedLocation.name,
                            providerCount = selectedLocation.providerCount,
                            onClick = {},
                            color = locationsViewModel.getLocationColor(key)
                        )
                    }

                    URSearchInput(
                        value = searchQuery,
                        onValueChange = { query ->
                            if (query.text != searchQuery.text) {
                                searchQuery = query
                                locationsViewModel.filterLocations(searchQuery.text)
                            }
                                        },
                        placeholder = "Search for all locations",
                        keyboardController
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    LocationsList(
                        // connectCountries = connectCountries,
                        // connectLocations = connectLocations,
                        onLocationSelect = {
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        },
                        promotedLocations = promotedLocations,
                        connectCountries = connectCountries,
                        locationsVm = locationsViewModel,
                        connectViewModel = connectViewModel
                    )
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Preview
//@Composable
//fun PreviewConnectCountriesList(
//    @PreviewParameter(ApplicationPreviewParameterProvider::class) application: MainApplication
//) {
//    val scaffoldState = rememberBottomSheetScaffoldState()
//    val connectVc = application.connectVc
//
//    URNetworkTheme {
//        ProvidersBottomSheet(
//            scaffoldState = scaffoldState,
//            connectVc = connectVc,
//
//        ) {
//            Text("Hello world")
//        }
//    }
//}
//
//
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Preview
//@Composable
//fun ProvidersBottomSheetOpenPreview(
//    @PreviewParameter(ApplicationPreviewParameterProvider::class) application: MainApplication
//) {
//    val connectVc = application.connectVc
//
//    val scaffoldState = rememberBottomSheetScaffoldState(
//        bottomSheetState = rememberStandardBottomSheetState(
//            SheetValue.Expanded
//        )
//    )
//
//    URNetworkTheme {
//        ProvidersBottomSheet(
//            scaffoldState,
//            connectVc,
//            selectedLocation = null,
//        ) {
//            Text("Hello world")
//        }
//    }
//}