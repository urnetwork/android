package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.ProGold
import com.bringyour.network.ui.theme.TextMuted

/**
 * A selectable plan card. The recommended plan (`glow`) wears the Pro-gold
 * dress (GoldPlanDress.kt) so it is the first thing the eye lands on. A card
 * that is not `enabled` (a plan the chosen payment method does not sell) is
 * greyed and can't be selected.
 */
@Composable
fun PlanOptionContainer(
    isSelected: Boolean,
    select: () -> Unit,
    content: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
    glow: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = if (glow) ProGold else Pink
    val dress = if (glow) rememberGoldDress() else null

    // use the box for the badge alignment
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_PLAN_ALPHA)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (dress != null) {
                        Modifier.goldPlanDress(dress = dress, selected = isSelected)
                    } else {
                        Modifier.border(
                            width = 2.dp,
                            color = if (isSelected) accent else TextMuted,
                            shape = shape
                        )
                    }
                )
                .clip(shape)
                .clickable(enabled = enabled) {
                    select()
                }
                // taller than wide: the plan lines get room to breathe
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent else Color.Transparent)
                    .border(width = 2.dp, color = if (isSelected) accent else TextMuted, shape = RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(8.dp))

            content()
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-16).dp, x = (-12).dp)
            ) {
                badge()
            }
        }

    }

}

// a plan card the chosen payment method does not sell
private const val DISABLED_PLAN_ALPHA = 0.4f
