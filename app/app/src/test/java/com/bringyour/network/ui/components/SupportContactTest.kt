package com.bringyour.network.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reported defect: the feedback and settings screens offered only a Discord
 * invite, which is unreachable in some regions, so users there had no way to
 * reach support.
 */
class SupportContactTest {

    @Test
    fun everySupportEntryPointOffersTheEmail() {
        val entryPoints = mapOf(
            "feedback" to SupportContact.feedbackLinks,
            "settings" to SupportContact.settingsLinks,
            "api error" to SupportContact.apiErrorLinks,
        )

        for ((name, links) in entryPoints) {
            assertTrue("$name offers no support email", links.any { it.uri == "mailto:support@ur.io" })
            assertTrue("$name lost Discord", links.any { it.uri == SupportContact.DISCORD_INVITE })
        }
    }

    @Test
    fun theFeedbackSentenceLinksTheEmailAndDiscord() {
        // the english send_feedback_contact, formatted the way the feedback screen does
        val text = "Send us your feedback directly, email us at ${SupportContact.EMAIL}, " +
            "or join our ${SupportContact.DISCORD_NAME} for direct support."

        val spans = supportLinkSpans(text, SupportContact.feedbackLinks)

        assertEquals(
            listOf("support@ur.io" to "mailto:support@ur.io", "Discord" to SupportContact.DISCORD_INVITE),
            spans.map { text.substring(it.start, it.end) to it.uri },
        )
    }

    @Test
    fun aLinkTextMissingFromATranslationGetsNoSpan() {
        val text = "Escríbenos a ${SupportContact.EMAIL}."

        val spans = supportLinkSpans(text, SupportContact.apiErrorLinks)

        assertEquals(listOf("mailto:support@ur.io"), spans.map { it.uri })
        assertTrue(spans.all { 0 <= it.start && it.end <= text.length })
    }
}
