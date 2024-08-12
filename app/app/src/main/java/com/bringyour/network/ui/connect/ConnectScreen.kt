package com.bringyour.network.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun ConnectScreen() {

    ProvidersBottomSheetScaffold() { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Scaffold Content")
        }
    }
}

@Preview
@Composable
fun ConnectPreview() {

    URNetworkTheme {
        ConnectScreen()
    }

}