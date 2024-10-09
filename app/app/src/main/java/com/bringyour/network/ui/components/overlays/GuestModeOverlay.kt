package com.bringyour.network.ui.components.overlays

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.LoginActivity
import com.bringyour.network.MainApplication
import com.bringyour.network.R
import com.bringyour.network.ui.components.ButtonStyle
import com.bringyour.network.ui.components.URButton
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.Blue200
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun GuestModeOverlay(
    onDismiss: () -> Unit
) {

    val context = LocalContext.current
    val application = context.applicationContext as? MainApplication

    OverlayBackground(
        onDismiss = { onDismiss() },
        bgImageResourceId = R.drawable.overlay_guest_mode_bg
    ) {
        Box(modifier = Modifier
            .background(
                Blue200,
                shape = RoundedCornerShape(12.dp)
            )
            .fillMaxWidth()
            .padding(24.dp)
        ) {
            Column() {

                Icon(painter = painterResource(id = R.drawable.globe_filled), contentDescription = "URnetwork globe filled")

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    stringResource(id = R.string.in_guest_mode),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Black
                )

                Text(
                    stringResource(id = R.string.start_earning_join),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Black
                )

                Spacer(modifier = Modifier.height(128.dp))

                URButton(
                    onClick = {

                        application?.logout()

                        onDismiss()

                        val intent = Intent(context, LoginActivity::class.java)
                        context.startActivity(intent)

                        (context as? Activity)?.finish()

                    },
                    style = ButtonStyle.OUTLINE
                ) { buttonTextStyle ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(id = R.string.create_account), style = buttonTextStyle, color = Black)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun GuestModeOverlayPreview() {
    URNetworkTheme {
        GuestModeOverlay(
            onDismiss = {}
        )
    }
}