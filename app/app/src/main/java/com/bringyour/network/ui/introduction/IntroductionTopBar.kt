package com.bringyour.network.ui.introduction

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.bringyour.network.R
import com.bringyour.network.ui.POST_LOGIN_INTRO_CLOSE_TAG
import com.bringyour.network.ui.theme.Black
import com.bringyour.network.ui.theme.TextFaint
import com.bringyour.network.ui.theme.TextMuted

/** The pages of the post-login onboarding flow, in order (welcome, data, providing, referral, quick connect). */
const val INTRO_STEP_COUNT = 5

/** The connector mark's size in the header; the mark itself is about two thirds of it (launcher safe zone). */
private const val HEADER_CONNECTOR_SIZE_DP = 34

/**
 * The shared top bar of the onboarding flow: back after the first page, the
 * step bubbles in the middle so the user always sees where they are and how
 * much is left, and a small muted Skip at the far end that leaves the whole
 * flow at any point, so nobody feels captive. Skip carries the acceptance
 * test tag the old close button had.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroductionTopBar(
    step: Int,
    onSkip: () -> Unit,
    onBack: (() -> Unit)? = null,
    totalSteps: Int = INTRO_STEP_COUNT,
) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (1 < step) {
                    // the connector lands here from page 1's route line
                    val connector = LocalIntroConnector.current
                    Box(
                        modifier = Modifier
                            .size(HEADER_CONNECTOR_SIZE_DP.dp)
                            .introConnectorHeaderSlot(connector)
                    ) {
                        if (connector == null) {
                            IntroConnectorMark(modifier = Modifier.size(HEADER_CONNECTOR_SIZE_DP.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }
                OnboardingStepBubbles(step = step, totalSteps = totalSteps)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = stringResource(id = R.string.back)
                    )
                }
            }
        },
        actions = {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag(POST_LOGIN_INTRO_CLOSE_TAG),
                colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)
            ) {
                Text(
                    stringResource(id = R.string.skip),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Black
        ),
    )
}

/**
 * One bubble per step: the current step is a white pill, earlier steps are
 * dimmed white, later steps are faint. Read to accessibility as
 * "Step 2 of 4".
 */
@Composable
fun OnboardingStepBubbles(
    step: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(id = R.string.onboarding_step_of, step, totalSteps)

    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 1..totalSteps) {
            val current = index == step
            val width by animateDpAsState(
                targetValue = if (current) 22.dp else 8.dp,
                label = "step-bubble-width"
            )
            val color by animateColorAsState(
                targetValue = when {
                    current -> Color.White
                    index < step -> Color.White.copy(alpha = 0.55f)
                    else -> TextFaint
                },
                label = "step-bubble-color"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
