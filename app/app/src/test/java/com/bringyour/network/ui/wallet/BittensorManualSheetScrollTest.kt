package com.bringyour.network.ui.wallet

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manual proof sheet (Talisman and TAO.com on Android) as laid out in the
 * sources: the sheet needs a device, so this reads BittensorProofSheets.kt;
 * BittensorProofSheetsTest (androidTest) scrolls the real sheet. A payout
 * consent is about 2 KB of JSON, and in a sheet that did not scroll it filled
 * the screen: Copy, the address and signature fields and Continue were below
 * it, out of reach, so a consent could never be signed by hand (support inbox
 * 8058, 8073).
 */
class BittensorManualSheetScrollTest {

    private val sheets = File("src/main/java/com/bringyour/network/ui/wallet/BittensorProofSheets.kt").readText()

    // the brace-matched body of the block `start` opens (it ends in '{')
    private fun blockAfter(source: String, start: String): String {
        val from = source.indexOf(start)
        assertTrue("missing: $start", 0 <= from)
        var i = from + start.length
        var depth = 1
        while (0 < depth) {
            when (source[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return source.substring(from + start.length, i - 1)
    }

    @Test
    fun `the manual sheet scrolls its message, fields and continue`() {
        val signing = blockAfter(sheets, "is BittensorProofStage.Signing -> {")
        val sheet = blockAfter(signing, "containerColor = SheetBlack,\n            ) {")
        // the sheet's one column, from its modifier to its body
        val column = sheet.substring(sheet.indexOf("Column("), sheet.indexOf(") {", sheet.indexOf("Column(")))
        assertTrue(column, column.contains(".verticalScroll(rememberScrollState())"))
        val body = sheet.substring(sheet.indexOf(") {", sheet.indexOf("Column(")))
        assertTrue(body.contains("Text(s.session.message"))
        assertTrue(body.contains("onValueChange = { flow.updateSignature(it.text) }"))
        assertTrue(body.contains("onClick = onSubmit"))
    }
}
