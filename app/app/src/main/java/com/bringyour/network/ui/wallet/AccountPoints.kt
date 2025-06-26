package com.bringyour.network.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bringyour.network.R
import com.bringyour.network.ui.components.buttonTextStyle
import com.bringyour.network.ui.theme.HeadingLargeCondensed
import com.bringyour.network.ui.theme.MainTintedBackgroundBase
import com.bringyour.network.ui.theme.TextMuted
import com.bringyour.network.ui.theme.gravityCondensedFamily
import java.util.Locale

@Composable
fun AccountPoints(
        holdsMultiplier: Boolean,
        payoutPoints: Double,
        multiplierPoints: Double,
        referralPoints: Double,
        totalAccountPoints: Double
) {
    Column() {
        Box(
                modifier =
                        Modifier
                            .background(
                                color = MainTintedBackgroundBase,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(
                                start = 16.dp,
                                top = 16.dp,
                                bottom = 10.dp, // hacky due to line-height issue
                                end = 16.dp
                            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                        stringResource(id = R.string.points_breakdown),
                        style = buttonTextStyle,
                        color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                                stringResource(id = R.string.payouts),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                        )

                        Text(String.format(Locale.US, "%,.0f", payoutPoints), style = HeadingLargeCondensed)
                    }

                    Column {
                        Text(
                                stringResource(id = R.string.referral),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                        )

                        Text(String.format(Locale.US, "%,.0f", referralPoints), style = HeadingLargeCondensed)
                    }

                    Column {
                        Text(
                                stringResource(id = R.string.reliability),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                        )

                        Text("0", style = HeadingLargeCondensed)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                if (holdsMultiplier) {

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                    ) {

                        /** Seeker Token Holder handling */
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.point_multiplier),
                                    contentDescription = "Earning double points",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.width(36.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                            stringResource(id = R.string.seeker_token_verified),
                                            style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                            stringResource(id = R.string.earnings_doubled),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextMuted
                                    )
                                }
                            }
                        }

                        Text("+${ String.format(Locale.US, "%,.0f", multiplierPoints) }", style = HeadingLargeCondensed)

                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            String.format(Locale.US, "%,.0f", totalAccountPoints),
                                // style = HeadingLargeCondensed,
                                style =
                                        TextStyle(
                                                fontFamily = gravityCondensedFamily,
                                                fontWeight = FontWeight(900),
                                                fontSize = 48.sp,
                                                color = Color.White
                                        )
                        )

                        Text(
                                stringResource(id = R.string.net_points_earned),
                                modifier = Modifier.offset(y = (-10).dp),
                                style = TextStyle(color = TextMuted),
                        )
                    }
                }
            }
        }
    }
}
