package com.bringyour.network.ui.leaderboard

import androidx.annotation.StringRes
import com.bringyour.network.R
import java.text.NumberFormat
import java.util.Locale

/*
 * The Protocol tab of the leaderboard (apple/LEADERTOP.md; the ranking, data
 * and api are server/LEADERTOP.md's), as data: the board's snapshot, its rows
 * and the caller's own standing, copied off the sdk's gomobile objects (sdk
 * SnTop and SnTopMe), and what the tab shows for them. Everything here is
 * plain data and functions, so the JVM tests cover it without the sdk's
 * native library; SdkProtocolTopClient copies the gomobile results in and
 * ProtocolLeaderboardTab renders the result.
 *
 * Privacy (server/LEADERTOP.md §9, Q12): rows carry no network id. A row's
 * name is empty unless its network opted in, and an anonymous claimed row is
 * shown by its fleet's UID, never by a name. The caller's own row is the one
 * the server flags is_me; the app never matches rows by name.
 */

/**
 * The Protocol tab's tunables. The one default is
 * [defaultProtocolLeaderboardSettings]; tests pass their own.
 */
data class ProtocolLeaderboardSettings(
    // rows per page of GET /sn/top while scrolling: the server's default page,
    // 50 (LEADERTOP §6); the server takes 1..200
    val pageSize: Int,
    // rows per page while a seek of the position indicator pages toward a
    // position not loaded yet: the server's largest page, so the far end of
    // the list is two requests away instead of eight
    val seekPageSize: Int,
    // the last position the list and its position indicator span: the server
    // serves the top 400 (Q5)
    val listRange: Long,
    // rows from the end of the loaded rows at which the next page is asked for
    val loadMoreThreshold: Int,
    // within this fraction of the line a network is close to it: M of Q13,
    // 20%. While every row reads "estimate" (Q17) the claim prompt's
    // borderline warning goes by it; once labels switch on, by the label
    val closeMargin: Double,
)

fun defaultProtocolLeaderboardSettings() = ProtocolLeaderboardSettings(
    pageSize = 50,
    seekPageSize = 200,
    listRange = 400,
    loadMoreThreshold = 10,
    closeMargin = 0.20,
)

/** The recommendation label (server/LEADERTOP.md §5.6, Q13, Q17), by its wire value. */
enum class ProtocolLabel(val wire: String, @get:StringRes val textRes: Int) {
    ESTIMATE("estimate", R.string.leaderboard_protocol_label_estimate),
    LIKELY("likely", R.string.leaderboard_protocol_label_likely),
    BORDERLINE("borderline", R.string.leaderboard_protocol_label_borderline),
    UNLIKELY("unlikely", R.string.leaderboard_protocol_label_unlikely),
    NOT_MEASURABLE("not_measurable", R.string.leaderboard_protocol_label_not_measurable);

    companion object {
        // null for a value a newer server adds: no chip rather than a wrong one
        fun fromWire(wire: String): ProtocolLabel? = entries.firstOrNull { it.wire == wire }
    }
}

/** Why the validator can't score the caller's network (SnTopMe.not_measurable_reason). */
enum class ProtocolNotMeasurableReason(val wire: String, @get:StringRes val textRes: Int?) {
    NONE("", null),
    NOT_IN_COHORT("not_in_cohort", R.string.leaderboard_protocol_not_measurable_cohort),
    SHARED_EGRESS("shared_egress", R.string.leaderboard_protocol_not_measurable_shared),
    NOT_PROVIDING("not_providing", R.string.leaderboard_protocol_not_measurable_providing);

    companion object {
        // a reason a newer server adds has no text here; the label still says not measurable
        fun fromWire(wire: String): ProtocolNotMeasurableReason = entries.firstOrNull { it.wire == wire } ?: NONE
    }
}

/** SnTopSnapshot: the one tempo's board every row and the card come from. */
data class ProtocolSnapshot(
    val id: String,
    val tempoIndex: Long,
    // RFC 3339 computed_at in Unix millis; null when the server sent none
    val computedAtMillis: Long?,
    val totalRanked: Long,
    // the head seats, 200: the line sits under the last of them
    val headSeats: Long,
    val claimedFleets: Long,
    val openSeats: Long,
    // the projected score of the last head seat, the line's score
    val projectedCutoff: Double,
    // fleet binding is open on mainnet (Q16)
    val bindingOpen: Boolean,
    // computed more than 3 tempos ago
    val stale: Boolean,
)

