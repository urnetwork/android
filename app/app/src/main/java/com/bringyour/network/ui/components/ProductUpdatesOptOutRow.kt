package com.bringyour.network.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

/**
 * The "Periodic product updates" line on every sign-up page: on by default,
 * one tap turns it off, and the choice travels with the create request
 * (NetworkCreateArgs.productUpdatesOptOut). Shown at collection so the
 * onboarding emails have the opt-out the soft opt-in rules require
 * (mmm/onboarding/PLAN.md "Compliance"); Settings keeps the same preference
 * afterwards.
 */
@Composable
fun ProductUpdatesOptOutRow(
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(id = R.string.periodic_product_updates),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        URSwitch(
            checked = checked,
            enabled = enabled,
            toggle = { onCheckedChanged(!checked) },
        )
    }
}
