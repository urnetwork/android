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
    stripePortalUrl: String?
) {

    val context = LocalContext.current

    TextButton(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, stripePortalUrl?.toUri())
            context.startActivity(intent)
        }
    ) {
        Text(
            stringResource(id = R.string.manage_subscription),
            color = BlueMedium
        )
    }
}