package com.bringyour.network.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

/**
 * "Account: name" under a plan (support inbox 6546): customers with several
 * accounts read the plan of one and had paid on another, and no plan said which
 * account it was on. Shown under the plan on the Account screen, the Connect plan
 * card, Settings and the purchase overlay. [name] is the sdk's
 * (Sdk.planAccountName); empty, for a guest or before a name is known, shows
 * nothing.
 */
@Composable
fun PlanAccountNameLine(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = TextMuted,
) {
    if (name.isEmpty()) {
        return
    }
    Text(
        stringResource(id = R.string.plan_account_name, name),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
