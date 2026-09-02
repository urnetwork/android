package com.bringyour.network.ui.wallet

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bringyour.network.R
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.ReferralGold
import com.bringyour.network.ui.theme.ReferralGoldInk
import com.bringyour.network.ui.theme.ReferralGoldLight
import com.bringyour.network.ui.theme.ReferralGoldPale
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.URNetworkTheme

// the ur.io route that walks a qualifying network through claiming a head spot
const val TOP200_URL = "https://ur.io/app/account/top200"

/**
 * Gold tile shown when the validators' consensus puts this network inside the head
 * miner cutoff and no hotkey is bound yet.
 */
@Composable
fun Top200Tile(
    head: SnHeadState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = ReferralGold.copy(alpha = 0.5f),
                spotColor = ReferralGold.copy(alpha = 0.5f)
            )
            .clip(shape)
            .drawBehind {
                drawRect(Black)
                drawRect(ReferralGold.copy(alpha = 0.05f))
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ReferralGold.copy(alpha = 0.22f),
                            ReferralGold.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.1f, 0f),
                        radius = size.width * 1.1f
                    )
                )
            }
            .border(1.dp, ReferralGold.copy(alpha = 0.6f), shape)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(id = R.string.top200).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ReferralGold
                )
                Text(
                    stringResource(id = R.string.top200_you_qualify),
                    style = MaterialTheme.typography.labelSmall,
                    color = ReferralGoldLight
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                stringResource(
                    id = R.string.top200_detail,
                    head.rankEstimate,
                    head.cutoff
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            GoldPill(
                text = stringResource(id = R.string.claim_your_spot),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, TOP200_URL.toUri()))
                }
            )
        }
    }
}

/** The bound state: the network holds a head spot. */
@Composable
fun Top200BoundRow(
    head: SnHeadState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, TOP200_URL.toUri()))
            }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(id = R.string.top200),
                style = MaterialTheme.typography.bodyLarge,
                color = ReferralGold
            )
            Text(
                stringResource(id = R.string.top200_bound_status, head.uid, head.rank),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(id = R.string.top200_bound_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        if (head.nearFloor) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.top200_demotion_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = Amber
            )
        }
    }
}

@Composable
private fun GoldPill(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(
                Brush.verticalGradient(listOf(ReferralGoldPale, ReferralGold))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = ReferralGoldInk
        )
    }
}

@Preview
@Composable
private fun Top200TilePreview() {
    URNetworkTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Top200Tile(
                head = SnHeadState(
                    eligible = true, score = 812.0, floor = 640.0, rankEstimate = 143, cutoff = 200,
                    bound = false, hotkey = null, uid = 0, rank = 0, epoch = 1219, source = "server"
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Top200BoundRow(
                head = SnHeadState(
                    eligible = true, score = 660.0, floor = 640.0, rankEstimate = 180, cutoff = 200,
                    bound = true, hotkey = "5G…", uid = 57, rank = 180, epoch = 1219, source = "chain"
                )
            )
            Spacer(modifier = Modifier.width(1.dp))
        }
    }
}
