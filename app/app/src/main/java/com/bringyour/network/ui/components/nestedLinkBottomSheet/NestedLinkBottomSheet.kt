package com.bringyour.network.ui.components.nestedLinkBottomSheet

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bringyour.network.R
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.connect.ConnectStatus
import com.bringyour.network.ui.connect.ConnectViewModel
import com.bringyour.network.ui.connect.FilterLocationsState
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch
import kotlin.text.isLowerCase
import kotlin.text.replaceFirstChar
import kotlin.text.split
import kotlin.text.titlecase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedLinkBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    targetLink: String?,
    defaultLocation: String?,
    connectViewModel: ConnectViewModel,
    nestedLinkBottomSheetViewModel: NestedLinkBottomSheetViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {

    val connectStatus by connectViewModel.connectStatus.collectAsState()
    val fetchLocationsState by remember { nestedLinkBottomSheetViewModel.filterLocationsState }.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = defaultLocation) {
        if (!defaultLocation.isNullOrEmpty()) {
            nestedLinkBottomSheetViewModel.filterLocations(defaultLocation)
        }
    }

    LaunchedEffect(key1 = connectStatus) {
        if (connectStatus == ConnectStatus.CONNECTED && scaffoldState.bottomSheetState.isVisible) {
            scaffoldState.bottomSheetState.hide()

            if (targetLink != null) {
                uriHandler.openUri(targetLink ?: "")
            }
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetShape = RoundedCornerShape(
            0.dp,
        ),
        sheetContainerColor = Black,
        sheetContentColor = Black,
        sheetPeekHeight = 0.dp,
        sheetDragHandle = {},
        sheetContent = {

            NestedLinkSheetContent(
                targetLink = targetLink,
                defaultLocation = defaultLocation,
                confirm = {
                    if (nestedLinkBottomSheetViewModel.searchLocationResults.isNotEmpty()) {
                        connectViewModel.connect(nestedLinkBottomSheetViewModel.searchLocationResults[0])
                    }
                },
                connectStatus = connectStatus,
                searchLocationsCount = nestedLinkBottomSheetViewModel.searchLocationResults.size,
                dismiss = {
                    scope.launch {
                        scaffoldState.bottomSheetState.hide()
                    }
                },
                locationsState = fetchLocationsState
            )

        }
    ) {
        content()
    }
}

@Composable
private fun NestedLinkSheetContent(
    targetLink: String?,
    defaultLocation: String?,
    confirm: () -> Unit,
    dismiss: () -> Unit,
    connectStatus: ConnectStatus,
    searchLocationsCount: Int,
    locationsState: FilterLocationsState
) {

    val noLocationsFound = locationsState == FilterLocationsState.Loaded && searchLocationsCount <= 0

    val headerText = if (noLocationsFound) stringResource(id = R.string.no_locations_found)
        else if (targetLink.isNullOrEmpty()) stringResource(id = R.string.connect)
            else stringResource(id = R.string.connect_and_open)

    val detailResourceId = if (noLocationsFound) R.string.no_locations_found_detail else if (targetLink.isNullOrEmpty()) R.string.connect_to_location else R.string.open_nested_link

    val actionTextResourceId = if (targetLink.isNullOrEmpty()) R.string.connect
        else R.string.connect_and_open
    
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp


    val titlecasedLocation = defaultLocation?.split(" ")
        ?.joinToString(" ") { it.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

    Column(
        modifier = Modifier
            .then(
                if (screenWidth > 640.dp) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                        .requiredWidth(LocalConfiguration.current.screenWidthDp.dp + 4.dp)
                }
            )
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
            .padding(
                horizontal = 16.dp,
            )
    ) {

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            headerText,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(
                id = detailResourceId,
                titlecasedLocation ?: "",
                targetLink ?: "",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(
            modifier = Modifier.height(48.dp)
        )

        if (noLocationsFound) {
            URButton(
                onClick = dismiss,
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = R.string.dismiss),
                    style = buttonTextStyle
                )
            }
        } else {
            URButton(
                onClick = confirm,
                enabled = connectStatus == ConnectStatus.DISCONNECTED && searchLocationsCount > 0
            ) { buttonTextStyle ->
                Text(
                    stringResource(id = actionTextResourceId),
                    style = buttonTextStyle
                )
            }
        }

        Spacer(
            modifier = Modifier.height(32.dp)
        )
    }
}

@Preview
@Composable
private fun NestedLinkSheetPreview() {

    URNetworkTheme {
        NestedLinkSheetContent(
            targetLink = "https://ur.io/123/abcdefg",
            defaultLocation = "Germany",
            confirm = {},
            connectStatus = ConnectStatus.DISCONNECTED,
            searchLocationsCount = 1,
            locationsState = FilterLocationsState.Loaded,
            dismiss = {}
        )
    }
}

@Preview
@Composable
private fun NestedLinkSheetNoLocationsPreview() {

    URNetworkTheme {
        NestedLinkSheetContent(
            targetLink = "https://ur.io/123/abcdefg",
            defaultLocation = "Lorem Ipsum",
            confirm = {},
            connectStatus = ConnectStatus.DISCONNECTED,
            searchLocationsCount = 0,
            locationsState = FilterLocationsState.Loaded,
            dismiss = {}
        )
    }
}

@Preview
@Composable
private fun NestedLinkSheetNoLinkPreview() {

    URNetworkTheme {
        NestedLinkSheetContent(
            targetLink = null,
            defaultLocation = "California",
            confirm = {},
            connectStatus = ConnectStatus.DISCONNECTED,
            searchLocationsCount = 1,
            locationsState = FilterLocationsState.Loaded,
            dismiss = {}
        )
    }
}