package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {

    if (visible) {
        Dialog(onDismissRequest = { onDismiss() }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 15.dp,
                        spotColor = Color(0x33000000),
                        ambientColor = Color(0x33000000)
                    )
                    .shadow(
                        elevation = 46.dp,
                        spotColor = Color(0x1F000000),
                        ambientColor = Color(0x1F000000)
                    )
                    .shadow(
                        elevation = 38.dp,
                        spotColor = Color(0x24000000),
                        ambientColor = Color(0x24000000)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .background(color = Color(0xFF121212))
                        .background(color = Color(0x29FFFFFF))
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    content()
                }
            }
        }
    }

}

@Preview(showBackground = true)
@Composable
private fun URDialogPreview() {
    URNetworkTheme {
        Scaffold() { innerPadding ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                URDialog(
                    visible = true,
                    onDismiss = {}
                ) {
                    Column() {
                        Text(
                            "Connect External Wallet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "USDC addresses on Solana and Polygon are currently supported.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        URTextInput(
                            value = TextFieldValue(""),
                            onValueChange = {},
                            label = "Wallet Address",
                            placeholder = "Copy and paste here"
                        )
                    }
                }
            }
        }
    }
}