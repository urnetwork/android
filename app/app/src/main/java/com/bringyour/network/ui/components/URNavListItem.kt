package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.Pink
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

@Composable
fun URNavListItem(
    iconResourceId: Int,
    text: String,
    onClick: () -> Unit,
    actionIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    // the row whose screen is open beside the list (a list-detail layout)
    selected: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (selected) {
                    Modifier
                        .background(MainTintedBackgroundBase)
                        .border(1.dp, Pink.copy(alpha = 0.35f))
                        .semantics { this.selected = true }
                } else {
                    Modifier
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            Icon(
                painterResource(id = iconResourceId),
                contentDescription = null,
                tint = TextMuted
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text)
        }
        Icon(
            imageVector = actionIcon,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

@Preview
@Composable
fun URNavListItemPreview() {
    URNetworkTheme {
        URNavListItem(
            iconResourceId = R.drawable.nav_list_item_user,
            text = "Profile",
            onClick = {}
        )
    }
}