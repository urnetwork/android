package com.bringyour.network.ui.upgrade

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.w3c.dom.Element

/**
 * The offer headline, the offer button and the trial terms put a count in
 * front of a noun ("3 months of Pro, free", "14 days free, then ..."). They
 * were plain strings, so every language got one fixed noun form: Russian
 * read "1 месяца", "5 месяца", English "1 days free". They are plural
 * resources now and the call sites pass the count as the quantity.
 *
 * Reads the module's generated resources; no device.
 */
class OfferCountPluralsTest {

    private val res = File("src/main/res")

    private fun plurals(dir: String, name: String): Map<String, String>? {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(File(res, dir), "strings.xml")).getElementsByTagName("plurals")
        val node = (0 until nodes.length).map { nodes.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == name } ?: return null
        val items = node.getElementsByTagName("item")
        return (0 until items.length).map { items.item(it) as Element }
            .associate { it.getAttribute("quantity") to it.textContent }
    }

    private val keys = listOf(
        "offer_months_free_headline",
        "offer_cta_start_trial_months_free",
        "offer_terms_first_year",
        "plan_terms_yearly",
    )

    @Test
    fun `offer counts are plural resources in english`() {
        for (key in keys) {
            val forms = plurals("values", key)
            assertNotNull("$key is not a plural resource", forms)
            assertEquals("$key english forms", setOf("one", "other"), forms!!.keys)
        }
        assertEquals("%1\$d month of Pro, free", plurals("values", "offer_months_free_headline")!!["one"])
        assertEquals("%1\$d day free, then %2\$s/year. Cancel anytime.", plurals("values", "plan_terms_yearly")!!["one"])
    }

    @Test
    fun `russian has a noun form for each count category`() {
        val headline = plurals("values-ru", "offer_months_free_headline")
        assertNotNull(headline)
        assertEquals("%1\$d месяц Pro бесплатно", headline!!["one"])
        assertEquals("%1\$d месяца Pro бесплатно", headline["few"])
        assertEquals("%1\$d месяцев Pro бесплатно", headline["many"])

        val terms = plurals("values-ru", "plan_terms_yearly")
        assertNotNull(terms)
        assertEquals("%1\$d день бесплатно, далее %2\$s/год. Отмена в любой момент.", terms!!["one"])
        assertEquals("%1\$d дня бесплатно, далее %2\$s/год. Отмена в любой момент.", terms["few"])
        assertEquals("%1\$d дней бесплатно, далее %2\$s/год. Отмена в любой момент.", terms["many"])

        for (key in keys) {
            val forms = plurals("values-ru", key)
            assertNotNull("$key is not a plural resource in ru", forms)
            assertEquals("$key ru forms", setOf("one", "few", "many", "other"), forms!!.keys)
        }
    }
}
