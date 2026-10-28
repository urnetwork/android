package com.bringyour.network.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint

/**
 * The detail pane of the Account list-detail layout before a screen is
 * chosen from the list: the app's mark, quiet, in place of an empty pane.
 */
@Composable
fun AccountDetailPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.main_nav_user),
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(48.dp),
        )
    }
}
