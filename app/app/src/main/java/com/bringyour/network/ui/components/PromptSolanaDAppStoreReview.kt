package com.bringyour.network.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun PromptSolanaDAppStoreReview(
    promptReview: () -> Unit,
    dismiss: () -> Unit
) {

    AlertDialog(
        title = {
            Text(text = "Enjoying URnetwork?")
        },
        text = {
            Text(text = "Would you like to review our app?")
        },
        onDismissRequest = {
            dismiss()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    promptReview()
                }
            ) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismiss()
                }
            ) {
                Text("No")
            }
        }
    )

}