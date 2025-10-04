package com.bringyour.network.ui.settings

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.bringyour.network.R
import com.bringyour.network.ui.theme.BlueMedium

@Composable
fun ManageSubscriptionButton(
    stripePortalUrl: String? // ignore stripePortalUrl for ungoogle
) {

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    TextButton(
        onClick = {
            uriHandler.openUri("https://pay.ur.io/p/login/00g16I4Mag2O240aEE")
        }
    ) {
        Text(
            stringResource(id = R.string.manage_subscription),
            color = BlueMedium
        )
    }
}