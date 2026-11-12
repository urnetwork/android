package com.bringyour.network.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ShownPlan
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/**
 * The plan's name on a plan card (Account, Connect, Settings): Pro or Free as
 * the sdk decided (ShownPlan), a spinner while the first balance read is on
 * its way, and the read's error with a retry once it failed. Never "Free"
 * before the server said so (support inbox 4074). The upgrade affordance
 * beside it shows only for [ShownPlan.sellsPro].
 */
@Composable
fun ShownPlanName(
    shownPlan: ShownPlan,
    retryPlan: () -> Unit,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
    // a Pro network's plan label replays the Pro celebration
    onPlanLabelTap: (() -> Unit)? = null,
) {
    when (shownPlan) {
        ShownPlan.Loading -> CircularProgressIndicator(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp),
            color = TextMuted,
            trackColor = TextFaint,
            strokeWidth = 2.dp
        )
        ShownPlan.Unreachable -> SectionLoadError(onRetry = retryPlan)
        ShownPlan.Pro -> Text(
            stringResource(id = R.string.supporter),
            style = style,
            modifier = onPlanLabelTap?.let { tap ->
                Modifier.clickable(role = Role.Button) { tap() }
            } ?: Modifier
        )
        ShownPlan.Free -> Text(
            stringResource(id = R.string.free),
            style = style,
        )
    }
}
