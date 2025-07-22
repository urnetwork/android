package com.bringyour.network.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R

@Composable
fun PromptSolanaDAppStoreReview(
    promptReview: () -> Unit,
    dismiss: () -> Unit
) {

    AlertDialog(
        title = {
            Text(text = stringResource(id = R.string.enjoying_urnetwork))
        },
        text = {
            Text(text = stringResource(id = R.string.review_our_app))
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
                Text(stringResource(id = R.string.yes))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    dismiss()
                }
            ) {
                Text(stringResource(id = R.string.no))
            }
        }
    )

}