package com.bringyour.network.ui.stats

/**
 * Main-thread UI projection. Exit changes do not invalidate the action snapshot
 * or its hostname display values. The memo only retains currently displayed rows.
 */
internal class BlockActionsProjection(
    private val readRows: () -> List<BlockActionUi>,
    private val readExits: () -> Map<String, Set<String>>,
    private val collapseHosts: (List<String>) -> List<String>,
) {
    private var rows: List<BlockActionUi> = emptyList()

    fun clear() {
        rows = emptyList()
    }

    fun refreshRows(): List<BlockActionUi> {
        val previous = rows.associateBy { it.id }
        rows = readRows().map { row ->
            val old = previous[row.id]
            val names = if (old != null && old.hosts == row.hosts) {
                old.hostBaseNames
            } else {
                collapseHosts(row.hosts)
            }
            row.copy(hostBaseNames = names)
        }
        return refreshExits()
    }

    fun refreshExits(): List<BlockActionUi> {
        if (rows.isEmpty()) return rows
        val exitsByIp = readExits()
        var updated: MutableList<BlockActionUi>? = null
        rows.forEachIndexed { index, row ->
            val ids = mutableSetOf<String>()
            row.matchedIps.forEach { exitsByIp[it]?.let(ids::addAll) }
            row.ips.forEach { exitsByIp[it]?.let(ids::addAll) }
            val exits = ids.sorted()
            if (row.exitShortIds != exits) {
                val changed = updated ?: rows.toMutableList().also { updated = it }
                changed[index] = row.copy(exitShortIds = exits)
            }
        }
        if (updated != null) rows = updated
        return rows
    }
}
