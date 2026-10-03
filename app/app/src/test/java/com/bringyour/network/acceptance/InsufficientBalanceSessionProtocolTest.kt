package com.bringyour.network.acceptance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InsufficientBalanceSessionProtocolTest {
    @Test
    fun `commands parse the closed verb set`() {
        assertEquals(
            InsufficientBalanceCommand("7", InsufficientBalanceVerb.OBSERVE, ""),
            parseInsufficientBalanceCommand("7|observe|\n"),
        )
        assertEquals(
            InsufficientBalanceCommand("12", InsufficientBalanceVerb.KILL_SWITCH, "off"),
            parseInsufficientBalanceCommand("12|kill-switch|off"),
        )
        assertEquals(
            InsufficientBalanceVerb.PRESS_DISCONNECT,
            parseInsufficientBalanceCommand("3|press-disconnect|").verb,
        )
    }

    @Test
    fun `malformed or partial commands are rejected`() {
        for (text in listOf(
            "",
            "7|observe",
            "7|obs|",
            "7|upgrade|",
            "7|kill-switch|",
            "7|kill-switch|maybe",
            "7|observe|now",
            "7/1|observe|",
            "7|observe||",
        )) {
            assertThrows(text, IllegalArgumentException::class.java) {
                parseInsufficientBalanceCommand(text)
            }
        }
    }

    @Test
    fun `a notice still showing is counted once`() {
        val counter = InsufficientBalanceNotificationCounter()
        counter.sample(listOf(1 to 100L))
        assertEquals(0, counter.count())
        counter.sample(listOf(1 to 100L, 102 to 5_000L))
        counter.sample(listOf(1 to 100L, 102 to 5_000L))
        counter.sample(listOf(102 to 5_000L))
        assertEquals(1, counter.count())
    }

    @Test
    fun `a repost counts even after the notice was dismissed`() {
        val counter = InsufficientBalanceNotificationCounter()
        counter.sample(listOf(102 to 5_000L))
        // dismissed, then posted again in the same episode: a defect to report
        counter.sample(emptyList())
        counter.sample(listOf(102 to 9_000L))
        assertEquals(2, counter.count())
    }

    @Test
    fun `observation fields use the host's keys in order`() {
        val fields = InsufficientBalanceObservation(
            connectRequested = true,
            connected = false,
            alert = true,
            disconnectVisible = true,
            upgradeVisible = false,
            notifications = 1,
        ).statusFields()
        assertEquals(
            "command=4\nstate=complete\n" +
                "connect_requested=true\nconnected=false\nalert=true\n" +
                "disconnect_visible=true\nupgrade_visible=false\nnotifications=1\n",
            insufficientBalanceStatusText("4", "complete", fields),
        )
    }

    @Test
    fun `an error cannot forge a status line`() {
        assertEquals(
            "command=5\nstate=failed\nerror=timed out state=complete\n",
            insufficientBalanceStatusText("5", "failed", listOf("error" to "timed out\nstate=complete")),
        )
        assertEquals(240, insufficientBalanceStatusValue("x".repeat(500)).length)
        assertThrows(IllegalArgumentException::class.java) {
            insufficientBalanceStatusText("5", "complete", listOf("Bad Key" to "x"))
        }
    }
}