/** SnTopFleet: one claimed fleet of a row. Nulls are what the server has not measured. */
data class ProtocolFleet(
    val uid: Long,
    val generation: Long,
    val validToEpoch: Long,
    val replicaScore: Double,
    val validatorScore: Double?,
    val validatorRank: Long?,
    val validatorRankOf: Long?,
    val selected: Boolean?,
    // a fraction, 0..1
    val incentive: Double?,
    val incentiveStale: Boolean,
)

/** SnTopRow: one ranked network. */
data class ProtocolRow(
    // 1-based position in the board's total order: the list's key
    val position: Long,
    // empty when the network is anonymous (Q12)
    val name: String,
    // the network's emoji tag; may be empty
    val tag: String,
    val projectedScore: Double,
    // a fraction of the projected cutoff: + above the line, - below
    val margin: Double,
    // positions moved since the previous tempo, + up
    val trend: Long,
    // null for a label this app does not know
    val label: ProtocolLabel?,
    val measurable: Boolean,
    // ask the network to claim: measurable, inside the head seats and not
    // claimed (the server's rule, which the app never recomputes)
    val claimPrompt: Boolean,
    // the authenticated caller's own row
    val isMe: Boolean,
    // empty when the network has not claimed
    val fleets: List<ProtocolFleet>,
)

/** One day of the caller's 90-day history, oldest first. */
data class ProtocolHistoryPoint(
    // YYYY-MM-DD
    val day: String,
    val rank: Long,
    val score: Double,
)

/** SnTopMe: the caller's network in detail. */
data class ProtocolMe(
    val row: ProtocolRow,
    // the score if no other unit sharing its prefixes claimed (Q4)
    val soloScore: Double,
    val distinctPrefixes: Long,
    val sharedPrefixes: Long,
    val sharedWithUnits: Long,
    val notMeasurableReason: ProtocolNotMeasurableReason,
    val history: List<ProtocolHistoryPoint>,
)

/** The tab as ProtocolLeaderboardModel keeps it. */
data class ProtocolLeaderboardState(
    // a device's api answers: nothing loads without one
    val hasClient: Boolean,
    // the calls carry the network's auth: the card needs it
    val signedIn: Boolean,
    // the list's snapshot, which its pages are pinned to
    val snapshot: ProtocolSnapshot?,
    // in position order, without gaps or repeats
    val rows: List<ProtocolRow>,
    // the next page's cursor; null at the end of the list
    val next: String?,
    // a first page arrived for this client
    val listLoaded: Boolean,
    // the first page, or a refresh over the shown rows
    val listLoading: Boolean,
    // the first page, or the last refresh, failed
    val listFailed: Boolean,
    val pageLoading: Boolean,
    // the last next page failed; it is not asked for again until a retry
    val pageFailed: Boolean,
    // null when the network has no row on the board
    val me: ProtocolMe?,
    val meSnapshot: ProtocolSnapshot?,
    // an answer to /sn/top/me arrived for this client
    val meLoaded: Boolean,
    val meLoading: Boolean,
    // the last /sn/top/me failed
    val meFailed: Boolean,
    // points_leaderboard_public, the one "show my name on leaderboards" switch
    // (Q12); null until read
    val namePublic: Boolean?,
    val settingNamePublic: Boolean,
) {
    companion object {
        // nothing loaded: no client yet, or the client changed
        val Initial = ProtocolLeaderboardState(
            hasClient = false,
            signedIn = false,
            snapshot = null,
            rows = listOf(),
            next = null,
            listLoaded = false,
            listLoading = false,
            listFailed = false,
            pageLoading = false,
            pageFailed = false,
            me = null,
            meSnapshot = null,
            meLoaded = false,
            meLoading = false,
            meFailed = false,
            namePublic = null,
            settingNamePublic = false,
        )
    }
}

