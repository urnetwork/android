package com.bringyour.network.ui.leaderboard

import com.bringyour.network.R
import com.bringyour.network.ui.wallet.top200ProjectedText
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab's text through the real English catalog (res/values/strings.xml,
 * generated from localizations) and the real number formats, formatted the
 * way Context.getString formats it. Each count, rank, UID and epoch is an
 * integer placeholder (%d) and must be passed as a number; every other value
 * is text the app formatted. A value of the wrong type throws here, as it
 * would on a device, rather than reading wrong.
 */
class ProtocolLeaderboardCatalogTest {

    // gradle runs unit tests with the module directory as the working directory
    private fun moduleFile(path: String): File {
        val file = listOf("", "app/", "app/app/")
            .map { File(it + path) }
            .firstOrNull { it.isFile }
        assertNotNull("$path not found", file)
        return file!!
    }

    // R.string's ids by name, and the english catalog's text by name
    private val names: Map<Int, String> = R.string::class.java.fields.associate { it.getInt(null) to it.name }

    private val english: Map<String, String> = run {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(moduleFile("src/main/res/values/strings.xml")).getElementsByTagName("string")
        (0 until nodes.length).associate { i ->
            val element = nodes.item(i) as org.w3c.dom.Element
            // the escapes an android string resource takes
            element.getAttribute("name") to element.textContent
                .replace("\\'", "'")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
        }
    }

    private val numbers = ProtocolNumberFormat(Locale.US)

    private val text = object : ProtocolText {
        override fun string(id: Int, vararg args: Any): String {
            val template = english[names[id]]
            assertNotNull("not in the catalog: ${names[id]}", template)
            return String.format(Locale.US, template!!, *args)
        }

        override fun count(value: Long) = numbers.count(value)

        override fun score(value: Double) = numbers.score(value)

        override fun percent(fraction: Double) = numbers.percent(fraction)

        override fun signedPercent(fraction: Double) = numbers.signedPercent(fraction)

        override fun share(fraction: Double) = numbers.share(fraction)

        override fun relativeTime(timeMillis: Long) = "3 hours ago"
    }

    private fun fleet() = ProtocolFleet(
        uid = 81,
        generation = 2,
        validToEpoch = 16,
        replicaScore = 312.4,
        validatorScore = 312.4,
        validatorRank = 1,
        validatorRankOf = 142,
        selected = true,
        incentive = 0.018,
        incentiveStale = true,
    )

    private fun row(
        position: Long = 173,
        isMe: Boolean = false,
        fleets: List<ProtocolFleet> = listOf(),
        measurable: Boolean = true,
        trend: Long = 9,
    ) = ProtocolRow(
        position = position,
        name = "",
        tag = "",
        projectedScore = 41.2,
        margin = 0.12,
        trend = trend,
        label = ProtocolLabel.ESTIMATE,
        measurable = measurable,
        claimPrompt = true,
        isMe = isMe,
        fleets = fleets,
    )

    @Test
    fun theCardReadsInEnglish() {
        assertEquals("of 41230 ranked networks", protocolOfRankedText(41_230, text))
        assertEquals("12% above the Top 200 line", protocolMarginText(0.12, text))
        assertEquals("8% below the Top 200 line", protocolMarginText(-0.08, text))
        assertEquals("up 9 since the last update", protocolTrendText(9, text))
        assertEquals("down 2 since the last update", protocolTrendText(-2, text))
        assertEquals("Projected rank over 90 days, now 173", protocolChartSpoken(173, text))
        val standing = ProtocolStanding(
            position = 173,
            totalRanked = 41_230,
            headSeats = 200,
            margin = 0.12,
            trend = 9,
            label = ProtocolLabel.ESTIMATE,
            tag = "",
            name = "",
            namePublic = false,
            history = listOf(),
            distinctPrefixes = 18,
            sharedPrefixes = 3,
            soloScore = 21.4,
            prompt = null,
            notMeasurable = null,
            fleets = listOf(),
        )
        assertEquals("If you claimed alone today: 21.4.", protocolSoloText(standing, text))
        // the why's english is being reworded; its two counts stay
        val why = protocolWhyText(standing, text)
        assertTrue(why, why.contains("18") && why.contains("3") && !why.contains("%"))
    }

    @Test
    fun theListAndTheBannersReadInEnglish() {
        assertEquals("Top 200 line · score 41.2", protocolLineText(41.2, text))
        assertEquals("Top 200 line, score 41.2", protocolLineSpoken(41.2, text))
        assertEquals("UID 81", protocolUidText(81, text))
        assertEquals("UID 77", protocolRowNameText(ProtocolRowName.Uid(77), text))
        assertEquals("Anonymous", protocolRowNameText(ProtocolRowName.Anonymous, text))
        assertEquals("Rankings last updated 3 hours ago.", protocolBannerText(ProtocolBanner.Stale(1L), text))
        assertEquals(
            "Claiming isn't open yet. You can see where you'd rank now.",
            protocolBannerText(ProtocolBanner.ClaimingClosed, text),
        )
        assertEquals("You, #173, Anonymous, 41.2, up 9 since the last update, Estimate", protocolRowSpoken(row(isMe = true), text))
        assertEquals(
            "#202, UID 81, 41.2, Not measurable",
            protocolRowSpoken(row(position = 202, fleets = listOf(fleet()), measurable = false, trend = 0), text),
        )
    }

    @Test
    fun theDetailSheetReadsInEnglish() {
        val claimed = row(position = 1, fleets = listOf(fleet()))
        assertEquals(
            listOf(
                ProtocolDetailFact("Projected rank", "#1"),
                ProtocolDetailFact("Validator rank", "#1 of 142 claimed"),
                ProtocolDetailFact("Validator score", "312.4"),
                ProtocolDetailFact("Board replica score", "312.4"),
                ProtocolDetailFact("On-chain incentive", "1.8% · stale"),
                ProtocolDetailFact("Generation", "2"),
                ProtocolDetailFact("Claim valid until", "Epoch 16"),
            ),
            protocolDetailFacts(claimed, claimed.fleets[0], text),
        )
    }

    @Test
    fun theEarningsTileReadsItsMarginWithItsSign() {
        assertEquals("projected · +12%", top200ProjectedText(0.12, text))
        // a minus sign, as apple writes it
        assertEquals("projected · \u22128%", top200ProjectedText(-0.08, text))
        assertEquals("+0%", numbers.signedPercent(0.0))
        assertEquals("1,234", numbers.count(1_234))
        assertFalse(numbers.score(41.25).isEmpty())
    }

    @Test
    fun aSmallMarginNeverReadsZero() {
        assertEquals("0.4% above the Top 200 line", protocolMarginText(0.004, text))
        assertEquals("0.4% below the Top 200 line", protocolMarginText(-0.004, text))
        assertEquals("1% above the Top 200 line", protocolMarginText(0.0096, text))
        assertEquals("0% above the Top 200 line", protocolMarginText(0.0, text))
    }
}
