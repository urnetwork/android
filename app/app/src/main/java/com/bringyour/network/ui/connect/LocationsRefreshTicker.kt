package com.bringyour.network.ui.connect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fetches the locations list again every [intervalMillis] while the home
 * screen is visible, so the provider counts it shows follow the network.
 *
 * Ticks only between setVisible(true) and setVisible(false); the first fetch
 * is one interval after becoming visible, since showing the screen already
 * has the last fetch. A tick is skipped while [isFetching] (a fetch is in
 * flight), so fetches never overlap.
 *
 * The interval runs on [scope]'s dispatcher, so a test dispatcher drives it
 * in virtual time.
 */
class LocationsRefreshTicker(
    private val scope: CoroutineScope,
    private val intervalMillis: Long,
    private val isFetching: () -> Boolean,
    private val refresh: () -> Unit,
) {
    private var job: Job? = null

    fun setVisible(visible: Boolean) {
        if (visible) {
            if (job?.isActive == true) {
                return
            }
            job = scope.launch {
                while (isActive) {
                    delay(intervalMillis)
                    if (!isFetching()) {
                        refresh()
                    }
                }
            }
        } else {
            job?.cancel()
            job = null
        }
    }
}