/** The one-line banners on top of the tab (LEADERTOP §4). */
sealed interface ProtocolBanner {
    // binding is closed on mainnet (Q16): the neutral banner, not an error
    data object ClaimingClosed : ProtocolBanner

    // the snapshot is more than 3 tempos old: "Rankings last updated {time}."
    data class Stale(val computedAtMillis: Long) : ProtocolBanner
}

/** The claim prompt (Q5, Q13, Q16): only for a measurable network inside the head seats. */
data class ProtocolClaimPrompt(
    // the "Claim on Earnings" button when binding is open; otherwise the
    // "opens soon" note, and nothing invites registering early
    val bindingOpen: Boolean,
    // close to the line (protocolClaimPrompt): the "close to the line" warning too
    val borderline: Boolean,
)

/** The caller's standing, as the own-status card shows it. */
data class ProtocolStanding(
    val position: Long,
    // of the snapshot the card's numbers come from; 0 when unknown
    val totalRanked: Long,
    val headSeats: Long,
    val margin: Double,
    val trend: Long,
    val label: ProtocolLabel?,
    val tag: String,
    // the name as others see it; empty when anonymous
    val name: String,
    // the switch's value: points_leaderboard_public; null until read
    val namePublic: Boolean?,
    val history: List<ProtocolHistoryPoint>,
    val distinctPrefixes: Long,
    val sharedPrefixes: Long,
    val soloScore: Double,
    // null when there is no prompt
    val prompt: ProtocolClaimPrompt?,
    // why the validator can't score the network; null while measurable
    val notMeasurable: ProtocolNotMeasurableReason?,
    val fleets: List<ProtocolFleet>,
)

/** The own-status card (LEADERTOP §5), or what stands in its place. */
sealed interface ProtocolCard {
    // no network auth: the public list only, and no card (LEADERTOP §9)
    data object SignedOut : ProtocolCard

    // placeholders in the card's box
    data object Loading : ProtocolCard

    // the first read failed: the retry row in the card's box
    data object LoadFailed : ProtocolCard

    // the network has no row on the board: what gets it ranked
    data object NotRanked : ProtocolCard

    data class Standing(val standing: ProtocolStanding) : ProtocolCard
}

/** What the list shows in place of, or around, its rows. */
enum class ProtocolListBody {
    // no client: nothing will load, so nothing is shown (no placeholder that never resolves)
    None,
    // the first page is on its way: placeholder rows
    Progress,
    // the first page failed: the retry row
    LoadFailed,
    // the snapshot ranks nobody yet
    Empty,
    Rows,
}

/** The row under the list's rows. */
enum class ProtocolListFooter {
    None,
    // a next page is on its way
    Loading,
    // a next page failed: the retry row
    Failed,
}

/** One row of the list as shown. */
data class ProtocolListRow(
    // unique per row, for the lazy list
    val key: String,
    val row: ProtocolRow,
    // inside the head seats: the faint accent tint
    val aboveLine: Boolean,
    // the last head seat: the Top 200 line is drawn under it
    val lineAfter: Boolean,
)

data class ProtocolLeaderboardUi(
    val banners: List<ProtocolBanner>,
    val card: ProtocolCard,
    val body: ProtocolListBody,
    val rows: List<ProtocolListRow>,
    val footer: ProtocolListFooter,
    // a load over the shown list (a pull, a retry): the pull indicator
    val refreshing: Boolean,
    // a refresh failed and the rows are the last list: the notice over them
    val refreshFailed: Boolean,
    // the line's score: the projected score of the last head seat
    val lineScore: Double,
    // the networks the snapshot ranks; 0 before one arrives
    val totalRanked: Long,
    // the positions the position indicator spans: 1..this
    val indicatorTotal: Long,
    // pages remain after the loaded rows
    val hasMoreAfter: Boolean,
    // a change of the name switch is on its way: the switch waits
    val settingNamePublic: Boolean,
) {
    companion object {
        val Initial = protocolLeaderboardUi(ProtocolLeaderboardState.Initial, defaultProtocolLeaderboardSettings())
    }
}

