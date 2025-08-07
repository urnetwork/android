package com.bringyour.network.ui.blocked_regions

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.CircleImage
import com.bringyour.network.ui.components.URSearchInput
import com.bringyour.network.ui.theme.Black
import com.bringyour.sdk.ConnectLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlockedLocationSheet(
    sheetState: SheetState,
    dismiss: () -> Unit,
    countries: List<ConnectLocation>,
    onSelect: (ConnectLocation) -> Unit,
    getLocationColor: (String) -> Color,
    viewModel: AddBlockedLocationViewModel = hiltViewModel()
) {

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchTextField by viewModel.searchQueryTextFieldValue.collectAsState()
    val filteredCountries by viewModel.filteredCountries.collectAsState()

    LaunchedEffect(countries) {
        viewModel.initCountries(countries)
    }

    ModalBottomSheet(
        onDismissRequest = {
            dismiss()
            viewModel.updateSearchQuery(TextFieldValue(""))
                           },
        sheetState = sheetState,

        modifier = Modifier
            .padding(
                top = WindowInsets.systemBars
                    .asPaddingValues()
                    .calculateTopPadding()
            ),
        containerColor = Black
    ) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState
        ) {

            item {
                Column {

                    Row (
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        URSearchInput(
                            value = searchTextField,
                            onValueChange = { query ->
                                if (query.text != searchTextField.text) {
                                    viewModel.updateSearchQuery(query)
                                    viewModel.filterLocations(query.text)
                                }
                            },
                            onSearch = {
                                viewModel.filterLocations(searchTextField.text)
                                keyboardController?.hide()
                            },
                            placeholder = stringResource(id = R.string.search_placeholder),
                            keyboardController = keyboardController,
                            onClear = {
                                viewModel.updateSearchQuery(TextFieldValue(""))
                                viewModel.filterLocations("")
                                keyboardController?.hide()
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                }

            }


            items(filteredCountries, key = { it.connectLocationId.locationId.idStr }) { location ->

                Column {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable {
                                onSelect(location)
                                dismiss()
                                viewModel.updateSearchQuery(TextFieldValue(""))
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        CircleImage(
                            size = 40.dp,
                            imageResourceId = null,
                            backgroundColor = getLocationColor(location.countryCode),
                        )
                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            location.name,
                            style = MaterialTheme.typography.bodyLarge,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )

                    }

                    HorizontalDivider()

                }

            }

        }
    }

}