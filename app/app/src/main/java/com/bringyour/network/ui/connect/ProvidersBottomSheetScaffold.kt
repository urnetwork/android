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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainBorderBase
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.URNetworkTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersBottomSheetScaffold(
    content: @Composable (PaddingValues) -> Unit,
) {

    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()

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
                        // .padding(bottom = 2.dp),
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

                    ProviderRow(
                        location = "Best available provider",
                        providerCount = 1520,
                        onClick = {}
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Sheet content")
                    Button(
                        modifier = Modifier.padding(bottom = 64.dp),
                        onClick = { scope.launch { scaffoldState.bottomSheetState.partialExpand() } }
                    ) {
                        Text("Click to collapse sheet")
                    }
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

@Preview
@Composable
fun ProvidersBottomSheetScaffoldPreview() {
    URNetworkTheme {
        ProvidersBottomSheetScaffold() {
            Text("Hello world")
        }
    }
}