/**
 * The claim prompt for a row (Q5, Q13, Q16), the same on the card and on the
 * Earnings tile. Whether a network is asked to claim is the server's
 * claim_prompt alone (CONTRACT, amended 2026-10-29: measurable, inside the
 * head seats, and no claimed fleet); the app never decides it. The prompt is
 * close to the line when the label says borderline, or, while it still reads
 * "estimate" (Q17), when the margin is within `closeMargin` of the line.
 */
fun protocolClaimPrompt(
    row: ProtocolRow,
    snapshot: ProtocolSnapshot?,
    closeMargin: Double = defaultProtocolLeaderboardSettings().closeMargin,
): ProtocolClaimPrompt? {
    if (!row.claimPrompt) {
        return null
    }
    val close = when (row.label) {
        ProtocolLabel.BORDERLINE -> true
        ProtocolLabel.ESTIMATE, null -> Math.abs(row.margin) < closeMargin
        else -> false
    }
    return ProtocolClaimPrompt(
        bindingOpen = snapshot?.bindingOpen == true,
        borderline = close,
    )
}

/**
 * The card for the model's state. The card's own numbers come from the
 * /sn/top/me snapshot, which is the one its row was ranked in.
 */
fun protocolCard(
    state: ProtocolLeaderboardState,
    closeMargin: Double = defaultProtocolLeaderboardSettings().closeMargin,
): ProtocolCard {
    if (!state.signedIn) {
        return ProtocolCard.SignedOut
    }
    val me = state.me
    if (me == null) {
        return when {
            state.meLoaded -> ProtocolCard.NotRanked
            state.meFailed -> ProtocolCard.LoadFailed
            else -> ProtocolCard.Loading
        }
    }
    val snapshot = state.meSnapshot ?: state.snapshot
    val row = me.row
    val measurable = row.measurable && row.label != ProtocolLabel.NOT_MEASURABLE
    val headSeats = snapshot?.headSeats ?: 0L
    val prompt = protocolClaimPrompt(row, snapshot, closeMargin)
    // after a change of the switch its value wins over a row read before it
    val name = when (state.namePublic) {
        false -> ""
        else -> row.name
    }
    return ProtocolCard.Standing(
        ProtocolStanding(
            position = row.position,
            totalRanked = snapshot?.totalRanked ?: 0L,
            headSeats = headSeats,
            margin = row.margin,
            trend = row.trend,
            label = row.label,
            tag = row.tag,
            name = name,
            namePublic = state.namePublic,
            history = me.history,
            distinctPrefixes = me.distinctPrefixes,
            sharedPrefixes = me.sharedPrefixes,
            soloScore = me.soloScore,
            prompt = prompt,
            notMeasurable = if (measurable) null else me.notMeasurableReason,
            fleets = row.fleets,
        )
    )
}

/** The banners for a snapshot: claiming closed, then the stale notice, in the order apple shows them. */
fun protocolBanners(snapshot: ProtocolSnapshot?): List<ProtocolBanner> {
    if (snapshot == null) {
        return listOf()
    }
    val banners = mutableListOf<ProtocolBanner>()
    if (!snapshot.bindingOpen) {
        banners.add(ProtocolBanner.ClaimingClosed)
    }
    val computedAtMillis = snapshot.computedAtMillis
    if (snapshot.stale && computedAtMillis != null) {
        banners.add(ProtocolBanner.Stale(computedAtMillis))
    }
    return banners
}

