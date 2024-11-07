package com.bringyour.network.ui.components

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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedLinkBottomSheet(
    scaffoldState: BottomSheetScaffoldState,
    targetLink: String?,
    defaultLocation: String?,
    confirm: () -> Unit,
    content: @Composable () -> Unit,
) {

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

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
                    stringResource(
                        id = R.string.open_nested_link,
                        targetLink ?: "",
                        defaultLocation ?: ""
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                URButton(
                    onClick = confirm
                ) { buttonTextStyle ->
                    Text(
                        "Connect and open link",
                        style = buttonTextStyle
                    )
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )
            }

        }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun NestedLinkBottomSheetPreview() {

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            SheetValue.Expanded
        )
    )

    URNetworkTheme {
        NestedLinkBottomSheet(
            scaffoldState = scaffoldState,
            targetLink = "https://ur.io/123/abcdefg",
            defaultLocation = "Germany",
            confirm = {}
        ) {
            Text("Content")
        }
    }
}