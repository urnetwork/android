package com.bringyour.network.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Settlement v2 tile beside the v1 claims: what each read shows, with and
 * without vault2's address configured. UX-n names the APPS-SPEC.md UX rule, in
 * its order (UX-2 the account lines, UX-3 unavailable, UX-4 the Claim action,
 * UX-5 before activation).
 */
class SnLedgerTileTest {

    // 5.12 earned, 1.00 paid, 4.12 claimable; a pending root holds 5.87
    private val account = SnLedgerAccountState(
        noId = 1,
        index = 42,
        finalizedRao = 5_120_000_000,
        claimableRao = 4_120_000_000,
        paidRao = 1_000_000_000,
        pending = true,
        pendingRao = 5_870_000_000,
    )

    private val paidUp = account.copy(claimableRao = 0, paidRao = 5_120_000_000, pending = false, pendingRao = 0)

    @Test
    fun `an account shows claimable, paid, earned and the pending increase`() {
        // UX-2
        assertEquals(
            SnLedgerAccountLines(
                noId = 1,
                claimableRao = 4_120_000_000,
                paidRao = 1_000_000_000,
                earnedRao = 5_120_000_000,
                pendingIncreaseRao = 750_000_000,
            ),
            account.lines()
        )
    }

    @Test
    fun `pending shows only what a pending root adds`() {
        // UX-2: X = pending less finalized, and only when positive
        assertNull(account.copy(pending = false, pendingRao = 0).lines().pendingIncreaseRao)
        assertNull(account.copy(pendingRao = account.finalizedRao).lines().pendingIncreaseRao)
        assertNull(account.copy(pendingRao = account.finalizedRao - 1).lines().pendingIncreaseRao)
        assertEquals(1L, account.copy(pendingRao = account.finalizedRao + 1).lines().pendingIncreaseRao)
        // an account only in the pending root: nothing finalized yet
        val onlyPending = SnLedgerAccountState(1, 43, 0, 0, 0, pending = true, pendingRao = 2_000_000_000)
        assertEquals(
            SnLedgerAccountLines(1, claimableRao = 0, paidRao = 0, earnedRao = 0, pendingIncreaseRao = 2_000_000_000),
            onlyPending.lines()
        )
    }

    @Test
    fun `claimable balances can be claimed once vault2 is configured`() {
        // UX-4
        assertEquals(
            SnLedgerTile(listOf(account.lines()), unavailable = false, showClaim = true, claimEnabled = true),
            snLedgerTile(SnLedgerLoad.Loaded(listOf(account)), claimConfigured = true)
        )
    }

    @Test
    fun `nothing claimable keeps Claim disabled`() {
        // UX-4: Claim needs ClaimableRao > 0
        assertEquals(
            SnLedgerTile(listOf(paidUp.lines()), unavailable = false, showClaim = true, claimEnabled = false),
            snLedgerTile(SnLedgerLoad.Loaded(listOf(paidUp)), claimConfigured = true)
        )
        // the ledger has no account for the coldkey yet: a loaded empty list, not unavailable
        assertEquals(
            SnLedgerTile(emptyList(), unavailable = false, showClaim = true, claimEnabled = false),
            snLedgerTile(SnLedgerLoad.Loaded(emptyList()), claimConfigured = true)
        )
    }

    @Test
    fun `every account is shown and any claimable one enables Claim`() {
        // UX-2: one block per pool
        val other = paidUp.copy(noId = 2, index = 7)
        assertEquals(
            SnLedgerTile(listOf(other.lines(), account.lines()), unavailable = false, showClaim = true, claimEnabled = true),
            snLedgerTile(SnLedgerLoad.Loaded(listOf(other, account)), claimConfigured = true)
        )
    }

    @Test
    fun `an unreadable ledger is unavailable, never zero, and Claim stays disabled`() {
        // UX-3
        assertEquals(
            SnLedgerTile(accounts = null, unavailable = true, showClaim = true, claimEnabled = false),
            snLedgerTile(SnLedgerLoad.Unavailable, claimConfigured = true)
        )
    }

    @Test
    fun `while loading there are no amounts and Claim is disabled`() {
        // UX-3: no zero before the first read either
        assertEquals(
            SnLedgerTile(accounts = null, unavailable = false, showClaim = true, claimEnabled = false),
            snLedgerTile(SnLedgerLoad.Loading, claimConfigured = true)
        )
    }

    @Test
    fun `before vault2 is configured the balances show without Claim`() {
        // UX-5
        assertEquals(
            SnLedgerTile(listOf(account.lines()), unavailable = false, showClaim = false, claimEnabled = false),
            snLedgerTile(SnLedgerLoad.Loaded(listOf(account)), claimConfigured = false)
        )
    }

    @Test
    fun `before vault2 is configured the tile hides unless the ledger returned accounts`() {
        // UX-5: the endpoint returned none (an older server answers 404, which is unavailable)
        assertNull(snLedgerTile(SnLedgerLoad.Loaded(emptyList()), claimConfigured = false))
        assertNull(snLedgerTile(SnLedgerLoad.Unavailable, claimConfigured = false))
        assertNull(snLedgerTile(SnLedgerLoad.Loading, claimConfigured = false))
    }
}