/** The tab for the model's state. */
fun protocolLeaderboardUi(state: ProtocolLeaderboardState, settings: ProtocolLeaderboardSettings): ProtocolLeaderboardUi {
    val snapshot = state.snapshot ?: state.meSnapshot
    val headSeats = snapshot?.headSeats ?: 0L
    val totalRanked = snapshot?.totalRanked ?: 0L
    val body = when {
        !state.hasClient -> ProtocolListBody.None
        // a retry of a failed first load shows placeholders again
        !state.listLoaded && state.listLoading -> ProtocolListBody.Progress
        !state.listLoaded && state.listFailed -> ProtocolListBody.LoadFailed
        !state.listLoaded -> ProtocolListBody.Progress
        state.rows.isEmpty() -> ProtocolListBody.Empty
        else -> ProtocolListBody.Rows
    }
    val rows = if (body == ProtocolListBody.Rows) {
        state.rows.map { row ->
            ProtocolListRow(
                key = "protocol:${row.position}",
                row = row,
                aboveLine = 0L < headSeats && row.position <= headSeats,
                // the line divides the ranked: none when every ranked network is inside
                lineAfter = 0L < headSeats && row.position == headSeats && headSeats < totalRanked,
            )
        }
    } else {
        listOf()
    }
    val listed = body == ProtocolListBody.Rows || body == ProtocolListBody.Empty
    return ProtocolLeaderboardUi(
        banners = protocolBanners(snapshot),
        card = protocolCard(state, settings.closeMargin),
        body = body,
        rows = rows,
        footer = when {
            body != ProtocolListBody.Rows -> ProtocolListFooter.None
            state.pageLoading -> ProtocolListFooter.Loading
            state.pageFailed -> ProtocolListFooter.Failed
            else -> ProtocolListFooter.None
        },
        refreshing = listed && state.listLoading,
        refreshFailed = listed && state.listFailed,
        lineScore = state.snapshot?.projectedCutoff ?: 0.0,
        totalRanked = totalRanked,
        indicatorTotal = minOf(settings.listRange, totalRanked).coerceAtLeast(0L),
        hasMoreAfter = body == ProtocolListBody.Rows && state.next != null,
        settingNamePublic = state.settingNamePublic,
    )
}

/** How a row's network is named in the list and the detail sheet. */
sealed interface ProtocolRowName {
    data class Named(val name: String) : ProtocolRowName

    // anonymous and claimed: its fleet's UID, never a name (Q12)
    data class Uid(val uid: Long) : ProtocolRowName

    data object Anonymous : ProtocolRowName
}

fun protocolRowName(row: ProtocolRow): ProtocolRowName = when {
    row.name.isNotBlank() -> ProtocolRowName.Named(row.name)
    row.fleets.isNotEmpty() -> ProtocolRowName.Uid(row.fleets.minOf { it.uid })
    else -> ProtocolRowName.Anonymous
}

/** The claimed badge's UID: the row's lowest; null when the network has not claimed. */
fun protocolBadgeUid(row: ProtocolRow): Long? = row.fleets.minOfOrNull { it.uid }

/**
 * The localized pieces of the tab's text: catalog strings, and the
 * platform's number and time formats (ProtocolLeaderboardTab's are
 * Context.getString, NumberFormat and DateUtils).
 *
 * The catalog's counts, ranks, UIDs and epochs are integer placeholders
 * (%d): the helpers below pass them as Long. Everything else they pass is
 * text this interface formatted (localizations STRINGS-LANDED, 07433bc).
 */
interface ProtocolText {
    fun string(@StringRes id: Int, vararg args: Any): String

    // an integer with the locale's grouping: "41,230"
    fun count(value: Long): String

    // a score with one decimal: "41.2"
    fun score(value: Double): String

    // a fraction as a whole percent: 0.12 is "12%"
    fun percent(fraction: Double): String

    // a fraction as a whole percent with its sign: 0.12 is "+12%", -0.08 is "-8%"
    fun signedPercent(fraction: Double): String

    // a share with one decimal: 0.018 is "1.8%"
    fun share(fraction: Double): String

    // a past time relative to now: "3 hours ago"
    fun relativeTime(timeMillis: Long): String
}

/** The numbers of [ProtocolText] in one locale, by java.text: plain JVM code the tests run as is. */
class ProtocolNumberFormat(
    private val locale: Locale,
) {
    fun count(value: Long): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun score(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)

    // the size of the fraction, whole, or to a tenth below 1% so a small
    // margin never reads 0%: as apple formats it
    fun percent(fraction: Double): String {
        val size = Math.abs(fraction)
        val underOne = 0.0 < size && size * 100 < 0.95
        return NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = if (underOne) 1 else 0
        }.format(size)
    }

    // "+12%" above the line, "\u221212%" below it (a minus sign, as apple writes it)
    fun signedPercent(fraction: Double): String =
        (if (0.0 <= fraction) "+" else "\u2212") + percent(fraction)

    fun share(fraction: Double): String = NumberFormat.getPercentInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(fraction)
}

