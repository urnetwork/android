package com.bringyour.network.ui.leaderboard

import com.bringyour.network.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the Protocol tab shows for the model's state: the labels, the claim
 * prompt's states, the not-measurable reasons, the privacy of the names, the
 * banners, the Top 200 line, the detail sheet's facts and the chart's scale.
 * The text is a fake that spells out each catalog lookup, so the tests read
 * which string a state chose and with what.
 */
class ProtocolLeaderboardPresentationTest {

    /** Spells a lookup as "name(args)", numbers as themselves. */
    private val text = object : ProtocolText {
        override fun string(id: Int, vararg args: Any): String {
            val name = names[id] ?: "string-$id"
            return if (args.isEmpty()) name else "$name(${args.joinToString(",")})"
        }

        override fun count(value: Long) = "$value"

        override fun score(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

        override fun percent(fraction: Double) = "${Math.round(fraction * 100)}%"

        override fun signedPercent(fraction: Double) = (if (0.0 <= fraction) "+" else "-") + percent(Math.abs(fraction))

        override fun share(fraction: Double) = String.format(java.util.Locale.US, "%.1f%%", fraction * 100)

        override fun relativeTime(timeMillis: Long) = "at-$timeMillis"
    }

    private val names = mapOf(
        R.string.anonymous to "anonymous",
        R.string.leaderboard_protocol_uid to "uid",
        R.string.leaderboard_protocol_above_line to "above",
        R.string.leaderboard_protocol_below_line to "below",
        R.string.leaderboard_protocol_trend_up to "up",
        R.string.leaderboard_protocol_trend_down to "down",
        R.string.leaderboard_protocol_banner_closed to "closed",
        R.string.leaderboard_protocol_banner_stale to "stale",
        R.string.leaderboard_protocol_line to "line",
        R.string.leaderboard_protocol_line_a11y to "line-a11y",
        R.string.leaderboard_protocol_why to "why",
        R.string.leaderboard_protocol_solo to "solo",
        R.string.leaderboard_protocol_chart_a11y to "chart",
        R.string.leaderboard_protocol_you to "you",
        R.string.leaderboard_protocol_label_not_measurable to "not-measurable",
        R.string.leaderboard_protocol_label_estimate to "estimate",
        R.string.leaderboard_protocol_label_borderline to "borderline",
        R.string.leaderboard_protocol_detail_projected_rank to "projected-rank",
        R.string.leaderboard_protocol_detail_validator_rank to "validator-rank",
        R.string.leaderboard_protocol_detail_validator_rank_value to "rank-of",
        R.string.leaderboard_protocol_detail_validator_score to "validator-score",
        R.string.leaderboard_protocol_detail_replica_score to "replica-score",
        R.string.leaderboard_protocol_detail_incentive to "incentive",
        R.string.leaderboard_protocol_detail_stale to "stale",
        R.string.leaderboard_protocol_detail_generation to "generation",
        R.string.leaderboard_protocol_detail_valid_until to "valid-until",
        R.string.leaderboard_protocol_detail_epoch to "epoch",
    )

    private val settings = defaultProtocolLeaderboardSettings()

    private fun snapshot(
        bindingOpen: Boolean = false,
        stale: Boolean = false,
        computedAtMillis: Long? = 1_799_000_000_000L,
        totalRanked: Long = 41_230,
    ) = ProtocolSnapshot(
        id = "snap-1",
        tempoIndex = 7,
        computedAtMillis = computedAtMillis,
        totalRanked = totalRanked,
        headSeats = 200,
        claimedFleets = 3,
        openSeats = 197,
        projectedCutoff = 41.2,
        bindingOpen = bindingOpen,
        stale = stale,
    )

    private fun fleet(uid: Long, selected: Boolean? = true) = ProtocolFleet(
        uid = uid,
        generation = 2,
        validToEpoch = 16,
        replicaScore = 312.4,
        validatorScore = 312.4,
        validatorRank = 1,
        validatorRankOf = 142,
        selected = selected,
        incentive = 0.018,
        incentiveStale = true,
    )

    private fun row(
        position: Long,
        name: String = "",
        label: ProtocolLabel? = ProtocolLabel.ESTIMATE,
        measurable: Boolean = true,
        claimPrompt: Boolean = measurable && position <= 200,
        isMe: Boolean = false,
        fleets: List<ProtocolFleet> = listOf(),
        margin: Double = 0.12,
        trend: Long = 0,
    ) = ProtocolRow(
        position = position,
        name = name,
        tag = "",
        projectedScore = 41.2,
        margin = margin,
        trend = trend,
        label = label,
        measurable = measurable,
        claimPrompt = claimPrompt,
        isMe = isMe,
        fleets = fleets,
    )

    private fun me(row: ProtocolRow, reason: ProtocolNotMeasurableReason = ProtocolNotMeasurableReason.NONE) = ProtocolMe(
        row = row,
        soloScore = 21.4,
        distinctPrefixes = 18,
        sharedPrefixes = 3,
        sharedWithUnits = 2,
        notMeasurableReason = reason,
        history = listOf(),
    )

    private fun signedIn(me: ProtocolMe?, snapshot: ProtocolSnapshot = snapshot(), namePublic: Boolean? = false) =
        ProtocolLeaderboardState.Initial.copy(
            hasClient = true,
            signedIn = true,
            me = me,
            meSnapshot = snapshot,
            meLoaded = true,
            namePublic = namePublic,
        )

    private fun standing(state: ProtocolLeaderboardState) = (protocolCard(state) as ProtocolCard.Standing).standing

    @Test
    fun labelsReadTheirWireValuesAndAnUnknownOneHasNoChip() {
        assertEquals(ProtocolLabel.ESTIMATE, ProtocolLabel.fromWire("estimate"))
        assertEquals(ProtocolLabel.LIKELY, ProtocolLabel.fromWire("likely"))
        assertEquals(ProtocolLabel.BORDERLINE, ProtocolLabel.fromWire("borderline"))
        assertEquals(ProtocolLabel.UNLIKELY, ProtocolLabel.fromWire("unlikely"))
        assertEquals(ProtocolLabel.NOT_MEASURABLE, ProtocolLabel.fromWire("not_measurable"))
        assertNull(ProtocolLabel.fromWire("certain"))
        assertNull(ProtocolLabel.fromWire(""))
        assertEquals(R.string.leaderboard_protocol_label_not_measurable, ProtocolLabel.NOT_MEASURABLE.textRes)
    }

    @Test
    fun notMeasurableReasonsReadTheirWireValues() {
        assertEquals(ProtocolNotMeasurableReason.NONE, ProtocolNotMeasurableReason.fromWire(""))
        assertEquals(ProtocolNotMeasurableReason.NOT_IN_COHORT, ProtocolNotMeasurableReason.fromWire("not_in_cohort"))
        assertEquals(ProtocolNotMeasurableReason.SHARED_EGRESS, ProtocolNotMeasurableReason.fromWire("shared_egress"))
        assertEquals(ProtocolNotMeasurableReason.NOT_PROVIDING, ProtocolNotMeasurableReason.fromWire("not_providing"))
        assertEquals(ProtocolNotMeasurableReason.NONE, ProtocolNotMeasurableReason.fromWire("solar_flare"))
        assertEquals(R.string.leaderboard_protocol_not_measurable_cohort, ProtocolNotMeasurableReason.NOT_IN_COHORT.textRes)
        assertEquals(R.string.leaderboard_protocol_not_measurable_shared, ProtocolNotMeasurableReason.SHARED_EGRESS.textRes)
        assertEquals(R.string.leaderboard_protocol_not_measurable_providing, ProtocolNotMeasurableReason.NOT_PROVIDING.textRes)
    }

    @Test
    fun withBindingClosedTheClaimPromptIsTheOpensSoonNote() {
        val prompt = standing(signedIn(me(row(173, margin = 0.32)), snapshot(bindingOpen = false))).prompt
        assertEquals(ProtocolClaimPrompt(bindingOpen = false, borderline = false), prompt)
    }

    @Test
    fun withBindingOpenTheClaimPromptIsTheButton() {
        val prompt = standing(signedIn(me(row(173, margin = 0.32)), snapshot(bindingOpen = true))).prompt
        assertEquals(ProtocolClaimPrompt(bindingOpen = true, borderline = false), prompt)
    }

    @Test
    fun aBorderlineNetworkGetsThePromptWithTheCloseToTheLineWarning() {
        val prompt = standing(signedIn(me(row(196, label = ProtocolLabel.BORDERLINE, margin = 0.03)), snapshot(bindingOpen = true))).prompt
        assertEquals(ProtocolClaimPrompt(bindingOpen = true, borderline = true), prompt)
    }

    @Test
    fun whileEveryRowReadsEstimateTheMarginSaysWhatIsClose() {
        // before precision reaches 90% no row is labeled borderline (Q17):
        // within 20% of the line is close
        assertEquals(true, protocolClaimPrompt(row(190, margin = 0.05), snapshot())?.borderline)
        assertEquals(true, protocolClaimPrompt(row(160, margin = 0.199), snapshot())?.borderline)
        assertEquals(false, protocolClaimPrompt(row(120, margin = 0.2), snapshot())?.borderline)
        // once labels switch on, the label decides
        assertEquals(false, protocolClaimPrompt(row(190, label = ProtocolLabel.LIKELY, margin = 0.05), snapshot())?.borderline)
        assertEquals(true, protocolClaimPrompt(row(120, label = ProtocolLabel.BORDERLINE, margin = 0.25), snapshot())?.borderline)
        // an unknown label reads as no label
        assertEquals(true, protocolClaimPrompt(row(190, label = null, margin = 0.05), snapshot())?.borderline)
    }

    @Test
    fun whetherToPromptIsTheServersClaimPromptAlone() {
        // a network that claimed: the server leaves the flag false (CONTRACT, amended 2026-10-29)
        assertNull(protocolClaimPrompt(row(150, fleets = listOf(fleet(81)), claimPrompt = false), snapshot()))
        assertNull(protocolClaimPrompt(row(150, claimPrompt = false), snapshot()))
        // the app never recomputes it from the position, the label or the fleets
        assertEquals(
            ProtocolClaimPrompt(bindingOpen = false, borderline = false),
            protocolClaimPrompt(row(230, claimPrompt = true, margin = 0.3), snapshot()),
        )
        assertEquals(
            ProtocolClaimPrompt(bindingOpen = true, borderline = false),
            protocolClaimPrompt(row(150, fleets = listOf(fleet(81)), claimPrompt = true, margin = 0.3), snapshot(bindingOpen = true)),
        )
    }

    @Test
    fun outsideTheHeadSeatsThereIsNoPrompt() {
        val outside = standing(signedIn(me(row(240, margin = -0.08, label = ProtocolLabel.UNLIKELY))))
        assertNull(outside.prompt)
        assertNull(outside.notMeasurable)
    }

    @Test
    fun aNetworkTheValidatorCantMeasureGetsItsReasonAndNoPrompt() {
        // the server never prompts an unmeasurable network (claim_prompt false)
        val cohort = standing(
            signedIn(
                me(
                    row(120, measurable = false, label = ProtocolLabel.NOT_MEASURABLE, claimPrompt = false),
                    reason = ProtocolNotMeasurableReason.NOT_IN_COHORT,
                )
            )
        )
        assertNull(cohort.prompt)
        assertEquals(ProtocolNotMeasurableReason.NOT_IN_COHORT, cohort.notMeasurable)

        // labeled not measurable is as good as the flag
        val shared = standing(
            signedIn(
                me(
                    row(120, measurable = true, label = ProtocolLabel.NOT_MEASURABLE, claimPrompt = false),
                    reason = ProtocolNotMeasurableReason.SHARED_EGRESS,
                )
            )
        )
        assertNull(shared.prompt)
        assertEquals(ProtocolNotMeasurableReason.SHARED_EGRESS, shared.notMeasurable)
    }

    @Test
    fun theCardShowsTheNameOnlyWhenTheSwitchSaysSo() {
        // the switch just turned off: a standing read before it still has the name
        val hidden = standing(signedIn(me(row(173, name = "kayak-hollow")), namePublic = false))
        assertEquals("", hidden.name)
        val shown = standing(signedIn(me(row(173, name = "kayak-hollow")), namePublic = true))
        assertEquals("kayak-hollow", shown.name)
        // before the switch is read, the row's own word
        val unread = standing(signedIn(me(row(173, name = "kayak-hollow")), namePublic = null))
        assertEquals("kayak-hollow", unread.name)
    }

    @Test
    fun anonymousRowsAreAnonymousAndAnonymousClaimedRowsAreTheirUid() {
        assertEquals(ProtocolRowName.Named("stellar-mesh"), protocolRowName(row(2, name = "stellar-mesh")))
        assertEquals(ProtocolRowName.Anonymous, protocolRowName(row(3)))
        // never a name for an anonymous claimed row: its lowest UID
        assertEquals(ProtocolRowName.Uid(77), protocolRowName(row(202, fleets = listOf(fleet(212), fleet(77)))))
        assertEquals("anonymous", protocolRowNameText(protocolRowName(row(3)), text))
        assertEquals("uid(77)", protocolRowNameText(protocolRowName(row(202, fleets = listOf(fleet(77)))), text))
        // a named claimed row keeps its name; its badge is the UID
        assertEquals(ProtocolRowName.Named("quiet-harbor"), protocolRowName(row(196, name = "quiet-harbor", fleets = listOf(fleet(212)))))
        assertEquals(212L, protocolBadgeUid(row(196, fleets = listOf(fleet(212)))))
        assertNull(protocolBadgeUid(row(196)))
    }

    @Test
    fun whatTalkBackReadsForARow() {
        // an anonymous claimed row is its UID, said once
        assertEquals(
            "#202, uid(77), 41.2, up(3)",
            protocolRowSpoken(row(202, fleets = listOf(fleet(77)), trend = 3), text),
        )
        // a named claimed row has its badge too
        assertEquals(
            "#196, quiet-harbor, 41.2, uid(212)",
            protocolRowSpoken(row(196, name = "quiet-harbor", fleets = listOf(fleet(212))), text),
        )
        assertEquals(
            "#204, anonymous, 41.2, down(2), not-measurable",
            protocolRowSpoken(row(204, measurable = false, label = ProtocolLabel.NOT_MEASURABLE, trend = -2), text),
        )
        // the caller's own row says so, with its label
        assertEquals(
            "you, #173, anonymous, 41.2, estimate",
            protocolRowSpoken(row(173, isMe = true), text),
        )
        // another row's label is not read: the list shows only the caller's
        assertEquals("#174, anonymous, 41.2", protocolRowSpoken(row(174, label = ProtocolLabel.BORDERLINE), text))
    }

    @Test
    fun theMarginReadsAboveOrBelowTheLine() {
        assertEquals("above(12%)", protocolMarginText(0.12, text))
        assertEquals("below(8%)", protocolMarginText(-0.08, text))
        assertEquals("above(0%)", protocolMarginText(0.0, text))
    }

    @Test
    fun theTrendReadsUpOrDownAndNothingWhenTheRankHeld() {
        assertEquals("up(9)", protocolTrendText(9, text))
        assertEquals("down(2)", protocolTrendText(-2, text))
        assertNull(protocolTrendText(0, text))
    }

    @Test
    fun theClosedBannerComesFirstAndGoesWhenBindingOpens() {
        assertEquals(listOf(ProtocolBanner.ClaimingClosed), protocolBanners(snapshot()))
        assertEquals(
            listOf(ProtocolBanner.ClaimingClosed, ProtocolBanner.Stale(1_799_000_000_000L)),
            protocolBanners(snapshot(stale = true)),
        )
        assertEquals(listOf(ProtocolBanner.Stale(1_799_000_000_000L)), protocolBanners(snapshot(bindingOpen = true, stale = true)))
        assertEquals(listOf<ProtocolBanner>(), protocolBanners(snapshot(bindingOpen = true)))
        // a stale snapshot without its time has no "last updated"
        assertEquals(listOf<ProtocolBanner>(), protocolBanners(snapshot(bindingOpen = true, stale = true, computedAtMillis = null)))
        assertEquals("stale(at-1799000000000)", protocolBannerText(ProtocolBanner.Stale(1_799_000_000_000L), text))
        assertEquals("closed", protocolBannerText(ProtocolBanner.ClaimingClosed, text))
        assertEquals(listOf<ProtocolBanner>(), protocolBanners(null))
    }

    @Test
    fun theLineIsUnderTheLastHeadSeatAndRowsAboveItAreTinted() {
        val state = ProtocolLeaderboardState.Initial.copy(
            hasClient = true,
            snapshot = snapshot(),
            rows = (195L..205L).map { row(it) },
            listLoaded = true,
        )
        val ui = protocolLeaderboardUi(state, settings)
        assertEquals(listOf(200L), ui.rows.filter { it.lineAfter }.map { it.row.position })
        assertEquals((195L..200L).toList(), ui.rows.filter { it.aboveLine }.map { it.row.position })
        assertEquals(41.2, ui.lineScore, 0.0)
        assertEquals("line(41.2)", protocolLineText(ui.lineScore, text))
        assertEquals("line-a11y(41.2)", protocolLineSpoken(ui.lineScore, text))

        // with every ranked network inside the head seats there is nothing to divide
        val few = protocolLeaderboardUi(state.copy(snapshot = snapshot(totalRanked = 200)), settings)
        assertTrue(few.rows.none { it.lineAfter })
    }

    @Test
    fun theWhyIsCountsOnly() {
        val s = standing(signedIn(me(row(173))))
        assertEquals("why(18,3)", protocolWhyText(s, text))
        assertEquals("solo(21.4)", protocolSoloText(s, text))
        assertEquals("chart(173)", protocolChartSpoken(173, text))
    }

    @Test
    fun theDetailSheetListsWhatIsKnownAndMarksAStaleIncentive() {
        val claimed = row(1, fleets = listOf(fleet(81)))
        assertEquals(
            listOf(
                ProtocolDetailFact("projected-rank", "#1"),
                ProtocolDetailFact("validator-rank", "rank-of(1,142)"),
                ProtocolDetailFact("validator-score", "312.4"),
                ProtocolDetailFact("replica-score", "312.4"),
                ProtocolDetailFact("incentive", "1.8% · stale"),
                ProtocolDetailFact("generation", "2"),
                ProtocolDetailFact("valid-until", "epoch(16)"),
            ),
            protocolDetailFacts(claimed, claimed.fleets[0], text),
        )
        // what the validator and the chain have not measured is left out
        val unmeasured = fleet(81).copy(
            validatorScore = null,
            validatorRank = null,
            validatorRankOf = null,
            selected = null,
            incentive = null,
        )
        assertEquals(
            listOf("projected-rank", "replica-score", "generation", "valid-until"),
            protocolDetailFacts(claimed, unmeasured, text).map { it.label },
        )
    }

    @Test
    fun theChartDrawsTheDaysInOrderAndAlwaysHoldsTheLine() {
        val history = listOf(
            ProtocolHistoryPoint(day = "2026-10-28", rank = 180, score = 40.0),
            ProtocolHistoryPoint(day = "2026-10-26", rank = 260, score = 30.0),
            ProtocolHistoryPoint(day = "2026-10-27", rank = 0, score = 0.0),
            ProtocolHistoryPoint(day = "2026-10-28", rank = 173, score = 41.0),
        )
        val points = protocolChartPoints(history)
        assertEquals(listOf("2026-10-26", "2026-10-28"), points.map { it.day })
        assertEquals(173L, points.last().rank)

        val scale = protocolChartScale(points, headSeats = 200)
        // rank 1 is up: a better rank is nearer the top
        assertTrue(scale.fraction(173.0) < scale.fraction(260.0))
        // the line is inside the drawing
        assertTrue(scale.fraction(200.0) in 0.01f..0.99f)

        // far below the line, the line is still drawn across
        val far = protocolChartScale(listOf(ProtocolHistoryPoint("2026-10-28", 9_000, 1.0)), headSeats = 200)
        assertTrue(far.fraction(200.0) in 0.0f..0.2f)
        assertTrue(far.fraction(9_000.0) in 0.8f..1.0f)

        // at most the last 90 days
        val long = (1..120).map { ProtocolHistoryPoint(day = "2026-%02d-%02d".format(1 + it / 28, 1 + it % 28), rank = it.toLong(), score = 1.0) }
        assertEquals(90, protocolChartPoints(long).size)
    }

    @Test
    fun aTouchPicksTheNearestDay() {
        assertEquals(-1, protocolChartIndexAt(10f, 100f, 0))
        assertEquals(0, protocolChartIndexAt(50f, 100f, 1))
        assertEquals(0, protocolChartIndexAt(0f, 100f, 11))
        assertEquals(5, protocolChartIndexAt(50f, 100f, 11))
        assertEquals(10, protocolChartIndexAt(1_000f, 100f, 11))
    }

    @Test
    fun theFooterAndTheNoticeFollowTheLoads() {
        val loaded = ProtocolLeaderboardState.Initial.copy(
            hasClient = true,
            snapshot = snapshot(),
            rows = (1L..50L).map { row(it) },
            next = "c1",
            listLoaded = true,
        )
        assertEquals(ProtocolListFooter.Loading, protocolLeaderboardUi(loaded.copy(pageLoading = true), settings).footer)
        assertEquals(ProtocolListFooter.Failed, protocolLeaderboardUi(loaded.copy(pageFailed = true), settings).footer)
        assertEquals(ProtocolListFooter.None, protocolLeaderboardUi(loaded, settings).footer)
        assertTrue(protocolLeaderboardUi(loaded, settings).hasMoreAfter)
        val failedRefresh = protocolLeaderboardUi(loaded.copy(listFailed = true), settings)
        assertTrue(failedRefresh.refreshFailed)
        assertEquals(50, failedRefresh.rows.size)
        assertFalse(protocolLeaderboardUi(loaded, settings).refreshFailed)
    }
}
