package com.bringyour.network.ui.connect

/**
 * The provider count to show for the selected location on the home screen.
 *
 * The selected location is a copy taken when it was chosen, so its count is
 * as old as the selection. Whenever the locations list has been fetched since,
 * its entry for the same location id has the current count, so that one is
 * shown; the selection's own count is the fallback.
 *
 * [fetched] holds the fetched locations as (location id, provider count).
 */
fun selectedProviderCount(
    selectedId: String?,
    selectedCount: Int,
    fetched: Sequence<Pair<String, Int>>,
): Int {
    if (selectedId == null) {
        return selectedCount
    }
    return fetched.firstOrNull { it.first == selectedId }?.second ?: selectedCount
}