fun protocolRowNameText(name: ProtocolRowName, text: ProtocolText): String = when (name) {
    is ProtocolRowName.Named -> name.name
    is ProtocolRowName.Uid -> protocolUidText(name.uid, text)
    ProtocolRowName.Anonymous -> text.string(R.string.anonymous)
}

/** "UID 81": the claimed badge, and an anonymous claimed network's name. */
fun protocolUidText(uid: Long, text: ProtocolText): String = text.string(R.string.leaderboard_protocol_uid, uid)

/** "of 41230 ranked networks", under the card's position. */
fun protocolOfRankedText(totalRanked: Long, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_of_ranked, totalRanked)

/** The validator's "#1 of 142 claimed" (Q9). */
fun protocolValidatorRankText(rank: Long, of: Long, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_detail_validator_rank_value, rank, of)

/** "#173" */
fun protocolPositionText(position: Long, text: ProtocolText): String = "#" + text.count(position)

/** "12% above the Top 200 line" or "8% below the Top 200 line". */
fun protocolMarginText(margin: Double, text: ProtocolText): String = if (0.0 <= margin) {
    text.string(R.string.leaderboard_protocol_above_line, text.percent(margin))
} else {
    text.string(R.string.leaderboard_protocol_below_line, text.percent(-margin))
}

/** "up 9 since the last update"; null when the position held. */
fun protocolTrendText(trend: Long, text: ProtocolText): String? = when {
    0L < trend -> text.string(R.string.leaderboard_protocol_trend_up, trend)
    trend < 0L -> text.string(R.string.leaderboard_protocol_trend_down, -trend)
    else -> null
}

fun protocolBannerText(banner: ProtocolBanner, text: ProtocolText): String = when (banner) {
    is ProtocolBanner.Stale -> text.string(R.string.leaderboard_protocol_banner_stale, text.relativeTime(banner.computedAtMillis))
    ProtocolBanner.ClaimingClosed -> text.string(R.string.leaderboard_protocol_banner_closed)
}

/** "Top 200 line · score 41.2", and what TalkBack reads for it. */
fun protocolLineText(score: Double, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_line, text.score(score))

fun protocolLineSpoken(score: Double, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_line_a11y, text.score(score))

/** The card's "why": prefixes and how many are shared, then the solo score (Q4). Counts only, never IPs. */
fun protocolWhyText(standing: ProtocolStanding, text: ProtocolText): String = text.string(
    R.string.leaderboard_protocol_why,
    standing.distinctPrefixes,
    standing.sharedPrefixes,
)

fun protocolSoloText(standing: ProtocolStanding, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_solo, text.score(standing.soloScore))

/** The chart's summary for TalkBack: "Projected rank over 90 days, now 173". */
fun protocolChartSpoken(position: Long, text: ProtocolText): String =
    text.string(R.string.leaderboard_protocol_chart_a11y, position)

/**
 * What TalkBack reads for a row: its position, name, score and trend, the
 * claimed UID, and that it is the caller's own or can't be measured yet.
 */
fun protocolRowSpoken(row: ProtocolRow, text: ProtocolText): String {
    val parts = mutableListOf<String>()
    if (row.isMe) {
        parts.add(text.string(R.string.leaderboard_protocol_you))
    }
    val name = protocolRowName(row)
    parts.add(protocolPositionText(row.position, text))
    parts.add(protocolRowNameText(name, text))
    parts.add(text.score(row.projectedScore))
    protocolTrendText(row.trend, text)?.let { parts.add(it) }
    // an anonymous claimed row's name is already its UID
    if (name !is ProtocolRowName.Uid) {
        protocolBadgeUid(row)?.let { parts.add(protocolUidText(it, text)) }
    }
    if (!row.measurable) {
        parts.add(text.string(R.string.leaderboard_protocol_label_not_measurable))
    } else if (row.isMe) {
        row.label?.let { parts.add(text.string(it.textRes)) }
    }
    return parts.joinToString(", ")
}

