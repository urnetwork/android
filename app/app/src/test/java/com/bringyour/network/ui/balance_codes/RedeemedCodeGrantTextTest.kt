package com.bringyour.network.ui.balance_codes

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The redeemed codes list's Data column (GIFTOFPRIVACY.md 10.5, "After
 * redemption, everywhere"): a gift code reads "A year of Pro", never the
 * "+0.00 GiB" its 0 bytes would read; a data code keeps its data.
 */
class RedeemedCodeGrantTextTest {

    private val proYearText = "A year of Pro"

    private val gib = { bytes: Long -> "${bytes / (1024L * 1024 * 1024)} GiB" }

    @Test
    fun aGiftCodeReadsAYearOfPro() {
        assertEquals(
            "A year of Pro",
            redeemedCodeGrantText(kind = "pro_gift", byteCount = 0L, proYearText = proYearText, formatBytes = { error("a gift names no data") }),
        )
    }

    @Test
    fun aDataCodeReadsTheDataItAdded() {
        assertEquals("+5 GiB", redeemedCodeGrantText(kind = "data", byteCount = 5L * 1024 * 1024 * 1024, proYearText = proYearText, formatBytes = gib))
    }

    @Test
    fun aCodeOfNoKnownKindReadsAsData() {
        // the sdk reads a code from a server that predates gift codes as data;
        // any other kind keeps the data column too
        for (kind in listOf("", null, "something_new")) {
            assertEquals("+5 GiB", redeemedCodeGrantText(kind = kind, byteCount = 5L * 1024 * 1024 * 1024, proYearText = proYearText, formatBytes = gib))
        }
    }
}
