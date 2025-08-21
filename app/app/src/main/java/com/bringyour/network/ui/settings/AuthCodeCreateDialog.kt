package com.bringyour.network.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextFaint

@Composable
fun AuthCodeCreateDialog(
    onDismissRequest: () -> Unit,
    copyAuthCode: () -> Unit,
    authCode: String?
) {

    AlertDialog(
        icon = {
            Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(id = R.string.auth_code_created))
        },
        title = {
            Text(text = stringResource(id = R.string.auth_code_created))
        },
        text = {

            if (authCode != null) {
                Text(
                    text = authCode,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(
                            TextFaint,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.width(16.dp)
                )

            }
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyAuthCode()
                },
                enabled = (authCode != null)
            ) {
                Text(stringResource(id = R.string.copy_auth_code))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(stringResource(id = R.string.dismiss))
            }
        }
    )

}