/** One label and value of the claimed-row detail sheet. */
data class ProtocolDetailFact(
    val label: String,
    val value: String,
)

/**
 * The detail sheet's facts for one fleet of a claimed row (LEADERTOP §6):
 * the projected rank, the validator's rank and score (Q9), the board's
 * replica score, the on-chain incentive with "stale" when flagged (Q10), the
 * generation and the epoch the claim is valid until. A value the server has
 * not measured is left out.
 */
fun protocolDetailFacts(row: ProtocolRow, fleet: ProtocolFleet, text: ProtocolText): List<ProtocolDetailFact> {
    val facts = mutableListOf<ProtocolDetailFact>()
    facts.add(
        ProtocolDetailFact(
            label = text.string(R.string.leaderboard_protocol_detail_projected_rank),
            value = protocolPositionText(row.position, text),
        )
    )
    val validatorRank = fleet.validatorRank
    if (validatorRank != null) {
        val of = fleet.validatorRankOf
        facts.add(
            ProtocolDetailFact(
                label = text.string(R.string.leaderboard_protocol_detail_validator_rank),
                value = if (of != null) {
                    protocolValidatorRankText(validatorRank, of, text)
                } else {
                    protocolPositionText(validatorRank, text)
                },
            )
        )
    }
    fleet.validatorScore?.let {
        facts.add(
            ProtocolDetailFact(
                label = text.string(R.string.leaderboard_protocol_detail_validator_score),
                value = text.score(it),
            )
        )
    }
    facts.add(
        ProtocolDetailFact(
            label = text.string(R.string.leaderboard_protocol_detail_replica_score),
            value = text.score(fleet.replicaScore),
        )
    )
    fleet.incentive?.let {
        val share = text.share(it)
        facts.add(
            ProtocolDetailFact(
                label = text.string(R.string.leaderboard_protocol_detail_incentive),
                value = if (fleet.incentiveStale) {
                    "$share · ${text.string(R.string.leaderboard_protocol_detail_stale)}"
                } else {
                    share
                },
            )
        )
    }
    facts.add(
        ProtocolDetailFact(
            label = text.string(R.string.leaderboard_protocol_detail_generation),
            value = text.count(fleet.generation),
        )
    )
    facts.add(
        ProtocolDetailFact(
            label = text.string(R.string.leaderboard_protocol_detail_valid_until),
            value = text.string(R.string.leaderboard_protocol_detail_epoch, fleet.validToEpoch),
        )
    )
    return facts
}

/**
 * The history the progress chart draws: the days in order, at most the last
 * 90, with no rank below 1. Duplicate days keep the last answer's rank.
 */
fun protocolChartPoints(history: List<ProtocolHistoryPoint>): List<ProtocolHistoryPoint> =
    history
        .filter { 0L < it.rank && it.day.isNotEmpty() }
        .associateBy { it.day }
        .values
        .sortedBy { it.day }
        .takeLast(90)

/**
 * The progress chart's vertical scale: rank 1 at the top. The range always
 * holds the head seats' line so it is drawn across, with a margin of a
 * tenth of the span so neither the line nor the ends sit on the edge.
 */
data class ProtocolChartScale(
    // the rank at the top edge
    val topRank: Double,
    // the rank at the bottom edge
    val bottomRank: Double,
) {
    /** 0 at the top edge, 1 at the bottom: up means a better rank. */
    fun fraction(rank: Double): Float {
        val span = bottomRank - topRank
        if (span <= 0.0) {
            return 0.5f
        }
        return ((rank - topRank) / span).toFloat().coerceIn(0f, 1f)
    }
}

fun protocolChartScale(points: List<ProtocolHistoryPoint>, headSeats: Long): ProtocolChartScale {
    val ranks = points.map { it.rank.toDouble() } + listOfNotNull(headSeats.takeIf { 0L < it }?.toDouble())
    val best = ranks.minOrNull() ?: 1.0
    val worst = ranks.maxOrNull() ?: 1.0
    val pad = maxOf(1.0, (worst - best) / 10.0)
    return ProtocolChartScale(
        topRank = maxOf(1.0, best - pad),
        bottomRank = worst + pad,
    )
}
