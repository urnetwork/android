package com.bringyour.network.ui.refer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun ReferScreen() {
    Column {
        Text("Refer Screen")
    }
}

@Preview
@Composable
fun ReferScreenPreview() {
    URNetworkTheme {
        ReferScreen()
    }
}