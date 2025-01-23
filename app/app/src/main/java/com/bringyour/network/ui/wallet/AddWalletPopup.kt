package com.bringyour.network.ui.wallet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted

@Composable()
fun AddWallet(
    openLinkWalletSheet: () -> Unit,
    connectSagaWallet: () -> Unit,
) {
    val isSaga = Build.MODEL.equals("SAGA", ignoreCase = true)

    Box() {
        IconButton(
            onClick = {
                if (isSaga) {
                    connectSagaWallet()
                } else {
                    openLinkWalletSheet()
                }
            },
            modifier = Modifier
                .background(
                    color = MainTintedBackgroundBase,
                    shape = CircleShape
                )
                .width(26.dp)
                .height(26.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.plus_icon),
                contentDescription = stringResource(id = R.string.add_wallet),
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
