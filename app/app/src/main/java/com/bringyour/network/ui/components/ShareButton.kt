package com.bringyour.network.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R

@Composable
fun ShareButton(
    referralLink: String
) {

    val context = LocalContext.current

    URButton(onClick = {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, referralLink)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }) { textStyle ->
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.share),
                style = textStyle
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.icon_share),
                contentDescription = "Share Icon",
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }

}