package com.bringyour.network.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun SettingsScreen() {
    Column {
        Text("Settings Screen")
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    URNetworkTheme {
        SettingsScreen()
    }
}