package com.bringyour.network.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.theme.TextMuted

@Composable
fun CopyReferralCode(
    bonusReferralCode: String
) {

    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0x1AFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                clipboardManager.setText(AnnotatedString(bonusReferralCode))
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,

        ) {
        Text(
            bonusReferralCode,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Icon(
            painter = painterResource(id = R.drawable.content_copy),
            contentDescription = "Copy",
            tint = TextMuted,
            modifier = Modifier.width(16.dp)
        )
    }

}