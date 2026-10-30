package com.bringyour.network.ui.wallet

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.bringyour.network.R
import com.bringyour.network.ui.leaderboard.ProtocolFleet
import com.bringyour.network.ui.leaderboard.ProtocolLabel
import com.bringyour.network.ui.leaderboard.ProtocolLabelChip
import com.bringyour.network.ui.leaderboard.ProtocolSelectedChip
import com.bringyour.network.ui.leaderboard.ProtocolText
import com.bringyour.network.ui.leaderboard.protocolPositionText
import com.bringyour.network.ui.leaderboard.protocolUidText
import com.bringyour.network.ui.leaderboard.protocolValidatorRankText
import com.bringyour.network.ui.leaderboard.rememberProtocolText
import com.bringyour.network.ui.theme.Amber
import com.bringyour.network.ui.theme.BlueLight
import com.bringyour.network.ui.theme.BlueMedium
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainBorderBase
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
 * The Top 200 tile (apple/LEADERTOP.md §8) for a network projected inside the
 * head seats that has not claimed: its projected position, margin and label,
 * as on the Protocol tab. With binding open, the existing claim flow: Claim
 * your spot on ur.io and Sign fleet binding, with the warning every claim
 * prompt carries. While binding is closed, the "Claiming opens soon" note in
 * their place and nothing that invites registering early (Q16).
 */
@Composable
fun Top200Tile(
    state: Top200TileState.Projected,
    modifier: Modifier = Modifier,
    onSignFleetBinding: () -> Unit = {},
    onSeeBoard: () -> Unit = {},
) {
    val context = LocalContext.current
    val text = rememberProtocolText()
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
            Top200Heading(label = state.label)

            Spacer(modifier = Modifier.height(10.dp))

            Top200Position(position = state.position, margin = state.margin, text = text)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                stringResource(id = R.string.earnings_top200_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.bindingOpen) {
                GoldPill(
                    text = stringResource(id = R.string.claim_your_spot),
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, TOP200_URL.toUri()))
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SignFleetBindingButton(onClick = onSignFleetBinding)

                if (state.borderline) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(id = R.string.leaderboard_protocol_claim_borderline),
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(id = R.string.leaderboard_protocol_claim_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                ClaimingSoonNote()
            }

            Spacer(modifier = Modifier.height(4.dp))

            SeeBoardLink(onClick = onSeeBoard)
        }
    }
}

/**
 * The claimed state: the network holds head spots. Each fleet's UID with the
 * validator's rank and selection (as in the leaderboard's detail sheet), the
 * eviction warning when the network is close to the line, and Sign fleet
 * binding once binding is open: bindings renew per epoch window and new
 * devices join the fleet.
 */
@Composable
fun Top200BoundRow(
    state: Top200TileState.Claimed,
    modifier: Modifier = Modifier,
    onSignFleetBinding: () -> Unit = {},
    onSeeBoard: () -> Unit = {},
) {
    val text = rememberProtocolText()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MainTintedBackgroundBase, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Top200Heading(label = state.label)

        Spacer(modifier = Modifier.height(8.dp))

        Top200Position(position = state.position, margin = state.margin, text = text)

        state.fleets.forEach { fleet ->
            Spacer(modifier = Modifier.height(8.dp))
            Top200FleetLine(fleet = fleet, text = text)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(id = R.string.top200_bound_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        if (state.nearLine) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(id = R.string.top200_demotion_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = Amber
            )
        }
        if (state.bindingOpen) {
            Spacer(modifier = Modifier.height(8.dp))
            SignFleetBindingButton(onClick = onSignFleetBinding)
        }
        Spacer(modifier = Modifier.height(4.dp))
        SeeBoardLink(onClick = onSeeBoard)
    }
}

/** "TOP 200" in gold with the label chip of the Protocol tab. */
@Composable
private fun Top200Heading(
    label: ProtocolLabel?,
) {
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
        label?.let { ProtocolLabelChip(label = it) }
    }
}

/** "#173" and "projected · +12%". */
@Composable
private fun Top200Position(
    position: Long,
    margin: Double,
    text: ProtocolText,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            protocolPositionText(position, text),
            style = HeadingLargeCondensed,
            color = Color.White
        )
        Text(
            top200ProjectedText(margin, text),
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

/** One claimed fleet: "UID 81", the validator's "#1 of 142 claimed", and whether it was selected. */
@Composable
private fun Top200FleetLine(
    fleet: ProtocolFleet,
    text: ProtocolText,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .background(BlueMedium.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                protocolUidText(fleet.uid, text),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BlueLight,
            )
        }
        val rank = fleet.validatorRank
        val of = fleet.validatorRankOf
        if (rank != null && of != null) {
            Text(
                protocolValidatorRankText(rank, of, text),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
        fleet.selected?.let { ProtocolSelectedChip(selected = it) }
    }
}

/** While binding is closed: claiming opens soon, with the warning, and no button. */
@Composable
private fun ClaimingSoonNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .border(1.dp, MainBorderBase, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = BlueMedium,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(id = R.string.earnings_top200_claiming_soon),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                stringResource(id = R.string.earnings_top200_claiming_soon_body),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

/** The way to the leaderboard's Protocol tab. */
@Composable
private fun SeeBoardLink(
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(
            stringResource(id = R.string.earnings_top200_see_board),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = BlueMedium,
        )
    }
}

/** Earnings › Top 200 › Sign fleet binding: the secondary action, while binding is open. */
@Composable
private fun SignFleetBindingButton(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .border(1.dp, ReferralGold.copy(alpha = 0.6f), RoundedCornerShape(100))
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(id = R.string.top200_sign_binding),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = ReferralGoldLight
        )
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
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
                state = Top200TileState.Projected(
                    position = 143,
                    margin = 0.27,
                    label = ProtocolLabel.ESTIMATE,
                    bindingOpen = false,
                    borderline = false,
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Top200BoundRow(
                state = Top200TileState.Claimed(
                    position = 180,
                    margin = 0.04,
                    label = ProtocolLabel.BORDERLINE,
                    fleets = listOf(
                        ProtocolFleet(
                            uid = 57,
                            generation = 2,
                            validToEpoch = 16,
                            replicaScore = 41.9,
                            validatorScore = 41.9,
                            validatorRank = 180,
                            validatorRankOf = 196,
                            selected = true,
                            incentive = 0.004,
                            incentiveStale = false,
                        )
                    ),
                    bindingOpen = true,
                    nearLine = true,
                )
            )
        }
    }
}
