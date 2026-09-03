package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

@Composable
fun UpgradeScreenHeader() {
    Column {

        Spacer(modifier = Modifier.height(32.dp))

        // "Get Pro", the same title the Apple sheet and the account card use;
        // this used to read "Become a URnetwork Supporter".
        Text(
            stringResource(id = R.string.get_pro),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(id = R.string.support_us),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(id = R.string.unlock_speed),
            style = MaterialTheme.typography.bodyLarge,
            color = TextMuted
        )
    }
}