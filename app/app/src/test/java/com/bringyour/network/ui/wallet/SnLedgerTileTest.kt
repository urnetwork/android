package com.bringyour.network.ui.wallet

import com.bringyour.network.R
import com.bringyour.sdk.Sdk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settlement v2 tile beside the v1 claims: what each read shows, with and
 * without vault2's address configured. UX-n names the APPS-SPEC.md UX rule, in
 * its order (UX-2 the account lines, UX-3 unavailable, UX-4 the Claim action,
 * UX-5 before activation). CTF-n names the rule of capture the flag's claim
 * flow (sn CAPTURETHEFLAG.md §10): CTF-1 its account is its own row, named by
 * its kind; CTF-2 an unavailable pool never hides the others; CTF-3 one action
 * claims both pools; CTF-4 old servers read as before; CTF-5 a line for each
 * unclaimed win; CTF-6 Below the claim minimum for any pool's account under
 * the floor, alone or not, and no claim offered for it (owner, 2026-11-11).
 *
 * Sdk's constants are compile-time constants, which kotlin inlines: these
 * tests need no native library.
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

    // operator 1's capture the flag pool is vault2 pool 2^32 + 1: two slots won, 0.0961 claimable
    private val ctfNoId = Sdk.SnLedgerCtfPoolIdBase + 1
    private val ctf = SnLedgerAccountState(ctfNoId, 3, 96_051_320, 96_051_320, 0, false, 0, kind = Sdk.SnLedgerPoolKindCtf)
    private val ctfPool = SnLedgerPool(ctfNoId, Sdk.SnLedgerPoolKindCtf)
    private val pool1 = SnLedgerPool(1, Sdk.SnLedgerPoolKindPool)

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

    // ---- capture the flag

    @Test
    fun `capture the flag's account is its own block beside the pool's`() {
        // CTF-1
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf)), claimConfigured = true)!!
        assertEquals(listOf(account.lines(), ctf.lines()), tile.accounts)
        val flag = tile.accounts!![1]
        assertEquals(Sdk.SnLedgerPoolKindCtf, flag.kind)
        assertEquals(96_051_320L, flag.claimableRao)
        assertEquals(listOf(pool1, ctfPool), tile.pools)
        assertTrue(tile.claimEnabled)
    }

    @Test
    fun `capture the flag's account is named by its kind, and the pool beside it the provider pool`() {
        // CTF-1: sn_ctf_pool and sn_ledger_provider_pool in place of Pool {no_id}
        val inView = listOf(pool1, ctfPool)
        assertEquals(SnLedgerPoolName.CaptureTheFlag, snLedgerPoolName(ctfPool, inView))
        assertEquals(SnLedgerPoolName.ProviderPool, snLedgerPoolName(pool1, inView))
        // capture the flag's pool counts beside the pool while it could not be read too
        assertEquals(SnLedgerPoolName.ProviderPool, snLedgerPoolName(pool1, listOf(pool1, SnLedgerPool(ctfNoId))))
        // alone, capture the flag's account still names itself
        assertEquals(SnLedgerPoolName.CaptureTheFlag, snLedgerPoolName(ctfPool, listOf(ctfPool)))
        assertTrue(snLedgerNamesPools(listOf(ctfPool)))
        // two operator pools beside it keep their numbers: neither is the provider pool
        val pool2 = SnLedgerPool(2, Sdk.SnLedgerPoolKindPool)
        assertEquals(SnLedgerPoolName.Pool(2), snLedgerPoolName(pool2, listOf(pool1, pool2, ctfPool)))
    }

    @Test
    fun `without capture the flag the pools read as before`() {
        // CTF-4: the sdk reads an entry without kind as a pool, so an old server's
        // pools are Pool {no_id}, and a lone pool is not named at all
        assertEquals(Sdk.SnLedgerPoolKindPool, account.kind)
        assertEquals(SnLedgerPoolName.Pool(1), snLedgerPoolName(pool1, listOf(pool1)))
        assertFalse(snLedgerNamesPools(listOf(pool1)))
        val pool2 = SnLedgerPool(2)
        assertEquals(SnLedgerPoolName.Pool(1), snLedgerPoolName(pool1, listOf(pool1, pool2)))
        assertEquals(SnLedgerPoolName.Pool(2), snLedgerPoolName(pool2, listOf(pool1, pool2)))
        assertTrue(snLedgerNamesPools(listOf(pool1, pool2)))
        // and Claim reads Claim
        val other = account.copy(noId = 2, index = 7)
        assertFalse(snLedgerTile(SnLedgerLoad.Loaded(listOf(account, other)), claimConfigured = true)!!.claimBoth)
    }

    @Test
    fun `a pool's kind follows its id when the entry names none`() {
        // the sdk's rule: capture the flag's ids start at 2^32, and no operator pool's does
        assertEquals(Sdk.SnLedgerPoolKindPool, snLedgerPoolKindOf(1))
        assertEquals(Sdk.SnLedgerPoolKindPool, snLedgerPoolKindOf(Sdk.SnLedgerCtfPoolIdBase - 1))
        assertEquals(Sdk.SnLedgerPoolKindCtf, snLedgerPoolKindOf(Sdk.SnLedgerCtfPoolIdBase))
        assertEquals(Sdk.SnLedgerPoolKindCtf, snLedgerPoolKindOf(4_294_967_297))
        // the sdk's kind wins over the id
        assertFalse(SnLedgerPool(ctfNoId, Sdk.SnLedgerPoolKindPool).isCtf)
    }

    @Test
    fun `one claim takes both while both have something to claim`() {
        // CTF-3: Claim both (sn_ledger_claim_both)
        assertTrue(snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf)), claimConfigured = true)!!.claimBoth)
        // one of them paid up: Claim
        assertFalse(snLedgerTile(SnLedgerLoad.Loaded(listOf(paidUp, ctf)), claimConfigured = true)!!.claimBoth)
        assertFalse(snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf.copy(claimableRao = 0))), claimConfigured = true)!!.claimBoth)
        // not before vault2 is configured, when there is no Claim
        assertFalse(snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf)), claimConfigured = false)!!.claimBoth)
        assertTrue(snLedgerClaimsBoth(listOf(pool1, ctfPool)))
        assertFalse(snLedgerClaimsBoth(listOf(pool1, SnLedgerPool(2), ctfPool)))
        assertFalse(snLedgerClaimsBoth(listOf(ctfPool)))
    }

    @Test
    fun `a pool the ledger could not read is unavailable beside the others`() {
        // CTF-2: its own row, never zero, and it hides no account
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(account), unavailablePools = listOf(ctfPool)), claimConfigured = true)!!
        assertEquals(listOf(account.lines()), tile.accounts)
        assertEquals(listOf(ctfPool), tile.unavailablePools)
        assertFalse(tile.unavailable)
        assertTrue(tile.claimEnabled)
        assertFalse(tile.claimBoth)
        assertEquals(listOf(pool1, ctfPool), tile.pools)
        assertTrue(snLedgerNamesPools(tile.pools))
        // every pool unavailable: their rows, no zero, nothing to claim
        val none = snLedgerTile(SnLedgerLoad.Loaded(emptyList(), unavailablePools = listOf(pool1, ctfPool)), claimConfigured = true)!!
        assertEquals(emptyList<SnLedgerAccountLines>(), none.accounts)
        assertEquals(listOf(pool1, ctfPool), none.unavailablePools)
        assertFalse(none.claimEnabled)
        // a whole read that failed stays the tile's Unavailable
        assertTrue(snLedgerTile(SnLedgerLoad.Unavailable, claimConfigured = true)!!.unavailable)
    }

    @Test
    fun `the read's pools name the claim dialog's rows as the tile's`() {
        assertEquals(listOf(pool1, ctfPool), snLedgerPools(SnLedgerLoad.Loaded(listOf(account), unavailablePools = listOf(ctfPool))))
        assertEquals(emptyList<SnLedgerPool>(), snLedgerPools(SnLedgerLoad.Unavailable))
        assertEquals(emptyList<SnLedgerPool>(), snLedgerPools(SnLedgerLoad.Loading))
    }

    @Test
    fun `an account under the relay's transfer floor says so, and Claim both needs both above it`() {
        // CTF-6, sn_ledger_pool_under_floor: ClaimableRao < TransferFloorRao
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf)), claimConfigured = true)!!
        val floored = tile.withTransferFloor(100_000_000)
        assertEquals(listOf(false, true), floored.accounts!!.map { it.underFloor })
        assertFalse(floored.claimBoth)
        // Claim stays: the pool's account can be claimed
        assertTrue(floored.claimEnabled)
        // at or above the floor, and nothing to claim, is not under it
        assertEquals(listOf(false, false), tile.withTransferFloor(96_051_320).accounts!!.map { it.underFloor })
        assertTrue(tile.withTransferFloor(96_051_320).claimBoth)
        assertFalse(snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf.copy(claimableRao = 0))), claimConfigured = true)!!
            .withTransferFloor(100_000_000).accounts!![1].underFloor)
        // no price, no floor
        assertEquals(tile, tile.withTransferFloor(0))
    }

    @Test
    fun `the tile and its dialog agree on Claim both beside a second operator pool`() {
        // CTF-3, CTF-6: two operator pools and capture the flag's. With the second pool's account
        // under the floor the claim moves the first pool's and capture the flag's: Claim both, on
        // the tile as in the dialog, which decides it from the accounts that move (Ready.takes)
        val small = account.copy(noId = 2, index = 7, claimableRao = 48_025_660)
        val load = SnLedgerLoad.Loaded(listOf(account, small, ctf))
        fun tileAt(floor: Long) =
            snLedgerTileWithRelay(true, snLedgerTile(load, claimConfigured = true), SnClaimStatusState(transferFloorRao = floor, showsTile = true))!!
        fun dialogAt(floor: Long) = snLedgerClaimDialog(load, gasKey = null, gasTao = 0.124, transferFloorRao = floor) as SnLedgerClaimDialogState.Ready
        val tile = tileAt(90_000_000)
        assertEquals(listOf(false, true, false), tile.accounts!!.map { it.underFloor })
        assertTrue(tile.claimBoth)
        assertTrue(snLedgerClaimsBoth(dialogAt(90_000_000).takes.map { it.pool }))
        // the same at every floor: three that move, the pool's alone, or none
        for (floor in listOf<Long>(0, 48_025_660, 48_025_661, 96_051_320, 96_051_321, 4_120_000_000, 4_120_000_001)) {
            assertEquals(floor.toString(), snLedgerClaimsBoth(dialogAt(floor).takes.map { it.pool }), tileAt(floor).claimBoth)
        }
        assertFalse(tileAt(0).claimBoth)
        assertFalse(tileAt(96_051_321).claimBoth)
        // no Claim before vault2 is configured, so no Claim both either
        val unconfigured = snLedgerTile(load, claimConfigured = false)!!.withTransferFloor(90_000_000)
        assertFalse(unconfigured.showClaim)
        assertFalse(unconfigured.claimBoth)
    }

    @Test
    fun `with every account under the floor no claim is offered`() {
        // CTF-6: the runtime refuses each (AmountTooLow), so Claim is not offered for them
        val small = account.copy(claimableRao = 48_025_660)
        val floored = snLedgerTile(SnLedgerLoad.Loaded(listOf(small, ctf)), claimConfigured = true)!!.withTransferFloor(100_000_000)
        assertEquals(listOf(true, true), floored.accounts!!.map { it.underFloor })
        assertTrue(floored.showClaim)
        assertFalse(floored.claimEnabled)
        assertFalse(floored.claimBoth)
        // a pool unread beside them changes nothing: no account can move
        val partial = snLedgerTile(SnLedgerLoad.Loaded(listOf(small), unavailablePools = listOf(ctfPool)), claimConfigured = true)!!
        assertFalse(partial.withTransferFloor(100_000_000).claimEnabled)
        // without the relay's price the claim's own preflight decides, as before
        assertTrue(snLedgerTileWithRelay(true, partial, null)!!.claimEnabled)
        // the floor never turns on a Claim the tile did not offer
        val before = snLedgerTile(SnLedgerLoad.Loaded(listOf(account)), claimConfigured = false)!!
        assertFalse(before.withTransferFloor(100_000_000).claimEnabled)
    }

    @Test
    fun `the wallet's tile takes the relay's transfer floor`() {
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(account, ctf)), claimConfigured = true)!!
        val relay = SnClaimStatusState(transferFloorRao = 100_000_000, showsTile = true)
        assertEquals(tile.withTransferFloor(100_000_000), snLedgerTileWithRelay(true, tile, relay))
        // without an answer there is no floor to apply
        assertEquals(tile, snLedgerTileWithRelay(true, tile, null))
        assertEquals(tile, snLedgerTileWithRelay(true, tile, relay.copy(unavailable = true)))
    }

    @Test
    fun `a lone pool's account under the floor says so on any server, and offers no claim`() {
        // CTF-6 (owner, 2026-11-11): Below the claim minimum is not capture the flag's alone.
        // Today's relay already sends transfer_floor_rao, and the runtime refuses a claim under
        // it on any server, so a lone pool's account says so as one beside capture the flag does
        val small = account.copy(claimableRao = 48_025_660)
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(small)), claimConfigured = true)!!
        val relay = SnClaimStatusState(transferFloorRao = 100_000_000, showsTile = true)
        val floored = snLedgerTileWithRelay(true, tile, relay)!!
        assertEquals(tile.withTransferFloor(100_000_000), floored)
        assertEquals(listOf(true), floored.accounts!!.map { it.underFloor })
        assertFalse(floored.claimEnabled)
        // two operator pools, no capture the flag: the same
        val two = snLedgerTile(SnLedgerLoad.Loaded(listOf(small, account.copy(noId = 2, index = 7))), claimConfigured = true)!!
            .withTransferFloor(100_000_000)
        assertEquals(listOf(true, false), two.accounts!!.map { it.underFloor })
        assertTrue(two.claimEnabled)
        assertFalse(two.claimBoth)
        // the claim sheet's row says it too
        val row = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = 1, claimableRao = 48_025_660)
        val status = relay.copy(coldkeySs58 = "5Ab", accounts = listOf(row))
        assertEquals(SnClaimRelayRowStatus.UNDER_FLOOR, snClaimRelayRowStatus(row, status.transferFloorRao))
        // at or above the floor it is the relay's Pending, as before
        assertEquals(SnClaimRelayRowStatus.PENDING, snClaimRelayRowStatus(row.copy(claimableRao = 100_000_000), status.transferFloorRao))
        // with no price there is no floor, and the tile reads as before
        assertEquals(tile, snLedgerTileWithRelay(true, tile, relay.copy(transferFloorRao = 0)))
        assertTrue(snLedgerUnderFloor(1, 2))
        assertFalse(snLedgerUnderFloor(2, 2))
        assertFalse(snLedgerUnderFloor(0, 2))
        assertFalse(snLedgerUnderFloor(1, 0))
    }

    @Test
    fun `the claim sheet's rows say Below the claim minimum under the floor`() {
        val row = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = ctfNoId, claimableRao = 48_025_660, kind = Sdk.SnLedgerPoolKindCtf)
        val status = SnClaimStatusState(coldkeySs58 = "5Ab", transferFloorRao = 100_000_000, accounts = listOf(row))
        assertEquals(SnClaimRelayRowStatus.UNDER_FLOOR, snClaimRelayRowStatus(row, status.transferFloorRao))
        assertEquals(SnClaimRelayRowStatus.PENDING, snClaimRelayRowStatus(row, 48_025_660))
        // no price, no floor
        assertEquals(SnClaimRelayRowStatus.PENDING, snClaimRelayRowStatus(row, 0))
        val claimed = row.copy(claimableRao = 0, claimTxHash = "0x" + "1a".repeat(32), claimAmountRao = 48_025_660)
        assertEquals(SnClaimRelayRowStatus.CLAIMED, snClaimRelayRowStatus(claimed, 100_000_000))
        // its pool is capture the flag's
        assertEquals(ctfPool, row.pool)
    }

    @Test
    fun `the relay's claimable is no total while none of the coldkey's pools could be read`() {
        // never a zero for balances that could not be read: the unavailable rows say it
        val pool = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = 1, claimableRao = 4_120_000_000)
        val status = SnClaimStatusState(coldkeySs58 = "5Ab", claimableRao = 4_120_000_000, accounts = listOf(pool), showsTile = true)
        assertTrue(snClaimRelayTotalKnown(status))
        // a pool unread beside a read one: the total of the read one, with the unread one's row
        assertTrue(snClaimRelayTotalKnown(status.copy(unavailablePools = listOf(ctfPool))))
        // nothing of the coldkey's read
        assertFalse(snClaimRelayTotalKnown(status.copy(claimableRao = 0, accounts = emptyList(), unavailablePools = listOf(pool1, ctfPool))))
        // another coldkey's account is not the coldkey's total
        val other = pool.copy(coldkeySs58 = "5Cd")
        assertFalse(snClaimRelayTotalKnown(status.copy(claimableRao = 0, accounts = listOf(other), unavailablePools = listOf(ctfPool))))
        // a whole answer, even an empty one, is a total, as before
        assertTrue(snClaimRelayTotalKnown(status.copy(claimableRao = 0, accounts = emptyList())))
    }

    @Test
    fun `before vault2 is configured an unavailable pool alone hides the tile`() {
        // UX-5: before activation the tile shows only balances the ledger returned, as a read
        // that failed whole hides it; with an account, the unavailable row shows beside it
        assertNull(snLedgerTile(SnLedgerLoad.Loaded(emptyList(), unavailablePools = listOf(ctfPool)), claimConfigured = false))
        val tile = snLedgerTile(SnLedgerLoad.Loaded(listOf(account), unavailablePools = listOf(ctfPool)), claimConfigured = false)!!
        assertEquals(listOf(ctfPool), tile.unavailablePools)
        assertFalse(tile.showClaim)
    }

    @Test
    fun `the relay's tile shows the pools its claimable adds up once there is more than one`() {
        // CTF-1, CTF-2 for a network with no wallet on this device
        val pool = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = 1, claimableRao = 4_120_000_000)
        val flag = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = ctfNoId, claimableRao = 48_025_660, kind = Sdk.SnLedgerPoolKindCtf)
        // another coldkey's account (a wallet the network replaced) is not in the claimable
        val other = SnClaimRelayAccountState(coldkeySs58 = "5Cd", noId = 1, claimableRao = 900_000_000)
        val status = SnClaimStatusState(coldkeySs58 = "5Ab", claimableRao = 4_168_025_660, accounts = listOf(pool, flag, other), showsTile = true)
        assertEquals(listOf(pool, flag), snClaimRelayTileRows(status))
        assertEquals(listOf(pool1, ctfPool, pool1), snClaimRelayPools(status))
        // one operator pool: the claimable says it all, as before (CTF-4)
        assertEquals(emptyList<SnClaimRelayAccountState>(), snClaimRelayTileRows(status.copy(accounts = listOf(pool, other))))
        // a pool the relay could not read shows beside the rest
        assertEquals(listOf(pool), snClaimRelayTileRows(status.copy(accounts = listOf(pool), unavailablePools = listOf(ctfPool))))
        // a lone capture the flag account names itself
        assertEquals(listOf(flag), snClaimRelayTileRows(status.copy(accounts = listOf(flag))))
    }

    @Test
    fun `the no-wallet tile's lone total under the floor says so`() {
        // CTF-6: with no row under the total, a total that is one account's under the floor
        // says Below the claim minimum there, as a row would (Apple, Linux and Windows alike)
        val small = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = 1, claimableRao = 48_025_660)
        val status = SnClaimStatusState(
            coldkeySs58 = "5Ab", claimableRao = 48_025_660, transferFloorRao = 100_000_000, accounts = listOf(small), showsTile = true,
        )
        assertEquals(emptyList<SnClaimRelayAccountState>(), snClaimRelayTileRows(status))
        assertTrue(snClaimRelayTileTotalUnderFloor(status))
        // at or above the floor, or with no price, it does not
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(transferFloorRao = 48_025_660)))
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(transferFloorRao = 0)))
        // another coldkey's account (a wallet the network replaced) is not in the total
        val replaced = SnClaimRelayAccountState(coldkeySs58 = "5Cd", noId = 1, claimableRao = 900_000_000)
        assertTrue(snClaimRelayTileTotalUnderFloor(status.copy(accounts = listOf(small, replaced))))
        // nothing to claim: no line
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(claimableRao = 0, accounts = listOf(small.copy(claimableRao = 0)))))
        // rows under the total say it on their own: capture the flag's account, two pools,
        // or a pool the relay could not read beside it
        val flag = SnClaimRelayAccountState(coldkeySs58 = "5Ab", noId = ctfNoId, claimableRao = 48_025_660, kind = Sdk.SnLedgerPoolKindCtf)
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(accounts = listOf(flag))))
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(accounts = listOf(small, small.copy(noId = 2)))))
        assertFalse(snClaimRelayTileTotalUnderFloor(status.copy(unavailablePools = listOf(ctfPool))))
    }

    @Test
    fun `a slot this device won is a line by its role`() {
        // CTF-5: the role is in the key; a role this build does not know, or no country, is no line
        assertEquals(R.string.sn_ctf_won_provider_slot, snCtfWonRes(SnCtfWinState(Sdk.SnCtfRoleProvider, "is")))
        assertEquals(R.string.sn_ctf_won_extender_slot, snCtfWonRes(SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")))
        assertNull(snCtfWonRes(SnCtfWinState("validator", "is")))
        assertNull(snCtfWonRes(SnCtfWinState(Sdk.SnCtfRoleProvider, "")))
    }

    @Test
    fun `every unclaimed win is a line of its own, newest first`() {
        // CTF-5 (owner, 2026-11-11): one line per win in the sdk's UnclaimedWins, in its order,
        // newest first and providers before extenders within an epoch, the same slot of an
        // earlier epoch included
        val wins = listOf(
            SnCtfWinState(Sdk.SnCtfRoleProvider, "is"),
            SnCtfWinState(Sdk.SnCtfRoleExtender, "is"),
            SnCtfWinState(Sdk.SnCtfRoleProvider, "ke"),
            SnCtfWinState(Sdk.SnCtfRoleProvider, "is"),
        )
        assertEquals(
            listOf(
                R.string.sn_ctf_won_provider_slot to "is",
                R.string.sn_ctf_won_extender_slot to "is",
                R.string.sn_ctf_won_provider_slot to "ke",
                R.string.sn_ctf_won_provider_slot to "is",
            ),
            snCtfWonLines(wins)
        )
        // a win with no line is left out, and the others keep their order
        assertEquals(
            listOf(R.string.sn_ctf_won_extender_slot to "ke"),
            snCtfWonLines(listOf(SnCtfWinState("validator", "is"), SnCtfWinState(Sdk.SnCtfRoleExtender, "ke")))
        )
        // none: no line
        assertEquals(emptyList<Pair<Int, String>>(), snCtfWonLines(emptyList()))
    }
}
