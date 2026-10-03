package com.bringyour.network.ui.blocked_regions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The blocked locations list against a fake source whose calls complete only
 * when the test says so. Reported defect: a failed unblock silently put the
 * row back.
 */
class BlockedRegionsModelTest {

    private data class Region(val id: Int, val name: String)

    /** Holds each call's completion; `available = false` is a missing api. */
    private class HeldSource(var available: Boolean = true) : BlockedRegionsSource<Region> {
        val fetches = mutableListOf<(Result<List<Region>>) -> Unit>()
        val blocks = mutableListOf<(Boolean) -> Unit>()
        val unblocks = mutableListOf<(Boolean) -> Unit>()

        override fun fetch(done: (Result<List<Region>>) -> Unit): Boolean {
            if (available) fetches.add(done)
            return available
        }

        override fun block(location: Region, done: (failed: Boolean) -> Unit): Boolean {
            if (available) blocks.add(done)
            return available
        }

        override fun unblock(location: Region, done: (failed: Boolean) -> Unit): Boolean {
            if (available) unblocks.add(done)
            return available
        }
    }

    private val alpha = Region(1, "Alpha")
    private val bravo = Region(2, "Bravo")
    private val charlie = Region(3, "charlie")

    private fun model(source: HeldSource) = BlockedRegionsModel(
        source = source,
        post = { it() },
        nameOf = { it.name },
        isSame = { a, b -> a.id == b.id },
    )

    private fun loaded(source: HeldSource, vararg regions: Region) = model(source).also {
        it.fetch()
        source.fetches.removeAt(0)(Result.success(regions.toList()))
    }

    @Test
    fun aFailedUnblockRestoresTheRowAndSaysSo() {
        val source = HeldSource()
        val model = loaded(source, charlie, alpha, bravo)

        model.unblock(bravo)
        assertEquals(listOf(alpha, charlie), model.regions.value)

        source.unblocks.single()(true)

        assertEquals(listOf(alpha, bravo, charlie), model.regions.value)
        assertEquals(BlockedRegionsNotice.UnblockFailed("Bravo"), model.notice.value)
        assertFalse(model.isProcessing.value)
    }

    @Test
    fun aSucceededUnblockRemovesTheRowQuietly() {
        val source = HeldSource()
        val model = loaded(source, alpha, bravo)

        model.unblock(bravo)
        source.unblocks.single()(false)

        assertEquals(listOf(alpha), model.regions.value)
        assertNull(model.notice.value)
        assertFalse(model.isProcessing.value)
    }

    @Test
    fun aFailedBlockSaysSo() {
        val source = HeldSource()
        val model = loaded(source, alpha)

        model.block(bravo)
        source.blocks.single()(true)

        assertEquals(listOf(alpha), model.regions.value)
        assertEquals(BlockedRegionsNotice.BlockFailed("Bravo"), model.notice.value)
        assertFalse(model.isProcessing.value)
    }

    @Test
    fun aFailedLoadSaysSo() {
        val source = HeldSource()
        val model = model(source)

        model.fetch()
        source.fetches.single()(Result.failure(IllegalStateException("offline")))

        assertEquals(BlockedRegionsNotice.LoadFailed, model.notice.value)
        assertFalse(model.isFetching.value)
    }

    @Test
    fun noApiFailsVisiblyInsteadOfStayingBusy() {
        val model = model(HeldSource(available = false))

        model.fetch()
        assertFalse(model.isFetching.value)
        assertEquals(BlockedRegionsNotice.LoadFailed, model.notice.value)

        model.block(bravo)
        assertFalse(model.isProcessing.value)
        assertEquals(BlockedRegionsNotice.BlockFailed("Bravo"), model.notice.value)
    }

    @Test
    fun noApiOnUnblockKeepsTheRowAndSaysSo() {
        val source = HeldSource()
        val model = loaded(source, alpha, bravo)
        // the device went away after the list loaded
        source.available = false

        model.unblock(alpha)

        assertEquals(listOf(alpha, bravo), model.regions.value)
        assertEquals(BlockedRegionsNotice.UnblockFailed("Alpha"), model.notice.value)
        assertFalse(model.isProcessing.value)
    }
}
