package com.bringyour.network.ui.shared.models

import androidx.compose.ui.graphics.Color
import com.bringyour.network.ui.theme.Green
import com.bringyour.network.ui.theme.Red
import com.bringyour.network.ui.theme.Yellow
import com.bringyour.sdk.Sdk

/**
 * The provide indicator colors (apple parity), from the LIVE effective provide
 * tier. Shared by the settings picker and the provide-mode row on the stats and
 * earnings screens, so the two never disagree:
 *  - Network provide (incl. Auto while idle) = solid green dot
 *  - Public provide = green dot + outer green ring (yellow while paused, which
 *    stops public only)
 *  - not providing = red dot, no ring
 * ProvideMode is a bit set: compare per-case, never with ranges.
 */
fun provideIndicatorDotColorFor(provideMode: Long, providePaused: Boolean): Color =
    when (provideMode) {
        Sdk.ProvideModePublic -> if (providePaused) Yellow else Green
        Sdk.ProvideModeNetwork, Sdk.ProvideModeFriendsAndFamily -> Green
        else -> Red
    }

fun provideIndicatorRingColorFor(provideMode: Long, providePaused: Boolean): Color? =
    when {
        provideMode != Sdk.ProvideModePublic -> null
        providePaused -> Yellow
        else -> Green
    }
