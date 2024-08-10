package com.bringyour.network.ui.feedback

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun FeedbackScreen() {

    Column {
        Text("Feedback Screen")
    }

}

@Preview
@Composable
fun ConnectPreview() {

    URNetworkTheme {
        FeedbackScreen()
    }

}