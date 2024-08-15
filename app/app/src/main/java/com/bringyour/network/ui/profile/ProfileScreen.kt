package com.bringyour.network.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun ProfileScreen() {
    Column {
        Text("Profile Screen")
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    URNetworkTheme {
        ProfileScreen()
    }
}